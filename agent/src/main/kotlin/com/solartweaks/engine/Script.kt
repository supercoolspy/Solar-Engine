package com.solartweaks.engine

import com.solartweaks.engine.tweaks.ChatSerializer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess

// function command(args)
const val commandEndpoint = "command"
const val scriptLanguage = "js"

fun String.asGraalContext(language: String = scriptLanguage): Context =
    Context.newBuilder(language)
        .allowHostClassLookup { true }
        .allowHostAccess(HostAccess.ALL).build()
        .also { it.getBindings(language).putMember("api", JSAPI) ; it.eval(language, this) }

fun Context.runCommand(args: List<String>, language: String = scriptLanguage) =
    getBindings(language).getMember(commandEndpoint).executeVoid(args)

object JSAPI {
    val player get() = client.player
    val world get() = client.world
    val minecraft get() = client
    fun toComponent(json: String) = ChatSerializer.jsonToComponent(json)
}