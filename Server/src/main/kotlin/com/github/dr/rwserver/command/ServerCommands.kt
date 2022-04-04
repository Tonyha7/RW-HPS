/*
 * Copyright 2020-2022 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package com.github.dr.rwserver.command

import com.github.dr.rwserver.Main
import com.github.dr.rwserver.core.Call
import com.github.dr.rwserver.core.Call.sendMessage
import com.github.dr.rwserver.core.Call.sendSystemMessage
import com.github.dr.rwserver.core.Call.sendTeamData
import com.github.dr.rwserver.core.Call.upDataGameData
import com.github.dr.rwserver.core.thread.TimeTaskData
import com.github.dr.rwserver.data.global.Data
import com.github.dr.rwserver.data.global.Data.LINE_SEPARATOR
import com.github.dr.rwserver.data.global.NetStaticData
import com.github.dr.rwserver.data.player.Player
import com.github.dr.rwserver.data.plugin.PluginManage
import com.github.dr.rwserver.func.StrCons
import com.github.dr.rwserver.game.GameMaps
import com.github.dr.rwserver.game.event.EventType.GameOverEvent
import com.github.dr.rwserver.game.event.EventType.PlayerBanEvent
import com.github.dr.rwserver.util.IsUtil
import com.github.dr.rwserver.util.Time
import com.github.dr.rwserver.util.Time.getTimeFutureMillis
import com.github.dr.rwserver.util.game.CommandHandler
import com.github.dr.rwserver.util.game.Events
import com.github.dr.rwserver.util.log.Log
import com.github.dr.rwserver.util.log.Log.error
import java.io.IOException

/**
 * @author Dr
 */
