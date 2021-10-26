package the_worst_one.game.commands

import arc.files.Fi
import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import mindustry.io.MapIO
import java.io.File

class MapManager(val driver: Driver): Command("mapmanager", Ranks.Control.High) {
    override fun run(args: Array<String>): Enum<*> {
        val map = if ("add update".contains(args[0])) {
            val file = when(kind) {
                Kind.Discord -> {
                    if(message!!.attachments.isEmpty()) {
                        send("mapmanager.discord.missingMap")
                        return Generic.NotEnough
                    }
                    try {
                        loadDiscordAttachment("maps")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        send("mapmanager.discord.loadFailed", e.message ?: "none")
                        return Result.DiscordLoadFailed
                    }
                }
                else -> {
                    if(args.size > 2 && args[2] == "dir") {
                        for(file in File(args[1]).walkBottomUp()) {
                            run(arrayOf("add", file.path))
                        }
                        return Generic.Success
                    }

                    val file = File(args[if(args[0] == "add") 1 else {
                        if(ensure(args, 3)) return Generic.NotEnough
                        2
                    }])
                    if (!file.exists()) {
                        send("mapmanager.notFound")
                        return Generic.NotFound
                    }
                    file
                }
            }
            try {
                MapIO.createMap(Fi(file), true)
            } catch (e: Exception) {
                send("mapmanager.add.invalid")
                return Result.Invalid
            }
        } else null

        val id = if("update remove activate deactivate".contains(args[0])) {
            if(notNum(1, args)) return Generic.NotAInteger
            val id = num(args[1])
            if(!driver.maps.exists(id)) {
                send("mapmanager.nonexistent")
                return Result.Nonexistent
            }

            if("deactivate remove".contains(args[0])) try {
                driver.maps.deactivate(num(args[1]))
            } catch (e: Exception) {
                if(args[0] == "deactivate") {
                    e.printStackTrace()
                    send("mapmanager.deactivate.fail", e.message ?: "none")
                    return Result.Error
                }
            }

            id
        } else 0

        when(args[0]) {
            "add" -> {
                val newId = driver.maps.add(map!!)
                if(newId != Long.MAX_VALUE) {
                    send("mapmanager.add.success", newId)
                    return Generic.Success
                }
                args[0] = "update"
            }
            "update" -> driver.maps.update(id, map!!)
            "remove" -> {
                driver.maps.remove(id)
            }
            "activate" -> try {
                driver.maps.activate(id)
            } catch (e: Exception) {
                e.printStackTrace()
                send("mapmanager.activate.fail", e.message ?: "none")
            }
            "deactivate" -> {}
            else -> {
                send("wrongOption", "add update remove activate")
                return Generic.Mismatch
            }
        }

        send("mapmanager.${args[0]}.success")
        return Generic.Success
    }

    enum class Result {
        Nonexistent, Invalid, DiscordLoadFailed, Error
    }
}