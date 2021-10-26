package the_worst_one.game.commands

import the_worst_one.cfg.Globals
import the_worst_one.cfg.Globals.time
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import the_worst_one.game.Displayable
import the_worst_one.game.Voting
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Rules
import mindustry.gen.Call
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import java.io.File
import java.lang.Integer.max
import java.lang.StringBuilder

class Boost(val driver: Driver, val voting: Voting, val logger: Logger, override var configPath: String) : Command("boost"), Reloadable, Displayable {
    val create = Voting.Session.Data(1, 5, "create", "boost", Ranks.Perm.Boost)
    val active = mutableListOf<Booster>()
    var config = mutableMapOf<String, Data>()

    init {
        logger.on(EventType.GameOverEvent::class.java) {
            active.clear()
        }
    }

    override fun run(args: Array<String>): Enum<*> {
        val booster = config[args[0]]

        if(booster == null) {
            if(config.isEmpty()) send("boost.inactive")
            else send("boost.unknown", config.keys.joinTo(StringBuilder(), " ").toString())
            return Result.Unknown
        }

        if(args.size == 2) {
            val sb = StringBuilder()
            booster.effects.asIterable().joinTo(sb, "") {"${it.key} ${it.value}\n"}
            sb
                .append("duration:")
                .append(booster.duration.time())
                .append("\nprice\n")
            booster.cost.asIterable().joinTo(sb, "\n") { "${Globals.itemIcons[it.key]!!}${it.value}" }

            alert("boost.info", "placeholder", sb.toString())
            return Result.List
        }



        val missing = driver.items.findMissing(booster.cost)
        if(missing.isNotEmpty()) {
            val sb = StringBuilder()
            for((k, v) in missing) {
                sb
                    .append(Globals.itemIcons[k]!!)
                    .append(v)
            }
            send("boost.missing", sb.toString())
            return Generic.Denied
        }

        voting.add(Voting.Session(create, user!!, args[0]) {
            booster.effects.forEach { apply(it.key, it.value) }
            active.add(Booster(booster, booster.duration))
            driver.items.take(booster.cost)
            Call.setRules(Vars.state.rules)
        })

        return Generic.Success
    }

    override fun reload() {
        try {
            config.clear()
            val raw = Klaxon().parse<Map<String, JsonObject>>(File(configPath))!!
            for((k, v) in raw) {
                config[k] = Klaxon().parseFromJsonObject<Data>(v)!!
            }
            var notOk = false;
            for ((k, v) in config) {
                for (i in v.effects.keys) {
                    val msg = verify(k, i)
                    if(msg != "ok") notOk = true
                    else continue
                    println(msg)
                }
            }

            if(notOk) {
                config.clear()
                val sb = StringBuilder()
                for (f in Rules::class.java.fields) {
                    if (f.type == Float::class.java) {
                        sb
                            .append(f.name)
                            .append(" ")
                    }
                }

                println("boost:: some boosts are invalid")
                println("boost:: available fields: $sb")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("boost", e)
            Fs.createDefault(configPath, mapOf(
                "buildSpeed" to Data()
            ))
        }
    }

    override fun tick() {
        val it = active.iterator()
        while (it.hasNext()) {
            val current = it.next()
            current.timer--
            if(current.timer <= 0L) {
                current.undo()
                it.remove()
                Call.setRules(Vars.state.rules)
            }
        }
    }

    override fun display(user: Driver.RawUser): String {
        val sb = StringBuilder()
        for(a in active) {
            for((k, v) in a.data.effects) sb
                .append(k)
                .append(" ")
                .append(v)
                .append("x ")

            sb
                .append(a.timer.times(1000).time())
                .append(Globals.hudDelimiter)
        }
        return sb.substring(0, max(0, sb.length - Globals.hudDelimiter.length))
    }

    fun verify(name: String, stat: String): String {
        try {
            val prop = Rules::class.java.getField(stat)
            if(prop.type != Float::class.java) return "$name::effects::$stat cannot be configured, it has to be a float"
        } catch (e: Exception) {
            return "property $name::effects::$stat does not exist withing the_worst_one.game rules"
        }

        return "ok"
    }

    companion object {
        fun apply(stat: String, value: Float) {
            val field = Rules::class.java.getField(stat)
            val orig = field.getFloat(Vars.state.rules)
            field.setFloat(Vars.state.rules, value * orig)
        }
    }

    class Booster(val data: Data, var timer: Long) {
        fun undo() {
            for((k, v) in data.effects) {
                val field = Rules::class.java.getField(k)
                val value = field.getFloat(Vars.state.rules)
                field.setFloat(Vars.state.rules, value / v)
            }
        }
    }

    class Data(
        val effects: Map<String, Float> = mapOf("unitBuildSpeedMultiplier" to 3f),
        val duration: Long = 60 * 3,
        val cost: Map<String, Long> = mapOf("copper" to 1L)
    )

    enum class Result {
        Unknown, List
    }
}
