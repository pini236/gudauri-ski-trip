package io.github.pini236.skiapp.group

/** Release builds have no pretend server: the real client (server/) or [NoServer]. */
object DevServer {
    fun create(): GroupApi? = null
    fun seed(api: GroupApi, kind: String) = Unit
}
