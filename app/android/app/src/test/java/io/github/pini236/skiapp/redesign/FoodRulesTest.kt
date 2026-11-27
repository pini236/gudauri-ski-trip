package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.places.Places
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRulesTest {
    private val json = """{"schema":1,"stations":[
      {"id":"gb","ll":[42.47,44.49],"ends":[{"liftId":1,"end":"bottom"}],"info":"some","places":[{"p":"a","d":58},{"p":"b","d":77}]},
      {"id":"gt","ll":[42.48,44.5],"ends":[{"liftId":1,"end":"top"}],"info":"some","places":[{"p":"c","d":40}]}],
     "places":[
      {"id":"a","kind":"food","name":"Dunkin","ll":[42.47,44.49]},
      {"id":"a2","kind":"food","name":"Dunkin","ll":[42.47,44.49]},
      {"id":"b","kind":"cafe","name":"Cafe","ll":[42.47,44.49]},
      {"id":"c","kind":"food","name":"Top","ll":[42.48,44.5],"seasonal":true},
      {"id":"d","kind":"cafe","name":"Run","ll":[42.48,44.5],"slope":true,"liftId":1},
      {"id":"w","kind":"wc","name":"WC","ll":[42.48,44.5]}]}"""
    private fun lift() = Lift("Goodaura", "gondola", FloatArray(0), "1")

    @Test fun listAndOrder() {
        val p = Places.parse(json)!!
        val r = FoodRules.items(p, listOf(lift()), { it.id == "a" || it.id == "b" }, { if (it.id == "c") 2500f else 2000f })
        assertEquals(4, r.size)
        assertEquals("a", r[0].place.id); assertEquals("b", r[1].place.id)
        assertEquals(60, (r[0].where as FoodWhere.FromLift).metres)
        assertTrue(r[2].where is FoodWhere.NearTop || r[2].where is FoodWhere.OnRun)
        assertEquals("c", r[2].place.id)
        assertTrue(r[3].where is FoodWhere.OnRun)
    }
}
