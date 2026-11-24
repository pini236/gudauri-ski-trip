package io.github.pini236.skiapp.home

import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.meet.MeetHere
import io.github.pini236.skiapp.season.OpeningView
import io.github.pini236.skiapp.skipass.Prices
import io.github.pini236.skiapp.trip.Trip
import java.time.LocalDate

/**
 * What the new mountain (the redesign, test build only) needs around it (design/handoff/2026-11-21-redesign-around):
 * the meeting point; "your trip" and how to save it, the passenger and the pass's sounds; the ski-pass prices; and the
 * countdown to the season's opening. The rules are today's (trip/, skipass/, season/); only how they are shown is new.
 */
class Around(
    val meet: MeetHere,
    val trip: Trip?,
    val onTrip: (Trip?) -> Unit,
    val passenger: Passenger?,
    val sounds: Sounds,
    val onTripForm: () -> Unit,
    val prices: Prices?,
    /** The resort's today (its time zone), for the prices' validity and the countdown. */
    val today: LocalDate,
    val opening: OpeningView?,
    /** The season's opening day when it has passed with no report (Q10's "late"), else null. */
    val lateOpen: LocalDate?,
    /** Part 3 (design/handoff/2026-11-23-redesign-people): the group, the account and the settings; null with no server. */
    val people: People? = null,
    /** Part 4 (S07): the runs' videos (site/data/videos-seed.json), for the run's full sheet. */
    val videos: List<io.github.pini236.skiapp.data.Video> = emptyList(),
    /** Part 4b (the bottom bar and the village): start a day's recording, where this build has it; null hides the button. */
    val onRecord: (() -> Unit)? = null,
    /** Part 4b (the village's "evening in the hut"): the games page of today, and how many games it has. */
    val onGames: (() -> Unit)? = null,
    val games: Int = 0,
)

/**
 * What the people screens of the new mountain do through the app (part 3): the server as the screens use it today
 * (group/GroupApi.kt), Google's sheet, the pages in the browser, the settings and the games. Nothing here is new: it is
 * what the current group, account and about screens already call.
 */
class People(
    val api: io.github.pini236.skiapp.group.GroupApi,
    val signInGoogle: suspend () -> Unit,
    val onPrivacy: () -> Unit,
    val onSupport: () -> Unit,
    val onLicenses: () -> Unit,
    val onLang: (String?) -> Unit,
    val mode: DayNight.Mode,
    val onMode: (DayNight.Mode) -> Unit,
    val onResetBests: () -> Unit,
    val onGame: (String) -> Unit,
    /** A meetup of a group opens the meeting point on it (Q05), or a new one is picked there (Q04) for this group. */
    val onMeetups: () -> Unit,
    /** The trip form (Q08), for "add my trip". */
    val onTripForm: () -> Unit,
    /** Signed out, deleted, left: the reminders and the group sign follow. */
    val onChanged: () -> Unit,
    val version: String,
    /** The trip this phone keeps (for "I'm on the same flight", which makes it mine). */
    val onMyTrip: (io.github.pini236.skiapp.trip.Trip) -> Unit,
    /** Part 4 (S10): "my days" from "me" (L12). */
    val onDays: () -> Unit = {},
    /** Part 5 (T04): an admin fills a member's flight: the trip form for that member (group, user). */
    val onTripFor: (String, String) -> Unit = { _, _ -> },
)
