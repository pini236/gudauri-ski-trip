package io.github.pini236.skiapp.dayrec

import android.content.Context
import io.github.pini236.skiapp.data.SiteData
import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import io.github.pini236.skiapp.dayrec.analysis.Net
import io.github.pini236.skiapp.dayrec.analysis.Params
import org.json.JSONObject
import java.io.File

/**
 * The analysis of the ski day on the phone: loads the network (the site's runs and lifts, as the app has them now, and the
 * terrain) and the numbers (tools/fixtures/dayrec-params.json, packaged by tools/build-app-data.py) once per process, and
 * writes a day's result beside its log. The result can always be computed again from the log, so a missing or old one is
 * not a loss ([ensure]).
 */
object DayAnalysis {
    @Volatile private var loaded: Pair<Analyzer, Params>? = null

    private fun load(ctx: Context): Pair<Analyzer, Params> {
        loaded?.let { return it }
        synchronized(this) {
            loaded?.let { return it }
            val c = ctx.applicationContext
            val read = { name: String -> c.assets.open("data/$name").bufferedReader().use { it.readText() } }
            val params = Params(JSONObject(read("dayrec-params.json")))
            @Suppress("DEPRECATION")
            val info = c.packageManager.getPackageInfo(c.packageName, 0)
            val version = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            val site = SiteData(File(c.filesDir, "site-data"), version, read)
            val net = Net(JSONObject(site.read("runs-and-lifts.json")), JSONObject(read("terrain.json")), params)
            return (Analyzer(net, params) to params).also { loaded = it }
        }
    }

    fun analyzer(ctx: Context): Analyzer = load(ctx).first
    fun version(ctx: Context): Int = load(ctx).second.analysis
    fun engine(ctx: Context, header: JSONObject) = DayEngine(analyzer(ctx), header)

    /** The result of [day] beside it, computed now if it is missing or of an older analysis. Heavy for a long day: not on the main thread. */
    fun ensure(ctx: Context, file: File): File? {
        val (a, p) = load(ctx)
        return write(a, p.analysis, file)
    }

    /** Pure part of [ensure], so a test can run it. */
    internal fun write(a: Analyzer, analysis: Int, file: File): File? {
        val out = DayStore.resultOf(file)
        if (out.isFile && runCatching { JSONObject(out.readText()).optInt("analysis", -1) == analysis }.getOrDefault(false)) return out
        val day = runCatching { DayLog.read(file) }.getOrNull() ?: return null
        if (day.points.isEmpty()) return null
        val r = a.final(day.header, day.points, day.events)
        val json = ResultJson.of(r, day.resort, analysis)
        val tmp = File(out.parentFile, out.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(out)) { out.delete(); if (!tmp.renameTo(out)) return null }
        return out
    }
}
