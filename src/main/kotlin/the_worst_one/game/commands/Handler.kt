package the_worst_one.game.commands

import arc.util.CommandHandler
import arc.util.Time
import the_worst_one.bundle.Bundle
import the_worst_one.cfg.Config
import the_worst_one.cfg.Globals.time
import the_worst_one.game.Users
import mindustry.gen.Player
import mindustry_plugin_utils.Logger
import the_worst_one.cfg.Globals

import java.lang.RuntimeException
import java.lang.StringBuilder
import java.util.*


//handler registers the_worst_one.game and terminal commands
class Handler(val users: Users, val logger: Logger, val config: Config, private val kind: Command.Kind, val discord: Discord): HashMap<String, Command>() {
    lateinit var inner: CommandHandler

    fun init(handler: CommandHandler) {
        inner = handler
    }

    // registers any command
    fun reg(command: Command, name: String? = null, recursion: Boolean = false) {
        if (command.alias != null && !recursion) {
            reg(command, command.alias, true)
        }
        if (Command.Kind.Game == kind) {
            try {
                inner.register<Player>(name ?: command.name, command.args, "") { a, p ->
                    val user = users[p.uuid()]
                    if (user == null) {
                        p.sendMessage("[yellow] Please report that you saw this message. You cannot use command due to the bug in server.")
                        return@register
                    }

                    val dif = user.data.commandRateLimit * 1000 - Time.millis() + user.data.lastCommand
                    if(dif > 0 && !user.data.rank.control.admin() && !config.data.rateLimitFreeCommands.contains(command.name)) {
                        user.send("commandRateLimit", dif.time())
                        return@register
                    }

                    user.data.lastCommand = Time.millis()

                    if (user.paralyzed && command.name != "account" && command.name != "help") {
                        user.send("paralyzed")
                        return@register
                    }

                    if(config.data.disabledGameCommands.contains(command.name)) {
                        user.send("disabled")
                        return@register
                    }

                    if(command.control.value > user.data.rank.control.value && command.control.value > user.data.display.control.value) {
                        user.send("tooLowControl", user.data.rank.control, command.control)
                        return@register
                    }

                    if (!config.data.censuredCommands.contains(command.name)) {
                        discord.with("commandLog") {
                            Globals.run { it.restChannel.createMessage(String.format(
                                "id: **%d** name:**%s** rank: **%s** command: **%s** args: **%s**",
                                user.data.id,
                                user.data.name,
                                user.data.rank.name,
                                command.name,
                                a.joinTo(StringBuilder(), " ").toString()
                            )).block() }
                        }
                    }

                    command.user = user

                    logger.run { command.run(a) }
                }
            } catch (e: Exception) {
                RuntimeException("failed to register ${command.name}", e).printStackTrace()
            }
        }

        if (Command.Kind.Cmd == kind) {
            try {
                inner.register(command.name, Bundle.translate("${command.name}.args"), Bundle.translate("${command.name}.desc")) {
                    logger.run { command.run(it) }
                }
            } catch (e: Exception) {
                RuntimeException("failed to register ${command.name}", e).printStackTrace()
            }
        }

        command.kind = kind
        put(command.name, command)
    }
}