package net.mamoe.mirai.console.center

import java.io.File

interface PluginCenter {

    data class PluginInsight(
        val name: String,
        val version: String,
        val coreVersion: String,
        val consoleVersion: String,
        val author: String,
        val description: String,
        val tags: List<String>,
        val commands: List<String>
    )

    data class PluginInfo(
        val name: String,
        val version: String,
        val coreVersion: String,
        val consoleVersion: String,
        val tags: List<String>,
        val author: String,
        val contact: String,
        val description: String,
        val usage: String,
        val vcs: String,
        val commands: List<String>,
        val changeLog: List<String>
    )

    /**
     * 获取一些中心的插件基本信息,
     * 能获取到多少由实际的PluginCenter决定
     * 返回 插件名->Insight
     */
    suspend fun fetchPlugin(page: Int): Map<String, PluginInsight>

    /**
     * 尝试获取到某个插件 by 全名, case sensitive
     * null 则没有
     */
    suspend fun findPlugin(name:String):PluginInfo?


    suspend fun <T:Any> T.downloadPlugin(name:String, progressListener:T.(Float) -> Unit): File

    suspend fun downloadPlugin(name:String, progressListener:PluginCenter.(Float) -> Unit): File = downloadPlugin<PluginCenter>(name,progressListener)

    /**
     * 刷新
     */
    suspend fun refresh()

    val name:String
}

internal fun handleReplacement(

){

}