class ServerCommands(handler: CommandHandler) {
    private fun registerPlayerCommand(handler: CommandHandler) {
        handler.register("say", "<text...>", "serverCommands.say") { arg: Array<String>, _: StrCons ->
            val response = StringBuilder(arg[0])
            var i = 1
            val lens = arg.size
            while (i < lens) {
                response.append(" ").append(arg[i])
                i++
            }
            if (Data.config.SingleUserRelay) {
                try {
                    NetStaticData.relay.groupNet.broadcast(NetStaticData.protocolData.abstractNetPacket.getSystemMessagePacket(response.toString().replace("<>", "")))
                } catch (ignored: IOException) {
                }
            }
            sendSystemMessage(response.toString().replace("<>", ""))
        }
        handler.register("gameover", "serverCommands.gameover") { _: Array<String>?, _: StrCons ->
            Events.fire(GameOverEvent())
        }
        handler.register("admin", "<add/remove> <PlayerPosition> [SpecialPermissions]", "serverCommands.admin") { arg: Array<String>, log: StrCons ->
            if (Data.game.isStartGame) {
                log[localeUtil.getinput("err.startGame")]
                return@register
            }
            if (!("add" == arg[0] || "remove" == arg[0])) {
                log["Second parameter must be either 'add' or 'remove'."]
                return@register
            }
            val add = "add" == arg[0]
            val site = arg[1].toInt() - 1
            val player = Data.game.playerManage.getPlayerArray(site)
            val supAdmin = arg.size > 2
            if (player != null) {
                if (add) {
                    Data.core.admin.addAdmin(player.uuid,supAdmin)
                } else {
                    Data.core.admin.removeAdmin(player.uuid)
                }

                player.isAdmin = add
                player.superAdmin = supAdmin

                try {
                    player.con!!.sendServerInfo(false)
                } catch (e: IOException) {
                    error("[Player] Send Server Info Error", e)
                }
                sendTeamData()
                log["Changed admin status of player: {0}", player.name]
            }
        }
        handler.register("clearbanall", "serverCommands.clearbanall") { _: Array<String>?, _: StrCons ->
            Data.core.admin.bannedIPs.clear()
            Data.core.admin.bannedUUIDs.clear()
        }
        handler.register("ban", "<PlayerSerialNumber>", "serverCommands.ban") { arg: Array<String>, _: StrCons ->
            val site = arg[0].toInt() - 1
            val player = Data.game.playerManage.getPlayerArray(site)
            if (player != null) {
                Events.fire(PlayerBanEvent(player))
            }
        }
        handler.register("mute", "<PlayerSerialNumber> [Time(s)]", "serverCommands.mute") { arg: Array<String>, _: StrCons ->
            val site = arg[0].toInt() - 1
            val player = Data.game.playerManage.getPlayerArray(site)
            if (player != null) {
                //Data.game.playerData[site].muteTime = getLocalTimeFromU(Long.parseLong(arg[1])*1000L);
                player.muteTime = getTimeFutureMillis(43200 * 1000L)
            }
        }
        handler.register("kick", "<PlayerSerialNumber> [time]", "serverCommands.kick") { arg: Array<String>, _: StrCons ->
            val site = arg[0].toInt() - 1
            val player = Data.game.playerManage.getPlayerArray(site)
            if (player != null) {
                player.kickTime = if (arg.size > 1) getTimeFutureMillis(
                    arg[1].toInt() * 1000L
                ) else getTimeFutureMillis(60 * 1000L)
                try {
                    player.kickPlayer(localeUtil.getinput("kick.you"))
                } catch (e: IOException) {
                    error("[Player] Send Kick Player Error", e)
                }
            }
        }
        handler.register("isafk", "<off/on>", "serverCommands.isAfk") { arg: Array<String>, _: StrCons ->
            if (Data.config.OneAdmin) {
                Data.game.isAfk = "on" == arg[0]
            }
        }
        handler.register("maplock", "<off/on>", "serverCommands.maplock") { arg: Array<String>, _: StrCons ->
            Data.game.mapLock = "on" == arg[0]
        }
        handler.register("kill", "<PlayerSerialNumber>", "serverCommands.kill") { arg: Array<String>, log: StrCons ->
            if (Data.game.isStartGame) {
                val site = arg[0].toInt() - 1
                val player = Data.game.playerManage.getPlayerArray(site)
                if (player != null) {
                    player.con!!.sendSurrender()
                }
            } else {
                log[localeUtil.getinput("err.noStartGame")]
            }
        }
        handler.register("giveadmin", "<PlayerSerialNumber...>", "serverCommands.giveadmin") { arg: Array<String>, _: StrCons ->
            Data.game.playerManage.playerGroup.each(
                { p: Player -> p.isAdmin }) { i: Player ->
                val player = Data.game.playerManage.getPlayerArray(arg[0].toInt())
                if (player != null) {
                    i.isAdmin = false
                    player.isAdmin = true
                    upDataGameData()
                    sendMessage(player, localeUtil.getinput("give.ok", player.name))
                }
            }
        }
        handler.register("clearmuteall", "serverCommands.clearmuteall") { _: Array<String>?, _: StrCons ->
            Data.game.playerManage.playerGroup.each { e: Player -> e.muteTime = 0 }
        }
        handler.register("cleanmods", "serverCommands.cleanmods") { _: Array<String>?, _: StrCons ->
            Data.core.unitBase64.clear()
            Data.core.save()
            Main.loadNetCore()
        }
        handler.register("reloadmaps", "serverCommands.reloadmaps") { _: Array<String>?, log: StrCons ->
            val size = Data.game.mapsData.size
            Data.game.mapsData.clear()
            Data.game.checkMaps()
            // Reload 旧列表的Custom数量 : 新列表的Custom数量
            log["Reload Old Size:New Size is {0}:{1}", size, Data.game.mapsData.size]
        }
    }

