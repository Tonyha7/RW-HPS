/*
 * Copyright 2020-2023 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.game.simulation.gameFramework

import com.corrodinggames.rts.game.a.a
import com.corrodinggames.rts.game.c
import com.corrodinggames.rts.game.f
import com.corrodinggames.rts.game.n
import com.corrodinggames.rts.game.units.am
import com.corrodinggames.rts.game.units.ar
import com.corrodinggames.rts.gameFramework.j.al
import com.corrodinggames.rts.gameFramework.l
import com.corrodinggames.rts.gameFramework.w
import net.rwhps.server.data.global.Data
import net.rwhps.server.data.global.NetStaticData
import net.rwhps.server.game.simulation.core.AbstractGameData
import net.rwhps.server.game.simulation.core.AbstractPlayerData
import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.net.core.IRwHps
import net.rwhps.server.util.PacketType
import net.rwhps.server.util.WaitResultUtil
import net.rwhps.server.util.inline.findField
import net.rwhps.server.util.inline.toClassAutoLoader
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.log.exp.ImplementedException
import java.io.IOException
import com.corrodinggames.rts.gameFramework.j.`as` as GameNetOutStream


internal class GameData : AbstractGameData {
    override fun getGameData(): Packet {
        val gameEngine: l = GameEngine.gameEngine
        val arVar = GameNetOutStream()
        arVar.c(0)
        arVar.a(Data.game.tickGame.getAndAdd(1))
        arVar.a(gameEngine.by)
        arVar.a(1.0f)
        arVar.a(1.0f)
        arVar.a(false)
        arVar.a(false)
        arVar.e("gameSave")
        wirteGameRsyncData(arVar)
        arVar.a("gameSave")
        return Packet(35,arVar.b(35).c)
    }

    override fun getGameCheck(): Packet {
        val netEngine = GameEngine.netEngine
        val arVar = GameNetOutStream()
        arVar.a(netEngine.ah)
        arVar.a(netEngine.am.a)
        arVar.a(netEngine.am.b.size)
        val it: Iterator<*> = netEngine.am.b.iterator()
        while (it.hasNext()) {
            arVar.a((it.next() as al).b)
        }
        return Packet(PacketType.SYNC_CHECK,arVar.b(PacketType.SYNC_CHECK.typeInt).c)
    }

    @Suppress("UNCHECKED_CAST")
    override fun verifyGameSync(packet: Packet): Boolean {
        var syncFlag = false

        GameInputStream(packet).use { stream ->
            stream.readByte()
            val syncTick = stream.readInt()
            val tick = stream.readInt()
            if (stream.readBoolean()) {
                //stream.readLong()stream.readLong()
                stream.skip(16)
                stream.getDecodeStream(false).use { checkList ->
                    checkList.readInt()
                    if (checkList.readInt() != GameEngine.netEngine.am.b.size) {
                        Log.debug("RustedWarfare", "checkSumSize!=syncCheckList.size()")
                    }
                    val checkTypeList: ArrayList<al> = GameEngine.netEngine.am.b as ArrayList<al>
                    for (checkType in checkTypeList) {
                        val server = checkList.readLong()
                        val client = checkList.readLong()
                        if (server != client) {
                            syncFlag = true
                            Log.debug("RustedWarfare", "CheckType: ${checkType.a} Checksum: $syncTick Server: $server Client: $client")
                        }
                        val tickServer = Data.game.tickGame.get()
                        if (tickServer >= syncTick) {
                            Log.debug("RustedWarfare", "Not marking desync, already resynced before tick: $tickServer <= $tick")
                            return false
                        }

                    }
                }
            }
        }

        return syncFlag
    }

    override fun getWin(team: Int): Boolean {
        val teamData: n = n.k(team) ?: return false

        return !teamData.b() && !teamData.G && !teamData.F && !teamData.E
    }

    override fun getPlayerBirthPointXY() {
        for (player in Data.game.playerManage.playerGroup) {
            n.k(player.site).let {
                var flagA = false
                var flagB = false
                var x: Float? = null
                var y: Float? = null
                var x2: Float? = null
                var y2: Float? = null

                for (amVar in am.bF()) {
                    if ((amVar is am) && !amVar.bV && amVar.bX == it) {
                        if (amVar.bO && !flagA) {
                            flagA = true
                            x = amVar.eo
                            y = amVar.ep
                        }
                        if (amVar.bP && !flagB) {
                            flagB = true
                            x2 = amVar.eo
                            y2 = amVar.ep
                        }
                    }
                }

                if (x == null) {
                    x = x2
                    y = y2
                }
                Log.clog("Site ${player.site} , $x $y")
            }
        }
    }


    override fun clean() {
        if (NetStaticData.ServerNetType != IRwHps.NetType.ServerProtocol) {
            return
        }
        val gameEngine: l = GameEngine.gameEngine
        gameEngine.bX.b("exited")
        InterruptedException().printStackTrace()
        Thread.sleep(100)
    }

    override fun getDefPlayerData(): AbstractPlayerData {
        return initValue
    }

    override fun getPlayerData(site: Int): AbstractPlayerData {
        return PrivateClass_Player(WaitResultUtil.waitResult { n.k(site) } ?: throw ImplementedException.PlayerImplementedException("[PlayerData-New] Player is invalid"))
    }

    private val initValue = object: AbstractPlayerData {
        private val error: ()->Nothing get() = throw ImplementedException.PlayerImplementedException("[Player] No Bound PlayerData")
        override val survive get() = error()
        override val unitsKilled get() = error()
        override val buildingsKilled get() = error()
        override val experimentalsKilled get() = error()
        override val unitsLost get() = error()
        override val buildingsLost get() = error()
        override val experimentalsLost get() = error()

        override var credits: Int = 0
    }

    private fun wirteGameRsyncData(asVar: GameNetOutStream) {
        val gameEngine = GameEngine.gameEngine
        try {
            asVar.c("rustedWarfareSave")
            asVar.a(gameEngine.c(true))
            asVar.a(96)
            asVar.a(gameEngine.ar)
            asVar.a("saveCompression", true)
            asVar.e("customUnitsBlock")
            com.corrodinggames.rts.game.units.custom.l.a(asVar)
            asVar.a("customUnitsBlock")
            asVar.e("gameSetup")
            val z = gameEngine.bX.B || gameEngine.bX.F
            asVar.a(gameEngine.bX.B)
            asVar.a(gameEngine.bX.F)
            asVar.a(z)
            if (z) {
                gameEngine.bX.a(asVar)
            }
            asVar.a("gameSetup")
            asVar.c(gameEngine.dl)
            val z2 = gameEngine.dm != null
            asVar.a(z2)
            if (z2) {
                // Writing remote map steam into save
                asVar.a(gameEngine.dm)
            }
            asVar.a(gameEngine.by)
            asVar.a(gameEngine.cy + gameEngine.cI)
            asVar.a(gameEngine.cz + gameEngine.cJ)
            asVar.a(gameEngine.cV)
            asVar.a("com.corrodinggames.rts.gameFramework.aa".toClassAutoLoader(this)!!
                .findField("a",Int::class.java)!!.get(gameEngine.bV) as Int
            )
            asVar.a(0)
            asVar.e()
            gameEngine.bL.a(asVar)
            asVar.a(gameEngine.bv)
            asVar.a(gameEngine.bL.E)
            asVar.a(gameEngine.bL.F)
            asVar.a(gameEngine.bL.G)
            asVar.a(gameEngine.ce != null)
            if (gameEngine.ce != null) {
                gameEngine.ce.a(asVar)
            }
            asVar.e()
            var i = -1
            if (gameEngine.bs != null) {
                i = gameEngine.bs.k
            }
            asVar.a(i)
            asVar.a(n.c)
            for (i2 in 0 until n.c) {
                val k = n.k(i2)
                asVar.a(k is a)
                asVar.a(k is c)
                asVar.a(k != null)
                k?.b(asVar)
            }

            // Section: unit shells
            val er = (w.er.clone() as com.corrodinggames.rts.gameFramework.utility.s)
            asVar.a(er.size)
            val it: Iterator<*> = er.iterator()
            while (it.hasNext()) {
                val wVar: w = it.next() as w? ?: throw RuntimeException("Found null in fastGameObjectList")
                if (wVar is am) {
                    val amVar = wVar
                    if (amVar.r() is ar) {
                        asVar.c(1)
                        asVar.a(amVar.r() as ar as Enum<*>)
                    } else if (amVar.r() is com.corrodinggames.rts.game.units.custom.l) {
                        asVar.c(3)
                        asVar.c((amVar.r() as com.corrodinggames.rts.game.units.custom.l).M)
                    } else {
                        throw IOException("Unhandled getUnitType on save:" + amVar.r().javaClass.toString())
                    }
                } else {
                    asVar.c(2)
                    if (wVar is com.corrodinggames.rts.game.l) {
                        asVar.c(1)
                    } else if (wVar is f) {
                        asVar.c(2)
                    } else if (wVar is com.corrodinggames.rts.gameFramework.d.f) {
                        asVar.c(3)
                    } else {
                        val str: String = wVar::class.java.toString()
                        throw IOException("Unhandled class on save: $str")
                    }
                }
                asVar.a(wVar.eh)
            }
            asVar.d("Section: CurrentUnitId")
            asVar.a(gameEngine.bX.z())
            gameEngine.bV.a(asVar)
            gameEngine.bS.a(asVar)
            gameEngine.bY.a(asVar)
            // 写入用户数据
            for (i3 in 0 until n.c) {
                val k2 = n.k(i3)
                k2?.a(asVar)
            }
            asVar.e()
            val it2: Iterator<*> = er.iterator()
            while (it2.hasNext()) {
                val wVar2: w = it2.next() as w
                wVar2.a(asVar)
                asVar.e()
            }
            asVar.a("saveCompression")
            asVar.e()
            asVar.c("<SAVE END>")
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }

    }
}
