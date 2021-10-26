package the_worst_one.game.commands

import the_worst_one.cfg.Reloadable
import the_worst_one.db.Ranks
import mindustry_plugin_utils.Json
import mindustry_plugin_utils.Enums

class Configure(val targets: Map<String, Reloadable>): Command("configure", Ranks.Control.Absolute) {
    override fun run(args: Array<String>): Enum<*> {
        val target = targets[args[0]]
        if (target == null) {
            val sb = StringBuilder()
            for((k, _) in targets) {
                sb.append(k).append(" ")
            }
            send("configure.unknown", sb.toString())
            return Result.Unknown
        }

        when(args[1]) {
            "view" -> {
                send("configure.view", target.view)
                return Result.View
            }
            "reload" -> {
                target.reload()
                send("configure.reload")
                return Result.Reload
            }
        }

        val r = when(args.size) {
            5 -> target.modify(args[1].capitalize(), args[2].capitalize(), args[3], args[4])
            3 -> target.modify(args[1].capitalize(), "Null", args[2], "")
            else -> {
                send("configure.count")
                return Result.Count
            }
        }

        val key = "configure.edit.${if(r != "") r else "success"}"
        when(r) {
            "type" ->  send(key, Enums.list(Json.Type::class.java))
            "method" -> send(key, Enums.list(Json.Method::class.java))
            else -> send(key)
        }

        return Result.Result
    }

    enum class Result {
        Unknown, Result, Reload, View, Count, Denied
    }


}