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
)
