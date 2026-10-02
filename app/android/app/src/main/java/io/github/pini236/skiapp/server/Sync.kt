package io.github.pini236.skiapp.server

import android.content.Context
import io.github.pini236.skiapp.trip.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The group's data on the phone (decision 28, server/CONTRACT.md "עותק מקומי"): what the screens show comes from
 * here, never straight from the network, so they open instantly and keep working with no signal.
 *
 * - Every group the person opened is kept as a file in the app's private storage, with the time it was read.
 * - [GroupStore.open] (a screen is showing) reads it again and listens to Realtime; a change reads it again.
 * - Writes made here show at once and wait in a queue (a file) until the server takes them: scores (only a higher
 *   one counts, so sending twice is harmless), meetups (the last one saved wins), my name and trip in the group.
 *   A write the server refuses is dropped and the group read again, so the screen shows the truth.
 * - Everything else (joining, admin actions) needs signal: call [Groups] directly, then [GroupStore.refresh].
 *
 * One [Sync] for the app ([of]). All network work runs on one background thread, in order.
 */
class Sync(
    val server: Server,
    private val dir: File,
    /** The games whose group tables are kept ("descent"...), as sent to submit_score. */
    private val games: List<String> = emptyList(),
    private val realtime: Realtime? = Realtime(server),
    private val worker: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "sync").apply { isDaemon = true } },
) {
    val groups = Groups(server)

    /** The groups I am in, kept like a group is. */
    data class Mine(val groups: List<Groups.Group> = emptyList(), val requests: List<Groups.JoinRequest> = emptyList(), val readAt: Long? = null, val offline: Boolean = false)

    /** One group as the screens show it. [readAt] is null until it was read once; [gone]: I am no longer in it. */
    data class Snapshot(
        val groupId: String,
        val group: Groups.Group? = null,
        val members: List<Groups.Member> = emptyList(),
        val flights: Map<String, Trip> = emptyMap(),
        /** Trips an admin entered for a member (Q7, "entered by"): trip id to the admin's user id. */
        val enteredBy: Map<String, String> = emptyMap(),
        val meetups: List<Groups.Meetup> = emptyList(),
        val scores: Map<String, List<Groups.Score>> = emptyMap(),
        /** The working invite: every member sees it and may share it. */
        val invite: Groups.Invite? = null,
        /** Requests waiting for an admin; empty for a member who is not one. */
        val requests: List<Groups.JoinRequest> = emptyList(),
        val readAt: Long? = null,
        val offline: Boolean = false,
        val gone: Boolean = false,
        /** Writes made here that the server has not taken yet. */
        val waiting: Int = 0,
    ) {
        fun me(userId: String?) = members.firstOrNull { it.userId == userId }
    }

    private val stores = HashMap<String, GroupStore>()
    private val outbox = Outbox(File(dir, "outbox.json"))
    private val mine = MutableStateFlow(readMine())
    private var mineChannel: Realtime.Channel? = null

    init {
        dir.mkdirs()
    }

    /** The groups I am in, from the phone first; [refreshMine] reads them again. */
    val myGroups: StateFlow<Mine> get() = mine

    @Synchronized
    fun group(id: String): GroupStore = stores.getOrPut(id) { GroupStore(id) }

    fun refreshMine() = worker.execute { readMineFromServer() }

    /** Watch my groups list (the home of the group screens): approvals and removals show without a reload. */
    @Synchronized
    fun watchMine() {
        val me = server.auth.current()?.userId ?: return
        if (mineChannel != null || realtime == null) return refreshMine()
        mineChannel = realtime.join(
            "me:$me",
            listOf(
                Realtime.Watch("group_members", "user_id=eq.$me"),
                Realtime.Watch("group_members", event = "DELETE"),
                Realtime.Watch("join_requests", "user_id=eq.$me"),
                Realtime.Watch("groups"),
            ),
            onChange = { refreshMine() },
            onReconnect = { flush(); refreshMine() },
        )
        refreshMine()
    }

    @Synchronized
    fun unwatchMine() {
        mineChannel?.let { realtime?.leave(it) }
        mineChannel = null
    }

    /** Send what waits (call on launch and when signal comes back). */
    fun flush() = worker.execute { sendOutbox() }

    /** After deleting the account or signing out: nothing of the groups stays on the phone. */
    @Synchronized
    fun forget() {
        unwatchMine()
        stores.values.forEach { it.close() }
        stores.clear()
        outbox.clear()
        dir.listFiles()?.forEach { it.deleteRecursively() }
        mine.value = Mine()
    }

    // --- My groups ---------------------------------------------------------

    private fun readMineFromServer() {
        try {
            val list = groups.mine()
            val req = groups.myRequests()
            val m = Mine(list, req, server.nowSeconds() * 1000)
            mine.value = m
            write(File(dir, "mine.json"), mineJson(m))
        } catch (_: Offline) {
            mine.value = mine.value.copy(offline = true)
        } catch (_: ServerError) {
            mine.value = mine.value.copy(offline = false)
        }
    }

    private fun readMine(): Mine = runCatching {
        val o = JSONObject(File(dir, "mine.json").readText())
        Mine(arr(o, "groups").map(Codec::group), arr(o, "requests").map(Codec::request), o.optLong("readAt").takeIf { it > 0 })
    }.getOrDefault(Mine())

    private fun mineJson(m: Mine) = JSONObject()
        .put("groups", JSONArray(m.groups.map(Codec::group)))
        .put("requests", JSONArray(m.requests.map(Codec::request)))
        .put("readAt", m.readAt ?: 0)

    // --- The queue of writes -------------------------------------------------

    private fun sendOutbox() {
        val touched = HashSet<String>()
        while (true) {
            val op = outbox.first() ?: break
            try {
                send(op)
                outbox.remove(op.id)
            } catch (_: Offline) {
                break
            } catch (e: ServerError) {
                // refused: drop it, and read the group again so the screen shows what the server has
                if (e.code == "no_session") break
                outbox.remove(op.id)
            }
            touched += op.groupId
        }
        touched.forEach { id -> synchronized(this) { stores[id] }?.readFromServer() }
        synchronized(this) { stores.values.toList() }.forEach { it.countWaiting() }
    }

    private fun send(op: Op) {
        val b = op.body
        when (op.kind) {
            "score" -> groups.submitScore(b.getString("game"), b.getInt("score"))
            "my_membership" -> groups.setMine(op.groupId, b.getString("name"), b.optString("trip").ifBlank { null })
            "meetup_add" -> {
                val m = groups.addMeetup(op.groupId, b.getString("station"), Instant.parse(b.getString("at")), b.optString("note").ifBlank { null })
                outbox.rename(b.getString("local"), m.id)
                synchronized(this) { stores[op.groupId] }?.renamed(b.getString("local"), m)
            }
            "meetup_change" -> groups.changeMeetup(b.getString("id"), b.getString("station"), Instant.parse(b.getString("at")), b.optString("note").ifBlank { null })
            "meetup_remove" -> groups.removeMeetup(b.getString("id"))
        }
    }

    // --- One group -----------------------------------------------------------

    inner class GroupStore internal constructor(val groupId: String) {
        private val file = File(dir, "groups/$groupId.json")
        private val flow = MutableStateFlow(load())
        private var channel: Realtime.Channel? = null
        @Volatile private var queued = false

        val state: StateFlow<Snapshot> get() = flow

        /** A screen shows this group: read it and listen until [close]. */
        @Synchronized
        fun open() {
            if (channel == null && realtime != null) {
                channel = realtime.join(
                    "group:$groupId",
                    listOf(
                        Realtime.Watch("groups", "id=eq.$groupId"),
                        Realtime.Watch("group_members", "group_id=eq.$groupId"),
                        Realtime.Watch("meetups", "group_id=eq.$groupId"),
                        Realtime.Watch("invites", "group_id=eq.$groupId"),
                        Realtime.Watch("join_requests", "group_id=eq.$groupId"),
                        // deletions cannot be filtered (Supabase); trips and scores have no group: the row rules
                        // decide what reaches the phone, and only the table name is used anyway
                        Realtime.Watch("group_members", event = "DELETE"),
                        Realtime.Watch("meetups", event = "DELETE"),
                        Realtime.Watch("trips"),
                        Realtime.Watch("scores"),
                    ),
                    onChange = { refresh() },
                    onReconnect = { flush(); refresh() },
                )
            }
            refresh()
        }

        @Synchronized
        fun close() {
            channel?.let { realtime?.leave(it) }
            channel = null
        }

        /** Read again (in the background). Several calls close together read once. */
        fun refresh() {
            if (queued) return
            queued = true
            worker.execute { queued = false; readFromServer() }
        }

        internal fun readFromServer() {
            val me = server.auth.current()?.userId
            try {
                val members = groups.members(groupId)
                if (me == null || members.none { it.userId == me }) {
                    flow.value = Snapshot(groupId, gone = true, readAt = server.nowSeconds() * 1000)
                    file.delete()
                    return
                }
                val group = groups.mine().firstOrNull { it.id == groupId }
                val admin = members.first { it.userId == me }.admin
                val flights = groups.flightDetails(groupId)
                var s = Snapshot(
                    groupId,
                    group,
                    members,
                    flights.mapValues { it.value.trip },
                    flights.values.filter { it.byAdmin }.associate { it.id to it.enteredBy!! },
                    groups.meetups(groupId),
                    games.associateWith { groups.leaderboard(groupId, it) },
                    groups.invite(groupId), // every member may share it (docs/USERS.md)
                    if (admin) groups.requests(groupId) else emptyList(),
                    server.nowSeconds() * 1000,
                )
                // what waits in the queue still shows, on top of what the server sent
                outbox.forGroup(groupId).forEach { s = applyLocal(s, it, me) }
                s = s.copy(waiting = outbox.forGroup(groupId).size)
                flow.value = s
                write(file, Codec.snapshot(s))
            } catch (_: Offline) {
                flow.value = flow.value.copy(offline = true)
            } catch (e: ServerError) {
                // not mine any more, or the session is gone: keep what is shown, it is marked as not fresh
                flow.value = flow.value.copy(offline = e.code == "no_session")
            }
        }

        // --- Writes that wait for signal ---

        fun submitScore(game: String, score: Int) = queue("score", JSONObject().put("game", game).put("score", score))

        fun setMine(displayName: String, tripId: String?) =
            queue("my_membership", JSONObject().put("name", displayName.trim()).put("trip", tripId ?: ""))

        /** Shows at once with a temporary id ("local:..."), which becomes the server's when it is sent. */
        fun addMeetup(station: String, at: Instant, note: String? = null): String {
            val local = "local:" + UUID.randomUUID()
            queue("meetup_add", JSONObject().put("local", local).put("station", station).put("at", at.toString()).put("note", note ?: ""))
            return local
        }

        fun changeMeetup(id: String, station: String, at: Instant, note: String? = null) {
            val body = JSONObject().put("station", station).put("at", at.toString()).put("note", note ?: "")
            // a meetup not sent yet: change what will be sent
            if (id.startsWith("local:") && outbox.update(id) { it.put("station", station).put("at", at.toString()).put("note", note ?: "") }) {
                flow.value = applyLocal(flow.value, Op("", "meetup_add", groupId, body.put("local", id)), server.auth.current()?.userId)
                return
            }
            queue("meetup_change", body.put("id", id))
        }

        fun removeMeetup(id: String) {
            if (id.startsWith("local:")) outbox.dropMeetup(id) else queue("meetup_remove", JSONObject().put("id", id))
            flow.value = flow.value.copy(meetups = flow.value.meetups.filterNot { it.id == id })
            countWaiting()
        }

        private fun queue(kind: String, body: JSONObject) {
            val op = outbox.add(kind, groupId, body)
            flow.value = applyLocal(flow.value, op, server.auth.current()?.userId).copy(waiting = outbox.forGroup(groupId).size)
            flush()
        }

        internal fun renamed(local: String, m: Groups.Meetup) {
            flow.value = flow.value.copy(meetups = flow.value.meetups.map { if (it.id == local) m else it })
        }

        internal fun countWaiting() {
            flow.value = flow.value.copy(waiting = outbox.forGroup(groupId).size)
        }

        private fun load(): Snapshot = runCatching { Codec.snapshot(JSONObject(file.readText())) }.getOrNull() ?: Snapshot(groupId)
    }

    /** A write that waits, shown on top of the last read. */
    private fun applyLocal(s: Snapshot, op: Op, me: String?): Snapshot {
        val b = op.body
        return when (op.kind) {
            "score" -> {
                val game = b.getString("game")
                val score = b.getInt("score")
                val name = s.me(me)?.name ?: return s
                val table = s.scores[game].orEmpty()
                val mineNow = table.firstOrNull { it.userId == me }
                if (mineNow != null && mineNow.best >= score) return s
                val next = (table.filterNot { it.userId == me } + Groups.Score(me!!, name, score)).sortedByDescending { it.best }
                s.copy(scores = s.scores + (game to next))
            }
            "my_membership" -> s.copy(members = s.members.map { if (it.userId == me) it.copy(name = b.getString("name"), tripId = b.optString("trip").ifBlank { null }) else it })
            "meetup_add" -> {
                val m = Groups.Meetup(b.getString("local"), s.groupId, b.getString("station"), Instant.parse(b.getString("at")), b.optString("note").ifBlank { null }, me)
                s.copy(meetups = (s.meetups.filterNot { it.id == m.id } + m).sortedBy { it.at })
            }
            "meetup_change" -> s.copy(meetups = s.meetups.map {
                if (it.id == b.getString("id")) it.copy(station = b.getString("station"), at = Instant.parse(b.getString("at")), note = b.optString("note").ifBlank { null }) else it
            }.sortedBy { it.at })
            "meetup_remove" -> s.copy(meetups = s.meetups.filterNot { it.id == b.getString("id") })
            else -> s
        }
    }

    // --- Files ---------------------------------------------------------------

    internal data class Op(val id: String, val kind: String, val groupId: String, val body: JSONObject)

    /** The queue, in a file, in order. */
    internal class Outbox(private val file: File) {
        private val ops = ArrayList<Op>(read())

        @Synchronized fun first(): Op? = ops.firstOrNull()
        @Synchronized fun forGroup(g: String): List<Op> = ops.filter { it.groupId == g }

        @Synchronized
        fun add(kind: String, groupId: String, body: JSONObject): Op {
            // a newer score for the same game, or my name again, replaces the older one still waiting
            if (kind == "my_membership") ops.removeAll { it.kind == kind && it.groupId == groupId }
            if (kind == "score") ops.removeAll { it.kind == kind && it.body.getString("game") == body.getString("game") && it.body.getInt("score") <= body.getInt("score") }
            return Op(UUID.randomUUID().toString(), kind, groupId, body).also { ops += it; save() }
        }

        @Synchronized fun remove(id: String) { ops.removeAll { it.id == id }; save() }

        /** A meetup created here got its id from the server: later changes to it go to that id. */
        @Synchronized
        fun rename(local: String, real: String) {
            ops.filter { it.body.optString("id") == local }.forEach { it.body.put("id", real) }
            save()
        }

        @Synchronized
        fun update(local: String, change: (JSONObject) -> Unit): Boolean {
            val op = ops.firstOrNull { it.kind == "meetup_add" && it.body.getString("local") == local } ?: return false
            change(op.body)
            save()
            return true
        }

        @Synchronized
        fun dropMeetup(local: String) {
            ops.removeAll { (it.kind == "meetup_add" && it.body.getString("local") == local) || it.body.optString("id") == local }
            save()
        }

        @Synchronized fun clear() { ops.clear(); file.delete() }

        private fun save() = write(file, JSONArray(ops.map { JSONObject().put("id", it.id).put("kind", it.kind).put("group", it.groupId).put("body", it.body) }))

        private fun read(): List<Op> = runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { a.getJSONObject(it) }.map { Op(it.getString("id"), it.getString("kind"), it.getString("group"), it.getJSONObject("body")) }
        }.getOrDefault(emptyList())
    }

    /** The snapshot as JSON on the phone (its own small format; v1). */
    internal object Codec {
        fun group(g: Groups.Group): JSONObject = JSONObject().put("id", g.id).put("name", g.name)
            .put("starts_on", g.startsOn?.toString() ?: JSONObject.NULL).put("ends_on", g.endsOn?.toString() ?: JSONObject.NULL)

        fun group(o: JSONObject) = Groups.Group(o.getString("id"), o.getString("name"), Groups.date(o, "starts_on"), Groups.date(o, "ends_on"))

        fun request(r: Groups.JoinRequest): JSONObject = JSONObject().put("id", r.id).put("group_id", r.groupId).put("user_id", r.userId)
            .put("display_name", r.name).put("kind", r.kind).put("reclaim_user_id", r.reclaimUserId ?: JSONObject.NULL).put("status", r.status)

        fun request(o: JSONObject) = Groups.JoinRequest(o.getString("id"), o.getString("group_id"), o.getString("user_id"), o.getString("display_name"),
            o.getString("kind"), o.optString("reclaim_user_id").takeUnless { o.isNull("reclaim_user_id") || it.isBlank() }, o.getString("status"))

        fun snapshot(s: Snapshot): JSONObject = JSONObject().put("v", 1).put("group_id", s.groupId)
            .put("group", s.group?.let(::group) ?: JSONObject.NULL)
            .put("members", JSONArray(s.members.map { JSONObject().put("user_id", it.userId).put("name", it.name).put("role", it.role).put("trip_id", it.tripId ?: JSONObject.NULL) }))
            .put("flights", JSONObject().apply { s.flights.forEach { (id, t) -> put(id, TripRow.of(t)) } })
            .put("entered_by", JSONObject(s.enteredBy))
            .put("meetups", JSONArray(s.meetups.map { JSONObject().put("id", it.id).put("station", it.station).put("at", it.at.toString()).put("note", it.note ?: JSONObject.NULL).put("created_by", it.createdBy ?: JSONObject.NULL) }))
            .put("scores", JSONObject().apply { s.scores.forEach { (g, l) -> put(g, JSONArray(l.map { JSONObject().put("user_id", it.userId).put("name", it.name).put("best", it.best) })) } })
            .put("invite", s.invite?.let { JSONObject().put("id", it.id).put("code", it.code).put("token", it.token).put("requires_approval", it.requiresApproval).put("max_uses", it.maxUses ?: JSONObject.NULL).put("expires_at", it.expiresAt ?: JSONObject.NULL) } ?: JSONObject.NULL)
            .put("requests", JSONArray(s.requests.map(::request)))
            .put("read_at", s.readAt ?: 0)

        fun snapshot(o: JSONObject): Snapshot? {
            if (o.optInt("v") != 1) return null
            val id = o.getString("group_id")
            val flights = o.getJSONObject("flights").let { f -> f.keys().asSequence().mapNotNull { k -> TripRow.trip(f.getJSONObject(k))?.let { k to it } }.toMap() }
            val scores = o.getJSONObject("scores").let { sc -> sc.keys().asSequence().associateWith { g -> arr(sc, g).map { Groups.Score(it.getString("user_id"), it.getString("name"), it.getInt("best")) } } }
            val inv = o.optJSONObject("invite")?.let {
                Groups.Invite(it.getString("id"), it.getString("code"), it.getString("token"), it.optBoolean("requires_approval"),
                    if (it.isNull("max_uses")) null else it.getInt("max_uses"), if (it.isNull("expires_at")) null else it.getString("expires_at"))
            }
            return Snapshot(
                id,
                o.optJSONObject("group")?.let(::group),
                arr(o, "members").map { Groups.Member(it.getString("user_id"), it.getString("name"), it.getString("role"), if (it.isNull("trip_id")) null else it.getString("trip_id")) },
                flights,
                o.optJSONObject("entered_by")?.let { e -> e.keys().asSequence().associateWith { e.getString(it) } }.orEmpty(),
                arr(o, "meetups").map {
                    Groups.Meetup(it.getString("id"), id, it.getString("station"), Instant.parse(it.getString("at")),
                        if (it.isNull("note")) null else it.getString("note"), if (it.isNull("created_by")) null else it.optString("created_by").ifBlank { null })
                },
                scores,
                inv,
                arr(o, "requests").map(::request),
                o.optLong("read_at").takeIf { it > 0 },
            )
        }
    }

    companion object {
        @Volatile private var instance: Sync? = null

        /** The app's one [Sync], kept in the app's private files (never backed up: backup is off in the manifest). */
        fun of(context: Context, games: List<String> = listOf("descent")): Sync = instance ?: synchronized(this) {
            instance ?: Sync(Server.of(context), File(context.applicationContext.filesDir, "server"), games).also { instance = it }
        }

        private fun arr(o: JSONObject, k: String): List<JSONObject> = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()

        // write a copy, then swap: a crash mid-write never leaves half a file
        private fun write(f: File, json: Any) {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
