package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.group.FlightKey
import io.github.pini236.skiapp.group.GroupTab
import io.github.pini236.skiapp.group.InviteCode
import io.github.pini236.skiapp.group.Me
import io.github.pini236.skiapp.group.Meetup
import io.github.pini236.skiapp.group.Member
import io.github.pini236.skiapp.group.Score
import io.github.pini236.skiapp.trip.Leg
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Part 3 of the redesign (design/handoff/2026-11-23-redesign-people): the rules behind the people screens, apart from
 * how they are drawn, so the unit tests read them (PeopleRulesTest). The data and the actions are today's (group/).
 */
internal object PeopleRules {
    /** A person's circle (section 1): one of seven, by the user id, the same person the same colour on every screen. */
    val COLORS = longArrayOf(0xFF7A5AE0, 0xFF1F7A7A, 0xFFB34A33, 0xFF3B6FB8, 0xFF8A6420, 0xFF5C7A3A, 0xFF6A5A8C)
    /** A group's circle, in "my groups". */
    const val GROUP_COLOR = 0xFF5A6A7AL

    /**
     * The colour's index: the id's UTF-16 code units summed, modulo 7. Not String.hashCode, so the iPhone (and the site,
     * should it draw circles) reach the same colour with no Java in between.
     */
    fun colorIndex(userId: String): Int = userId.sumOf { it.code } % 7

    /** The first letter of a name, upper-cased where the script has cases; "?" for an empty name. */
    fun initial(name: String?): String {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return "?"
        val cp = n.codePointAt(0)
        return String(Character.toChars(Character.toUpperCase(cp)))
    }

    /** What "me" opens (section 2): an account (R09), a guest in a group (R10), or nobody yet (R10 without the box). */
    enum class MeKind { ACCOUNT, GUEST, NOBODY }

    fun meKind(me: Me?, groups: Int): MeKind = when {
        me?.registered == true -> MeKind.ACCOUNT
        me != null && groups > 0 -> MeKind.GUEST
        else -> MeKind.NOBODY
    }

    /** The tab the group opens on (section 4): before the trip the flights, during it the meetups, after it the scores. */
    fun openingTab(start: LocalDate?, end: LocalDate?, today: LocalDate): GroupTab = when {
        start == null -> GroupTab.MEETUPS
        today < start -> GroupTab.FLIGHTS
        end != null && today > end -> GroupTab.SCORES
        else -> GroupTab.MEETUPS
    }

    /** The sheet's top (section 4): 292 under the screen's top, 254 on the flights and the members, which are longer. */
    /** Part 5 (GR3): one top for every tab, so the sheet never jumps when the tab or the group arrives. */
    @Suppress("UNUSED_PARAMETER")
    fun sheetTop(tab: GroupTab): Int = 254

    /** The meetups still on the list (R03): a meetup more than an hour past leaves it; the soonest first. */
    fun upcoming(meetups: List<Meetup>, now: Instant): List<Meetup> =
        meetups.filter { Duration.between(it.at, now) <= Duration.ofHours(1) }.sortedBy { it.at }

    /** The table's places (R05): a tie shares the place, and the next one skips (1, 2, 2, 4). The rows come sorted. */
    fun places(rows: List<Score>): List<Int> {
        val out = ArrayList<Int>(rows.size)
        for ((i, s) in rows.withIndex()) out += if (i > 0 && rows[i - 1].best == s.best) out[i - 1] else i + 1
        return out
    }

    /** What the six boxes keep from typed or pasted text (R01): a whole link's code or token, else the letters. */
    fun codeFrom(text: String): String = InviteCode.fromLink(text) ?: InviteCode.clean(text)

    /** A flight card (R04): one leg and who is on it, grouped as today (FlightKey, X-4). */
    data class Card(val key: FlightKey, val leg: Leg, val members: List<Member>) {
        val mine: Boolean get() = members.any { it.me }
        val byAdmin: Member? get() = members.firstOrNull { it.enteredByAdmin != null }
    }

    /** The cards of one direction (the out or return pill), by date and then by the hour it leaves. */
    fun cards(members: List<Member>, ret: Boolean): List<Card> {
        val rows = buildList {
            for (m in members) {
                val t = m.trip ?: continue
                val l = if (ret) t.ret ?: continue else t.out
                add(l to m)
            }
        }
        return rows.groupBy { FlightKey.of(it.first, ret) }
            .map { (k, v) -> Card(k, v.first().first, v.map { it.second }) }
            .sortedWith(compareBy({ it.key.date }, { it.leg.departs ?: java.time.LocalTime.MAX }))
    }

    /** At most six circles, and "+N" for the rest (R02). */
    fun circles(n: Int): Pair<Int, Int> = minOf(n, 6) to (n - 6).coerceAtLeast(0)
}
