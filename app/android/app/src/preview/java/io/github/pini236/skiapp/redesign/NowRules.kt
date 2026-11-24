package io.github.pini236.skiapp.redesign

import java.time.LocalDate

/** Part 4 (S05, handoff section 4): the rules behind "the mountain now"'s weather tab, apart from how it is drawn. */
internal object NowRules {
    /** A lift's tag: the server's estimate ("low", "medium", "high"), or MTA's report, which wins. */
    data class Tag(val lift: String, val level: String?, val open: Boolean?)

    private val ORDER = listOf("high", "medium", "low")

    /**
     * The tags of the lifts the server estimates, high first, one row of at most [fit]; more than that, only high and
     * medium. A lift with MTA's report shows it instead of the estimate, after the estimates.
     */
    fun tags(risk: Map<String, String?>, report: Map<String, Boolean?>, fit: Int = 3): List<Tag> {
        val all = risk.mapNotNull { (l, lv) ->
            val o = report[l]
            when { o != null -> Tag(l, null, o); lv in ORDER -> Tag(l, lv, null); else -> null }
        }
        val est = all.filter { it.open == null }.sortedBy { ORDER.indexOf(it.level) }
        val rep = all.filter { it.open != null }
        val both = est + rep
        return if (both.size <= fit) both else (est.filter { it.level != "low" } + rep).take(fit)
    }

    /** What the "why" line says: every lift by the estimate, every one by MTA's report, or some of each. */
    enum class Why { ESTIMATE, REPORT, MIXED }

    fun why(tags: List<Tag>): Why? = when {
        tags.isEmpty() -> null
        tags.all { it.open != null } -> Why.REPORT
        tags.any { it.open != null } -> Why.MIXED
        else -> Why.ESTIMATE
    }

    /**
     * The ski days' cards: shown only once the first ski day is in the forecast; during the trip from today on, today
     * first. A day not yet in the forecast keeps its card, with the date the forecast reaches it.
     */
    data class DayCard(val date: LocalDate, val inForecast: Boolean, val opensOn: LocalDate?)

    fun days(skiDays: List<LocalDate>, forecast: List<LocalDate>, today: LocalDate): List<DayCard>? {
        if (skiDays.isEmpty() || forecast.isEmpty()) return null
        val left = skiDays.filter { it >= today }
        if (left.isEmpty()) return null
        if (left.first() !in forecast) return null
        val reach = forecast.size.toLong() - 1
        return left.map { d -> val inF = d in forecast; DayCard(d, inF, if (inF) null else d.minusDays(reach)) }
    }

    /** During the trip the heading is "today on the mountain", else "the forecast for your ski days". */
    fun during(skiDays: List<LocalDate>, today: LocalDate) = skiDays.isNotEmpty() && today >= skiDays.first() && today <= skiDays.last()
}
