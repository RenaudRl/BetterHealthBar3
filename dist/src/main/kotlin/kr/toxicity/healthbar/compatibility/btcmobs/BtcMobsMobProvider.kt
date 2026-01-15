package kr.toxicity.healthbar.compatibility.btcmobs

import renaud.btc.api.MythicPlugin
import kr.toxicity.healthbar.api.mob.HealthBarMob
import kr.toxicity.healthbar.api.mob.MobProvider
import kr.toxicity.healthbar.manager.MobManagerImpl
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity

class BtcMobsMobProvider : MobProvider {
    override fun provide(entity: LivingEntity): HealthBarMob? {
        val plugin = Bukkit.getPluginManager().getPlugin("btcMobs") as? MythicPlugin ?: return null
        return plugin.mobManager.getActiveMob(entity.uniqueId).orElse(null)?.let {
            BtcActiveMobImpl(it, MobManagerImpl.configuration(it.mobType.internalName) ?: return null)
        }
    }
}
