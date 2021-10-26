package the_worst_one.game

import arc.Core
import arc.struct.Seq
import arc.util.Timer
import the_worst_one.cfg.Globals
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry_plugin_utils.Logger

class Hud(val users: Users, private val displayed: Array<Displayable>, logger: Logger) {

    init {
        logger.on(EventType.ServerLoadEvent::class.java) {
            if(!Globals.testing) Timer.schedule({
                Core.app.post {
                    logger.run {
                        update()
                    }
                }
            }, 0f, 1f)
        }
    }

    val buff = Seq<String>()

    fun update() {
        val sb = StringBuilder()
        displayed.forEach { it.tick() }


        users.forEach { k, u ->
            if (!u.inner.con.isConnected) {
                u.disconnect(users)
                buff.add(k)
                return@forEach
            }
            for(d in displayed) {
                sb.append(d.display(u.data))
            }
            if(sb.isNotEmpty()) {
                Call.setHudText(u.inner.con, sb.toString())
                sb.clear()
            } else Call.hideHudText(u.inner.con)
        }

        for(b in buff) {
            users.remove(b)
        }

        buff.clear()
    }
}