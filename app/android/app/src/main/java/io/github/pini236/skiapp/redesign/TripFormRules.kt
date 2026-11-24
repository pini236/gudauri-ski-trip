package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.trip.Airports
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import java.time.LocalDate
import java.time.LocalTime

/**
 * T09 (design/handoff/2026-11-23-redesign-in-words, section 7): the full trip form, as one draft. The rules are today's
 * (trip/TripForm.kt): only the outbound date is required; both airports from the list or a three-letter code; the way
 * back is the way out reversed unless it was changed by hand; the ski days follow the flights unless set by hand.
 */
internal data class TripDraft(
    val oDate: LocalDate? = null,
    val oFlight: String = "",
    val oFrom: String = "",
    val oTo: String = "",
    val oDep: LocalTime? = null,
    val oArr: LocalTime? = null,
    val rDate: LocalDate? = null,
    val rFlight: String = "",
    /** The way back's airports set by hand, or null: the way out reversed ("automatic"). */
    val rFrom: String? = null,
    val rTo: String? = null,
    val rDep: LocalTime? = null,
    val rArr: LocalTime? = null,
    /** The ski days set by hand, or null to follow the flights. */
    val ski: ClosedRange<LocalDate>? = null,
) {
    val rAuto: Boolean get() = rFrom == null && rTo == null
    val backFrom: String get() = rFrom ?: oTo
    val backTo: String get() = rTo ?: oFrom

    /** The trip the flights make (null without the outbound date), the ski days not yet checked. */
    fun flights(): Trip? = oDate?.let { d ->
        Trip(Leg(d, oFlight.trim(), oFrom.trim(), oTo.trim(), oDep, oArr),
            rDate?.let { Leg(it, rFlight.trim(), backFrom.trim(), backTo.trim(), rDep, rArr) })
    }

    /** What is wrong, card by card (the site's keys, trip.*); empty when it can be saved. */
    fun errors(today: LocalDate, initialOut: LocalDate?): TripErrors {
        val out = when {
            oDate == null -> TripError.NEED_DATE
            // a trip already saved may keep its past date (it is being fixed, not planned)
            oDate < today && oDate != initialOut -> TripError.PAST_DATE
            !Airports.ok(oFrom) || !Airports.ok(oTo) -> TripError.BAD_CODE
            else -> null
        }
        val back = when {
            oDate != null && rDate != null && rDate < oDate -> TripError.BAD_ORDER
            rDate != null && (!Airports.ok(backFrom) || !Airports.ok(backTo)) -> TripError.BAD_CODE
            else -> null
        }
        val s = ski
        val skiE = if (s == null) null else when {
            s.start > s.endInclusive -> TripError.SKI_OUTSIDE
            oDate != null && s.start < oDate -> TripError.SKI_OUTSIDE
            rDate != null && s.endInclusive > rDate -> TripError.SKI_OUTSIDE
            else -> null
        }
        return TripErrors(out, back, skiE)
    }

    /** The trip to save, or null while something is wrong. */
    fun trip(today: LocalDate, initialOut: LocalDate?): Trip? =
        if (errors(today, initialOut).any) null else flights()?.copy(ski = ski)

    /** New dates from the calendar (Q08): a new return keeps the way back automatic; manual ski days outside are dropped. */
    fun withDates(o: LocalDate, r: LocalDate?): TripDraft {
        val keepSki = ski?.takeIf { it.start >= o && (r == null || it.endInclusive <= r) }
        return copy(oDate = o, rDate = r, ski = keepSki).let { if (r == null) it.copy(rFlight = "", rDep = null, rArr = null, rFrom = null, rTo = null) else it }
    }

    companion object {
        /** The form for [t]; a new one starts from Tel Aviv to Tbilisi, as the site does ([tlv], [tbs]: "TLV · Tel Aviv"). */
        fun of(t: Trip?, tlv: String, tbs: String): TripDraft = if (t == null) TripDraft(oFrom = tlv, oTo = tbs) else {
            val r = t.ret
            // a way back that is not the way out reversed was set by hand
            val manual = r != null && (r.from.trim() != t.out.to.trim() || r.to.trim() != t.out.from.trim())
            TripDraft(t.out.date, t.out.flight, t.out.from, t.out.to, t.out.departs, t.out.arrives,
                r?.date, r?.flight.orEmpty(), if (manual) r?.from else null, if (manual) r?.to else null, r?.departs, r?.arrives, t.ski)
        }
    }
}

internal enum class TripError { NEED_DATE, PAST_DATE, BAD_CODE, BAD_ORDER, SKI_OUTSIDE }

internal data class TripErrors(val out: TripError?, val back: TripError?, val ski: TripError?) {
    val any: Boolean get() = out != null || back != null || ski != null
}
