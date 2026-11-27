package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.season.OpeningView
import io.github.pini236.skiapp.season.Season
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.weather.Forecast
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * P.25, the top pill (design/handoff/2026-11-27-redesign-top-pill, boards TP1 to TP7): which state each of the pill's two
 * parts shows. Pure logic, no drawing, so every state is tested with fixtures (the live report starts in December, S-20).
 * The forecast's ridge risk never goes in here: "stopped by wind" comes only from a fresh report's `reason`.
 */
internal object TopPillModel {
    /** The first lift's opening hour on TP4 ("opens tomorrow at 09:00"); the handoff's value, there is no data source for it yet. */
    const val FIRST_LIFT = "09:00"
    /** From this hour a closed mountain says when it opens tomorrow. */
    const val EVENING_HOUR = 16
    const val FRESH_SNOW_CM = 5
    const val GUSTS_KMH = 50

    enum class LiftsKind { LIVE, ALL_OPEN, CLOSED, NO_DATA, COUNT, TODAY, LATE }

    /** [windStopped] only from a fresh report; [progress] the wait ring (COUNT, TODAY, LATE); [days] the number inside it. */
    class Lifts(val kind: LiftsKind, val open: Int = 0, val total: Int = 0, val windStopped: Int = 0, val progress: Float = 0f,
                val days: Int = 0, val date: LocalDate? = null, val tomorrowAt: String? = null, val postponed: Boolean = false)

    fun lifts(status: LiftStatus?, opening: OpeningView?, lateOpen: LocalDate?, localHour: Int): Lifts {
        if (status != null && status.fresh && status.names.isNotEmpty()) {
            val open = status.open
            val total = status.names.size
            val wind = status.names.count { status.isOpen(it) == false && status.reason(it) == "wind" }
            return when {
                open == 0 -> Lifts(LiftsKind.CLOSED, 0, total, wind, tomorrowAt = if (localHour >= EVENING_HOUR) FIRST_LIFT else null)
                open == total -> Lifts(LiftsKind.ALL_OPEN, open, total)
                else -> Lifts(LiftsKind.LIVE, open, total, wind)
            }
        }
        return when {
            opening != null && opening.kind == Season.Kind.OPENING_DAY -> Lifts(LiftsKind.TODAY, progress = 1f, date = opening.open)
            opening != null && opening.kind == Season.Kind.COUNTDOWN ->
                Lifts(LiftsKind.COUNT, progress = opening.progress, days = opening.days.coerceAtLeast(1), date = opening.open, postponed = opening.postponed)
            opening == null && lateOpen != null -> Lifts(LiftsKind.LATE, progress = 1f, date = lateOpen)
            else -> Lifts(LiftsKind.NO_DATA)
        }
    }

    enum class Cond { SUN, CLEAR, MOON, CLOUD, SNOW, WIND, FOG, RAIN }
    enum class Line { FRESH_SNOW, GUSTS, SNOW_COMING, COND }

    /** [n] is the cm or the km/h of the line; [cond] always set, it is the icon and the fallback word. */
    class Weather(val temp: Int, val cond: Cond, val line: Line, val n: Int = 0, val night: Boolean = false)

    /** WMO code to the picture; gusts over [GUSTS_KMH] make it wind. Null code (an old answer without it): clear by day. */
    fun cond(code: Int?, gust: Int?, night: Boolean): Cond = when {
        gust != null && gust > GUSTS_KMH -> Cond.WIND
        code == null || code <= 1 -> if (night) Cond.MOON else Cond.SUN
        code == 2 || code == 3 -> Cond.CLOUD
        code == 45 || code == 48 -> Cond.FOG
        code in 71..77 || code == 85 || code == 86 -> Cond.SNOW
        code in 51..67 || code in 80..82 || code >= 95 -> Cond.RAIN
        else -> Cond.CLOUD
    }

    /**
     * The weather part. Line priority: fresh snow (morning only), then gusts, then the condition word; before the season
     * the snow ahead replaces fresh snow (TP6). Null when the forecast has no temperature: the part is then not drawn.
     */
    fun weather(now: Forecast.Now?, code: Int?, at: LocalDateTime, preSeason: Boolean, snowAhead: Int?): Weather? {
        val t = now?.temp ?: return null
        val night = at.hour < 7 || at.hour >= 19
        val gust = now.gust
        val c = cond(code, gust, night)
        return when {
            preSeason && snowAhead != null && snowAhead > 0 -> Weather(t, if (c == Cond.WIND) c else Cond.SNOW, Line.SNOW_COMING, snowAhead, night)
            !preSeason && (now.snow24 ?: 0) >= FRESH_SNOW_CM && at.hour < 12 -> Weather(t, Cond.SNOW, Line.FRESH_SNOW, now.snow24!!, night)
            gust != null && gust > GUSTS_KMH -> Weather(t, Cond.WIND, Line.GUSTS, gust, night)
            else -> Weather(t, c, Line.COND, 0, night)
        }
    }
}
