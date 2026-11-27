package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.places.Place
import io.github.pini236.skiapp.places.Places

/** Where a place is, as the food screen says it (UX pass part 4). */
internal sealed class FoodWhere {
    class FromLift(val metres: Int, val lift: String) : FoodWhere()
    class NearTop(val lift: String) : FoodWhere()
    class NearBottom(val lift: String) : FoodWhere()
    class OnRun(val lift: String) : FoodWhere()
    object None : FoodWhere()
}

internal class FoodItem(val place: Place, val inVillage: Boolean, val where: FoodWhere, val height: Float)

/** The food screen's list, pure: kinds food and cafe, one row per name, the village by distance and the mountain from the top down. */
internal object FoodRules {
    fun items(places: Places, lifts: List<Lift>, inPicture: (Place) -> Boolean, height: (Place) -> Float): List<FoodItem> {
        val seen = HashSet<String>()
        val cand = places.places.filter { it.kind == "food" || it.kind == "cafe" }.filter { !it.name.isNullOrBlank() && seen.add(it.name!!.trim().lowercase()) }
        fun liftName(id: String) = lifts.firstOrNull { it.id == id }?.name
        // the nearest station that lists the place: its distance and the lift at it
        class At(val d: Int, val lift: String, val bottom: Boolean)
        val at = HashMap<String, At>()
        for (s in places.stations) for (n in places.near(s)) {
            val e = s.ends.firstNotNullOfOrNull { en -> liftName(en.liftId)?.let { it to en.bottom } } ?: continue
            if ((at[n.place.id]?.d ?: Int.MAX_VALUE) > n.d) at[n.place.id] = At(n.d, e.first, e.second)
        }
        val out = cand.map { p ->
            val vil = inPicture(p); val a = at[p.id]
            val w = when {
                p.slope && liftName(p.liftId) != null -> FoodWhere.OnRun(liftName(p.liftId)!!)
                a == null -> FoodWhere.None
                vil -> FoodWhere.FromLift(((a.d + 5) / 10) * 10, a.lift)
                a.bottom -> FoodWhere.NearBottom(a.lift)
                else -> FoodWhere.NearTop(a.lift)
            }
            FoodItem(p, vil, w, height(p))
        }
        val (v, r) = out.partition { it.inVillage }
        return v.sortedBy { (it.where as? FoodWhere.FromLift)?.metres ?: Int.MAX_VALUE } + r.sortedByDescending { it.height }
    }
}
