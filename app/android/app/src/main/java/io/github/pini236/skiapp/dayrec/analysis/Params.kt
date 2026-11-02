package io.github.pini236.skiapp.dayrec.analysis

import org.json.JSONObject

/**
 * The numbers of the day analysis: tools/fixtures/dayrec-params.json, as it is (decision 76, m-8 condition 4). Every
 * threshold and variance lives in that file and nowhere in code; calibrating in the season is a change to it.
 * `d` reads a number of a section, `i` the same as an integer.
 */
class Params(val j: JSONObject) {
    fun d(section: String, key: String): Double = j.getJSONObject(section).getDouble(key)
    fun i(section: String, key: String): Int = j.getJSONObject(section).getInt(key)
    val analysis: Int get() = j.getInt("analysis")
    val gapS: Double get() = j.getDouble("gap_s")
    val resetGapS: Double get() = j.getDouble("reset_gap_s")

    /** The default cable speed of a lift kind, m/s, or the one of "other". */
    fun defaultLiftSpeed(kind: String?): Double {
        val m = j.getJSONObject("lift").getJSONObject("default_speed_mps")
        return if (kind != null && m.has(kind)) m.getDouble(kind) else m.getDouble("other")
    }
}
