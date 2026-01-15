package kr.toxicity.healthbar.compatibility

import renaud.btc.api.MythicPlugin
import kr.toxicity.healthbar.api.placeholder.PlaceholderContainer
import kr.toxicity.healthbar.compatibility.btcmobs.BtcMobsMobProvider
import kr.toxicity.healthbar.manager.MobManagerImpl
import kr.toxicity.healthbar.util.placeholder
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import renaud.btc.api.events.BtcMobSpawnEvent
import java.util.function.Function

class BtcMobsCompatibility : Compatibility, Listener {
    override fun accept() {
        MobManagerImpl.addProvider(BtcMobsMobProvider())

        val plugin = Bukkit.getPluginManager().getPlugin("BetterHealthBar") as org.bukkit.plugin.java.JavaPlugin
        Bukkit.getPluginManager().registerEvents(this, plugin)

        PlaceholderContainer.STRING.addPlaceholder("btcmobs", placeholder(1) {
            Function { data ->
                val btcPlugin = Bukkit.getPluginManager().getPlugin("btcMobs") as? MythicPlugin
                btcPlugin?.mobManager?.getActiveMob(data.entity.entity().uniqueId)?.orElse(null)?.let { a ->
                    a.mobType.displayName ?: a.mobType.internalName
                } ?: "<none>"
            }
        })
        PlaceholderContainer.NUMBER.addPlaceholder("btcmobs_level", placeholder(0) {
            Function { data ->
                val btcPlugin = Bukkit.getPluginManager().getPlugin("btcMobs") as? MythicPlugin
                btcPlugin?.mobManager?.getActiveMob(data.entity.entity().uniqueId)?.orElse(null)?.level ?: 0.0
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSpawn(event: BtcMobSpawnEvent) {
        event.activeMob.entity.isCustomNameVisible = false
    }
}
