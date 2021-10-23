package the_worst_one.game

import arc.Core
import arc.util.Timer
import the_worst_one.cfg.Config
import the_worst_one.cfg.Globals
import the_worst_one.db.Driver
import the_worst_one.db.Quest
import the_worst_one.db.Ranks
import the_worst_one.game.u.User
import kotlinx.coroutines.runBlocking
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.gen.Player
import mindustry.gen.Call
import mindustry.net.NetConnection
import mindustry.type.UnitType
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Templates
import java.util.*


// Users keeps needed data about users in ram memory
class Users(private val driver: Driver, logger: Logger, val ranks: Ranks, val config: Config): HashMap<String, User>() {
    val quests: Quest.Quests = Quest.Quests(ranks, driver)
    val vpn = VPN(config, this)

    init {
        logger.on(EventType.PlayerConnect::class.java) {
            it.player.name = Templates.cleanName(it.player.name)
            load(it.player)
        }

        logger.on(EventType.PlayerChatEvent::class.java) {
            val user = get(it.player.uuid())!!
            if(it.message.startsWith("/")) {
                user.data.stats.commands++
            } else {
                user.data.stats.messages++
                user.data.stats.onMessage()
            }
        }

        logger.on(EventType.UnitChangeEvent::class.java) {
            val user = get(it.player?.uuid()) ?: return@on
            if(user.mount != null) {
                user.mount!!.kill()
                user.mount = null
                user.data.stats.onDeath()
            }
        }

        logger.on(EventType.GameOverEvent::class.java) {
            forEach { _, u ->
                if(u.inner.team() == it.winner) {
                    u.data.stats.wins++
                }
                u.data.stats.played++
            }
        }

        logger.on(EventType.UnitDestroyEvent::class.java) {
            var uuid: String? = null
            if(!it.unit.isPlayer || it.unit.type == UnitTypes.alpha || it.unit.type == UnitTypes.beta || it.unit.type == UnitTypes.gamma) {
                val user = get(it.unit.player?.uuid()) ?: return@on
                user.data.stats.onDeath()
                user.data.stats.deaths++
                uuid = user.inner.uuid()
                return@on
            }

            forEach { _, u ->
                if(u.inner.team() != it.unit.team && u.inner.uuid() != uuid) {
                    u.data.stats.killed++
                }
            }
        }

        logger.on(EventType.BlockBuildEndEvent::class.java) {
            if(!it.unit.isPlayer || (!it.breaking && it.tile.block().buildCost < config.data.minBuildCost)) {
                return@on
            }

            val user = get(it.unit.player.uuid())!!
            if(it.breaking) {
                user.data.stats.destroyed++
            } else {
                user.data.stats.built++
            }
        }

        logger.on(EventType.ServerLoadEvent::class.java) {
            if(!Globals.testing) Timer.schedule({
                Core.app.post {
                    logger.run {
                        forEach { _, v ->
                            if (v.afkPoints >= config.data.afkPeriodInMinutes) {
                                v.inner.con.kick(v.data.translate("afk.kick"), 0)
                            } else if (v.afkPoints > 4) {
                                v.send( "afk.warming", v.afkPoints, config.data.afkPeriodInMinutes)
                            }
                            v.afkPoints++
                        }
                    }
                }
            }, 0f, 60f)
        }
    }



    fun reload(target: User) {
        if(!target.paralyzed) save(target)
        load(target.inner, target.data)
    }

    fun save(target: User) {
        target.data.save(driver.config.multiplier, ranks)
    }

    fun test(ip: String = "127.0.0.1", name: String = "name"): User {
        val p = Player.create()
        p.name = name
        p.con = object: NetConnection(ip) {
            override fun send(`object`: Any?, reliable: Boolean) {}
            override fun close() {}
        }

        val ru = driver.users.new(p)
        val u = User(p, ru)
        put(u.data.uuid, u)

        return u
    }

    fun load(player: Player, previous: Driver.RawUser? = null) {
        val existing = driver.users.search(player)
        val user = when (existing.size) {
            0 -> {
                val u = User(player, driver.users.new(player))
                runBlocking { vpn.input.send(u) }
                u
            }
            1 -> User(player, existing[0], previous)
            else -> {
                val sb = StringBuffer()
                for(e in existing) {
                    sb
                        .append("[yellow]")
                        .append(e.id)
                        .append(" [gray]")
                        .append(e.name)
                        .append(" [white]")
                        .append(e.rank.postfix)
                        .append("\n")
                }
                val u = User(player, Driver.RawUser(driver.ranks))
                u.alert("paralyzed.title", "paralyzed.body", sb.toString())
                u
            }
        }

        if(user.data.rank != ranks.griefer && user.data.banned()) {
            user.data.rank = ranks.griefer
            reload(user)
            return
        }

        put(player.uuid(), user)
        runBlocking { quests.input.send(user) }
    }

    fun withdraw(id: Long): Driver.RawUser? {
        if(!driver.users.exists(id)) {
            return null
        }

        val already = values.find { it.data.id == id }
        if(already != null) {
            return already.data
        }

        return driver.users.load(id)
    }

    fun send(key: String, vararg args: Any) {
        forEach { _, v -> v.send(key, *args) }
    }

    fun sendUserMessage(user: User, message: String) {
        forEach { _, v ->
            v.sendPlain(Globals.message(user.data.idName(), user.data.colorMessage(
                if(v.data.muted.contains(user.data.id)) {
                    v.data.translate("mute.muted", user.data.id)
                } else {
                    message
                }
            )))
        }
    }

}
