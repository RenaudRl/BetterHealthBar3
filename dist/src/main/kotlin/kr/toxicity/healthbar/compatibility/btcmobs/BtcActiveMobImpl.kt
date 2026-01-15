package kr.toxicity.healthbar.compatibility.btcmobs

import renaud.btc.api.core.mobs.ActiveMob
import kr.toxicity.healthbar.api.mob.HealthBarMob
import kr.toxicity.healthbar.api.mob.MobConfiguration

class BtcActiveMobImpl(
    private val mob: ActiveMob,
    private val configuration: MobConfiguration,
): HealthBarMob {
    override fun id(): String = mob.mobType.internalName
    override fun handle(): Any = mob
    override fun configuration(): MobConfiguration = configuration
}