    private fun registerPlayerStatusCommand(handler: CommandHandler) {
        handler.register("players", "serverCommands.players") { _: Array<String>?, log: StrCons ->
            if (Data.game.playerManage.playerGroup.size() == 0) {
                log["No players are currently in the server."]
            } else {
                log["Players: {0}", Data.game.playerManage.playerGroup.size()]
                val data = StringBuilder()
                for (player in Data.game.playerManage.playerGroup) {
                    data.append(LINE_SEPARATOR)
                        .append(player.name)
                        .append(" / ")
                        .append("ID: ").append(player.uuid)
                        .append(" / ")
                        .append("IP: ").append(player.con!!.ip)
                        .append(" / ")
                        .append("Protocol: ").append(player.con!!.useConnectionAgreement)
                        .append(" / ")
                        .append("Admin: ").append(player.isAdmin)
                }
                log[data.toString()]
            }
        }

        handler.register("admins", "serverCommands.admins") { _: Array<String>?, log: StrCons ->
            if (Data.core.admin.playerAdminData.size == 0) {
                log["No admins are currently in the server."]
            } else {
                log["Admins: {0}", Data.core.admin.playerAdminData.size]
                val data = StringBuilder()
                for (player in Data.core.admin.playerAdminData.values()) {
                    data.append(LINE_SEPARATOR)
                        .append(player.name)
                        .append(" / ")
                        .append("ID: ").append(player.uuid)
                        .append(" / ")
                        .append("Admin: ").append(player.admin)
                        .append(" / ")
                        .append("SuperAdmin: ").append(player.superAdmin)
                }
                log[data.toString()]
            }
        }
    }

    private fun registerPlayerCustomEx(handler: CommandHandler) {
        handler.register("summon", "<unitName> <x> <y> [index(NeutralByDefault)]", "serverCommands.summon") { arg: Array<String>, log: StrCons ->
            if (!Data.game.isStartGame) {
                log[localeUtil.getinput("err.noStartGame")]
                return@register
            }
            val index = if (arg.size > 3) {
                when {
                    IsUtil.isNumericNegative(arg[3]) -> arg[3].toInt()
                    else -> -1
                }
            } else {
                -1
            }
            if (IsUtil.notIsNumeric(arg[1]) || IsUtil.notIsNumeric(arg[2])) {
                log["Not Numeric, Invalid coordinates"]
                return@register
            }
            Data.game.gameCommandCache.offer(NetStaticData.protocolData.abstractNetPacket.gameSummonPacket(index,arg[0],arg[1].toFloat(),arg[2].toFloat()))
        }

        handler.register("changemap", "<MapNumber...>", "serverCommands.changemap") { arg: Array<String>, log: StrCons ->
            if (!Data.game.isStartGame || Data.game.mapLock) {
                log["游戏未开始 & 地图被锁定"]
                return@register
            }
            val response = StringBuilder(arg[0])
            var i = 1
            val lens = arg.size
            while (i < lens) {
                response.append(" ").append(arg[i])
                i++
            }
            val inputMapName = response.toString().replace("'", "").replace(" ", "").replace("-", "").replace("_", "")
            val mapPlayer = Data.MapsMap[inputMapName]
            if (mapPlayer != null) {
                val data = mapPlayer.split("@").toTypedArray()
                Data.game.maps.mapName = data[0]
                Data.game.maps.mapPlayer = data[1]
                Data.game.maps.mapType = GameMaps.MapType.defaultMap
            } else {
                if (Data.game.mapsData.size == 0) {
                    return@register
                }
                if (IsUtil.notIsNumeric(inputMapName)) {
                    log[localeUtil.getinput("err.noNumber")]
                    return@register
                }
                val name = Data.game.mapsData.keys().toSeq()[inputMapName.toInt()]
                val data = Data.game.mapsData[name]
                Data.game.maps.mapData = data
                Data.game.maps.mapType = data.mapType
                Data.game.maps.mapName = name
                Data.game.maps.mapPlayer = ""
            }

            TimeTaskData.stopCallTickTask()
            Data.game.isStartGame = false

            val enc = NetStaticData.protocolData.abstractNetPacket.getTeamDataPacket()

            Data.game.playerManage.playerGroup.each { e: Player ->
                try {
                    e.con!!.sendTeamData(enc)
                    e.con!!.sendStartGame()
                    e.lastMoveTime = Time.concurrentSecond()
                } catch (err: IOException) {
                    Log.error("Start Error", err)
                }
            }
            Data.game.isStartGame = true
            Data.game.playerManage.updateControlIdentifier()
            Call.testPreparationPlayer()
        }
    }

    companion object {
        private val localeUtil = Data.i18NBundle
    }

    init {
        registerPlayerCommand(handler)
        registerPlayerStatusCommand(handler)
        registerPlayerCustomEx(handler)

        PluginManage.runRegisterServerCommands(handler)
    }
}