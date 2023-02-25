package com.solartweaks.engine.tweaks

import com.solartweaks.engine.*
import com.solartweaks.engine.util.InvocationType
import com.solartweaks.engine.util.asmTypeOf
import com.solartweaks.engine.util.invokeMethod
import com.solartweaks.engine.util.load
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.IFEQ

fun initCustomCommands() {
    findMinecraftClass {
        methods {
            named("bridge\$getClientBrand")
            "commandEvent" {
                val callbackName = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"
                method hasDesc "(Ljava/lang/String;L$callbackName;)V"
                strings has "/"

                transform {
                    methodExit {
                        load<String>(1)
                        invokeMethod(::handleCommand)
                        val label = Label()
                        visitJumpInsn(IFEQ, label)
                        load<Any>(2)
                        invokeMethod(
                            invocationType = InvocationType.VIRTUAL,
                            owner = callbackName,
                            name = "cancel",
                            descriptor = "()V"
                        )
                        visitLabel(label)
                    }
                }
            }
        }
    }
}

val builtinCommands: Map<String, (List<String>) -> Unit> = mapOf(
    "reloadcapesystem" to {
        if (isModuleEnabled<CapeSystem>()) {
            sendChatMessage("Reloading all cosmetics...")

            runCatching {
                ConfigRegistry.clear()
                client.world?.actualPlayerEntities?.forEach(CapeUtils::reloadCape)
            }.onSuccess { sendChatMessage("Reloaded all cosmetics!", color = "green") }.onFailure {
                sendChatMessage("Failed to reload all cosmetics: $it", color = "red")
                it.printStackTrace()
            }
        } else {
            sendChatMessage("The Cape System module has not been enabled!", color = "red")
        }
    },
    "solartweaks" to {
        client.player.addChatMessage(
            ChatSerializer.jsonToComponent(
                """
            [
                "",
                {
                    "text": "Solar Engine Debug",
                    "color": "red"
                },
                {
                    "text": "\n\n"
                },
                {
                    "text": "Minecraft Version: ",
                    "color": "green"
                },
                {
                    "text": "${minecraftVersion.formatted}\n"
                },
                {
                    "text": "Engine Version: ",
                    "color": "green"
                },
                {
                    "text": "$version\n"
                },
                {
                    "text": "Username: ",
                    "color": "green"
                },
                {
                    "text": "${client.player.name}\n"
                },
                {
                    "text": "UUID: ",
                    "color": "green"
                },
                {
                    "text": "${client.player.gameProfile.id ?: "Not on legitimate account"}\n"
                },
                {
                    "text": "Current Server: ",
                    "color": "green"
                },
                {
                    "text": "${client.currentServerData?.serverIP() ?: "Singleplayer"}\n"
                },
                {
                    "text": "Active modules (${enabledModules().size}): ",
                    "color": "green"
                },
                {
                    "text": "${enabledModules().joinToString { it::class.java.simpleName }}\n"
                },
                {
                    "text": "Loaded cosmetics: ",
                    "color": "green"
                },
                {
                    "text": "${
                    if (isModuleEnabled<CapeSystem>() && shouldImplementItemsCached) {
                        ConfigRegistry.getConfig(client.player.name).playerItemModels.size
                    } else "Not enabled/overridden/1.16+"
                }\n"
                }
            ]
            """
            )
        )
    },
    "reloadscripts" to {
        globalConfiguration.customCommands.reloadScripts()
        sendChatMessage("Done!", color = "green")
    },
    "reloadmice" to {
        RawInputThread.rescanMice()
        sendChatMessage("Done!", color = "green")
    }
)

fun handleCommand(command: String): Boolean {
    val (commands) = globalConfiguration.customCommands
    val allCommands = commands.mapValues {
        { args: List<String> ->
            LocalPlayer.castAccessor(client.player).sendChatMessage("${it.value} ${args.joinToString(" ")}")
        }
    } + builtinCommands + globalConfiguration.customCommands.loadedScripts

    val partialArgs = command.split(' ')
    return allCommands[partialArgs.first().removePrefix("/")]?.invoke(partialArgs.drop(1)) != null
}

fun sendChatMessage(msg: String, color: String = "gray") = client.player.addChatMessage(
    ChatSerializer.jsonToComponent(
        """
        [
            "",
            {
                "text": "${msg.replace("\"", "\\\"")}",
                "color": "$color"
            }
        ]
        """.trimIndent()
    )
)

val chatSerializer = finders.findClass {
    strings hasPartial "Don't know how to turn"
    methods {
        "jsonToComponent" {
            arguments[0] = asmTypeOf<String>()
            method.isStatic()
        }
    }
}

val chatSerializerAccess by accessor<_, ChatSerializer.Static>(chatSerializer)

interface ChatSerializer : InstanceAccessor {
    interface Static : StaticAccessor<ChatSerializer> {
        fun jsonToComponent(json: String): Any
    }

    companion object : Static by chatSerializerAccess.static
}