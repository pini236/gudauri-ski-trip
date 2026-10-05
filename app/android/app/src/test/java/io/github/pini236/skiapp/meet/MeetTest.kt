package io.github.pini236.skiapp.meet

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.nav.Route
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The meeting point reads the mountain as the site does. The expected stations and routes were printed by the site's
 * own code (site/js/app.js, MEET) on the real data: a meetup saved on the site must show the same place in the app.
 */
class MeetTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val plan by lazy {
        MeetPlan.build(Runs.parse(File(dir, "runs-and-lifts.json").readText()), Terrain.parse(File(dir, "terrain.json").readText()))
    }

    @Test fun theStationsAreTheSitesOnes() {
        val site = listOf(
            "158744055b" to "Snow Park:b Goodaura:t", "158744055t" to "Snow Park:t", "158744059b" to "Pirveli:b",
            "158744059t" to "Pirveli:t Soliko:b", "158744060b" to "Sadzele:b", "158744060t" to "Sadzele:t", "158744063b" to "Khada:b",
            "158744063t" to "Khada:t", "158744075b" to "Goodaura:b New Goodaura:b", "158744077t" to "Soliko:t", "158817616b" to "Zuma:b",
            "158817616t" to "Zuma:t", "472374253b" to "Shino:b", "472374253t" to "Shino:t Firni:b", "663639361b" to "Kudebi:b",
            "663639361t" to "Kudebi:t", "663643826t" to "Firni:t", "1196532543b" to "Kikilo:b", "1196532543t" to "Kikilo:t",
            "1196532544t" to "New Goodaura:t",
        )
        assertEquals(site, plan.stations.map { s -> s.id to s.ends.joinToString(" ") { it.lift.name + if (it.top) ":t" else ":b" } })
        // the name is the first lift that starts there: the top of Goodaura is "Snow Park", the top of Shino is "Firni"
        assertEquals("Snow Park", plan.byId["158744055b"]!!.name)
        assertEquals("Firni", plan.byId["472374253t"]!!.name)
        assertEquals(2161.0, plan.byId["158744075b"]!!.h!!.toDouble(), 1.0)
        assertEquals(3235.0, plan.byId["158744060t"]!!.h!!.toDouble(), 1.0)
    }

    @Test fun theThreeSpotsInOneTap() {
        assertEquals("158744075b", plan.preset(Preset.MORNING)!!.id)
        assertEquals("158744055b", plan.preset(Preset.NOON)!!.id)
        assertEquals("158744075b", plan.preset(Preset.END)!!.id)
    }

    @Test fun howToGetThereComesFromTheConnections() {
        fun ways(id: String) = plan.ways(plan.byId[id]!!).map { w ->
            val from = when (val f = w.from) { is Way.From.Run -> "run:" + f.key; is Way.From.TopOf -> "top:" + f.key; is Way.From.BottomOf -> "bottom:" + f.lift }
            from + " " + w.hops.joinToString("|") { if (it is Way.Hop.Run) "r:" + it.piste.key else "l:" + (it as Way.Hop.Lift).name }
        }
        assertEquals(listOf("run:Goodaura 1 r:Goodaura 1|r:Goodaura 2", "run:Snow Park r:Snow Park|r:Goodaura 1", "run:Tatra 1 r:Tatra 1|r:Shino"), ways("158744075b"))
        assertEquals(listOf("bottom:Shino l:Shino", "run:Sadzele 3 r:Sadzele 3|r:Snow Park", "run:Firni 2 r:Firni 2|r:Baby", "run:Firni 1 r:Firni 1|r:Firni 2"), ways("472374253t"))
        assertEquals(listOf("top:Khada r:Khada", "run:Kudebi 1 r:Kudebi 1|r:Kudebi 2"), ways("158744063b"))
        assertEquals(listOf("bottom:New Goodaura l:New Goodaura"), ways("1196532544t"))
    }

    @Test fun theDaysAreYourTripsSkiDaysOrTheNextWeek() {
        val now = Instant.parse("2026-10-02T21:30:00Z") // already the 3rd in Gudauri
        val none = Meet.days(null, now)
        assertEquals(LocalDate.of(2026, 10, 3), none.first()); assertEquals(7, none.size)
        val trip = Trip(Leg(LocalDate.of(2027, 1, 10), "GD 101", "TLV", "TBS", LocalTime.of(16, 0), LocalTime.of(20, 35)),
            Leg(LocalDate.of(2027, 1, 15), "GD 102", "TBS", "TLV", LocalTime.of(1, 35), LocalTime.of(2, 15)))
        assertEquals((11..14).map { LocalDate.of(2027, 1, it) }, Meet.days(trip, now))
        // a trip with only the way there: the week after landing
        val out = Trip(Leg(LocalDate.of(2027, 1, 10), "", "TLV", "TBS"))
        val d = Meet.days(out, now)
        assertEquals(LocalDate.of(2027, 1, 11), d.first())
    }

    @Test fun theCountdownIsInGudauriTime() {
        val at = Meet.at(LocalDate.of(2027, 1, 11), LocalTime.of(9, 30)) // 05:30 UTC
        assertEquals(Instant.parse("2027-01-11T05:30:00Z"), at)
        assertEquals(Meet.Left.Minutes(30), Meet.left(at, Instant.parse("2027-01-11T05:00:00Z")))
        assertEquals(Meet.Left.Hours("1:12"), Meet.left(at, Instant.parse("2027-01-11T04:18:00Z")))
        assertEquals(Meet.Left.Days(2), Meet.left(at, Instant.parse("2027-01-10T04:00:00Z")))
        assertEquals(Meet.Left.Passed, Meet.left(at, Instant.parse("2027-01-11T05:31:00Z")))
    }

    @Test fun theLinkIsTheSitesAndReadsBack() {
        val link = Meet.link("158744075b", LocalTime.of(9, 30), LocalDate.of(2027, 1, 11))
        assertEquals("https://gudauri-ski-trip.vercel.app/#meet/158744075b/0930/20270111", link)
        val r = Route.fromSiteLink(link) as Route.Meet
        assertEquals(LocalTime.of(9, 30), Meet.time(r.time)); assertEquals(LocalDate.of(2027, 1, 11), Meet.day(r.day))
        assertNull(Meet.time("2561")); assertNull(Meet.day("20271341"))
    }

    @Test fun aReminderIsForAMeetupAheadThatIsNotTurnedOff() {
        val at = Meet.at(LocalDate.of(2027, 1, 11), LocalTime.of(9, 30))
        fun item(id: String, t: Instant) = Reminders.Item(id, t, "Goodaura", "Gudauri 2027", "09:30", "meet/158744075b/0930/20270111")
        val items = listOf(item("a", at), item("b", at), item("c", at.minusSeconds(3600)))
        // at 09:10: a quarter of an hour before 09:30 is 09:15, still ahead; the one an hour earlier is not
        val now = Instant.parse("2027-01-11T05:10:00Z")
        assertEquals(listOf("a"), Reminders.due(items, setOf("b"), now).map { it.id })
        // at 09:16 it is too late to remind of 09:30 (the meetup itself is still ahead)
        assertEquals(emptyList<String>(), Reminders.due(items, emptySet(), Instant.parse("2027-01-11T05:16:00Z")).map { it.id })
        // the item travels in the alarm as text and back
        assertEquals(items[0], Reminders.Item.of(items[0].json()))
    }

    @Test fun eachReminderHasACodeOfItsOwnThatIsNeverGivenAgain() {
        // "Aa" and "BB" have the same hash: under the hash they shared one alarm (R-18)
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val (codes, next) = Reminders.codesFor(emptyMap(), 1, listOf("Aa", "BB", "Aa"))
        assertEquals(mapOf("Aa" to 1, "BB" to 2), codes); assertEquals(3, next)
        // a meetup that is armed again keeps its code; a new one gets the next, even after others dropped out
        val (again, after) = Reminders.codesFor(mapOf("BB" to 2), next, listOf("BB", "c"))
        assertEquals(mapOf("BB" to 2, "c" to 3), again); assertEquals(4, after)
    }
}
