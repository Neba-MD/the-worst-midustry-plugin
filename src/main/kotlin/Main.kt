import arc.util.CommandHandler
import cfg.Config
import com.beust.klaxon.Klaxon
import db.Driver
import db.Ranks
import game.Users
import game.commands.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.mod.Plugin
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import java.io.File


class Main : Plugin(), Configure.Reloadable{

    private val root = "config/mods/worst/"


    override val configPath = root + "config.json"
    private val logger = Logger(root + "logger/config.json")
    private val ranks = Ranks(root + "ranks/config.json")
    private val driver = Driver(root + "databaseDriver/config.json", ranks)
    private val users = Users(driver, logger, ranks)

    private val config = Config()

    private val discord = Discord(root + "bot/config.json")
    private val game = Handler(users, logger, Command.Kind.Game)
    private val terminal = Handler(users, logger, Command.Kind.Cmd)

    override fun reload() {
        config.data = try {
            Klaxon().parse(File(configPath))!!
        } catch(e: Exception) {
            Fs.createDefault(configPath, Config.Data())
            Config.Data()
        }
    }

    private val reloadable = mapOf(
        "main" to this,
        "bot" to discord,
        "driver" to driver,
        "ranks" to ranks
    )

    init {
        reload()

        discord.reg(Help(game, discord))
        discord.reg(Execute(driver))
        discord.reg(SetRank(driver, users, ranks))
        discord.reg(Link(driver, discord))
        discord.reg(Configure(reloadable))

        runBlocking {
            GlobalScope.launch {
                discord.handler?.launch()
            }
        }
    }

    override fun registerClientCommands(handler: CommandHandler) {
        bulkRemove(handler, "")

        game.init(handler)

        game.reg(Help(game, discord))
        game.reg(Execute(driver))
        game.reg(SetRank(driver, users, ranks))
        game.reg(Account(driver, users, discord, config))
        game.reg(Configure(reloadable))
    }

    override fun registerServerCommands(handler: CommandHandler) {
        bulkRemove(handler, "help")

        terminal.init(handler)

        terminal.reg(Execute(driver))
        terminal.reg(SetRank(driver, users, ranks))
        terminal.reg(Configure(reloadable))
    }

    private fun bulkRemove(handler: CommandHandler, toRemove: String) {
        for(s in toRemove.split(" ")) handler.removeCommand(s)
    }
}
