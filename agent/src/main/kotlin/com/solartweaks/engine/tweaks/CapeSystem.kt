package com.solartweaks.engine.tweaks

import com.solartweaks.engine.*
import com.solartweaks.engine.util.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type

fun shouldImplementItems() = minecraftVersion.id in listOf("v1_8", "v1_7", "v1_12")
val shouldImplementItemsCached by lazy { shouldImplementItems() }

fun initCapeSystemTweaks() {
    withModule<CapeSystem> {
        finders.optifineClass("HttpUtils") {
            methods {
                namedTransform("getPlayerItemsUrl") { fixedValue(ConfigRegistry.cosmeticsServer) }
            }
        }

        finders.optifineClass("PlayerConfigurations") {
            methods {
                namedTransform("getPlayerConfiguration") {
                    whenImplementItems {
                        getObject<ConfigRegistry>()
                        load(0)
                        getPlayerName()
                        invokeMethod(ConfigRegistry::class.java.getMethod("getRawConfig", String::class.java))
                        cast(method.returnType)
                        returnMethod(ARETURN)
                    }
                }

                namedTransform("setPlayerConfiguration") {
                    whenImplementItems {
                        getObject<ConfigRegistry>()
                        load(0)
                        load(1)
                        invokeMethod(
                            ConfigRegistry::class.java.getMethod(
                                "setConfig",
                                String::class.java,
                                Any::class.java
                            )
                        )
                        returnMethod()
                    }
                }
            }
        }

        finders.optifineClass("PlayerItemsLayer") {
            methods {
                named("renderEquippedItems") {
                    arguments[1] = Type.FLOAT_TYPE
                    arguments[2] = Type.FLOAT_TYPE
                    arguments count 3

                    transform {
                        whenImplementItems {
                            val field = owner.fieldData.first()
                            loadThis()
                            getField(field)
                            cast(renderPlayer().type)
                            invokeMethod(playerGetMainModel())
                            load(1)
                            dup()
                            getPlayerName()
                            load(2, FLOAD)
                            load(3, FLOAD)
                            invokeMethod(::renderItems)
                            returnMethod()
                        }
                    }
                }
            }
        }
    }
}

fun MethodTransformContext.whenImplementItems(impl: MethodVisitor.() -> Unit) = methodEnter {
    val label = Label()
    getProperty(::shouldImplementItemsCached)
    visitJumpInsn(IFEQ, label)
    impl()
    visitLabel(label)
}

val playerConfiguration = finders.optifineClass("PlayerConfiguration") {
    methods {
        named("renderPlayerItems")
        "construct" { method.isConstructor() }
    }

    fields {
        "playerItemModels" {
            node match { Type.getType(it.field.desc).sort == Type.ARRAY }
        }
    }
}

val playerConfigAccess by accessor<_, PlayerConfiguration.Static>(playerConfiguration) {
    loadOptifineClass("PlayerConfiguration", "player")
}

interface PlayerConfiguration : InstanceAccessor {
    val playerItemModels: Array<Any>
    fun renderPlayerItems(model: Any?, player: Any?, x: Float, y: Float)

    interface Static : StaticAccessor<PlayerConfiguration> {
        fun construct(): PlayerConfiguration
    }

    companion object : Static by playerConfigAccess.static
}

val fileDownloadThread = finders.optifineClass("FileDownloadThread") {
    methods { "construct" { method.isConstructor() } }
}

val fdtAccess by accessor<_, FileDownloadThread.Static>(fileDownloadThread) {
    loadOptifineClass("FileDownloadThread", "http")
}

interface FileDownloadThread : InstanceAccessor {
    interface Static : StaticAccessor<FileDownloadThread> {
        fun construct(path: String, listener: Any): Thread
    }

    companion object : Static by fdtAccess.static
}

val playerConfigReceiver = finders.optifineClass("PlayerConfigurationReceiver") {
    methods {
        "construct" { method.isConstructor() }
        namedTransform("fileDownloadFinished") {
            replaceCall(
                matcher = { it.name == "dbg" },
                replacement = { pop() }
            )
        }
    }
}

val playerConfigRecvAccess by accessor<_, PlayerConfigurationReceiver.Static>(playerConfigReceiver) {
    loadOptifineClass("PlayerConfigurationReceiver", "player")
}

interface PlayerConfigurationReceiver : InstanceAccessor {
    interface Static : StaticAccessor<PlayerConfigurationReceiver> {
        fun construct(userName: String): PlayerConfigurationReceiver
    }

    companion object : Static by playerConfigRecvAccess.static
}

@Suppress("unused")
object ConfigRegistry {
    // Player to config
    private val configs = mutableMapOf<String, PlayerConfiguration>()
    val cosmeticsServer = getModule<CapeSystem>().serverURL.replace("https://", "http://")

    fun getConfig(player: String) = configs.getOrPut(player) {
        runCatching {
            FileDownloadThread.construct(
                path = "$cosmeticsServer/users/$player.cfg",
                listener = PlayerConfigurationReceiver.construct(player).delegate
            ).start()
        }.onFailure {
            println("Failed to download config for $player")
            it.printStackTrace()
        }

        PlayerConfiguration.construct()
    }

    fun getRawConfig(player: String) = getConfig(player).delegate
    fun setConfig(player: String, config: Any?) {
        if (config != null) {
            configs[player] = PlayerConfiguration.cast(config)
        }
    }

    fun clear() {
        configs.clear()
    }
}

fun renderItems(model: Any?, player: Any?, playerName: String, x: Float, y: Float) {
    with(glStateManager) {
        color(1f, 1f, 1f, 1f)
        disableRescaleNormal()
        enableCull()

        runCatching { ConfigRegistry.getConfig(playerName).renderPlayerItems(model, player, x, y) }.onFailure {
            println("Failed to render config for player")
            it.printStackTrace()
        }

        disableCull()
    }
}

val capeUtils = finders.optifineClass("CapeUtils") {
    methods { named("reloadCape") }
}

val capeUtilsAccess by accessor<_, CapeUtils.Static>(capeUtils)

interface CapeUtils : InstanceAccessor {
    interface Static : StaticAccessor<CapeUtils> {
        fun reloadCape(player: PlayerBridge)
    }

    companion object : Static by capeUtilsAccess.static
}

fun optifineClassMatcher(name: String): ClassMatcher =
    { it.name.startsWith(optifinePackage) && it.name.endsWith(name) }

fun FinderContext.optifineClass(name: String, block: ClassContext.() -> Unit) = findClass {
    node match optifineClassMatcher(name)
    block()
}