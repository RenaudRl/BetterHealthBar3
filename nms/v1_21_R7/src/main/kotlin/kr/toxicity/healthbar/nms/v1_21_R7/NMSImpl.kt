package kr.toxicity.healthbar.nms.v1_21_R7

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup
import com.mojang.math.Transformation
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise

import io.papermc.paper.entity.LookAnchor
import kr.toxicity.healthbar.api.BetterHealthBar
import kr.toxicity.healthbar.api.nms.NMS
import kr.toxicity.healthbar.api.nms.PacketBundler
import kr.toxicity.healthbar.api.nms.VirtualTextDisplay
import kr.toxicity.healthbar.api.player.HealthBarPlayer
import kr.toxicity.healthbar.api.trigger.HealthBarTriggerType
import kr.toxicity.healthbar.api.trigger.PacketTrigger
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.pointer.Pointers
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerCommonPacketListenerImpl
import net.minecraft.util.Brightness
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.level.entity.LevelEntityGetter
import net.minecraft.world.level.entity.LevelEntityGetterAdapter
import net.minecraft.world.level.entity.PersistentEntitySectionManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.WorldBorder
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer
import org.bukkit.craftbukkit.util.CraftChatMessage
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.PlayerInventory
import org.bukkit.permissions.Permission
import org.bukkit.util.Vector
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

@Suppress("UNUSED")
class NMSImpl : NMS {
    private val plugin
        get() = BetterHealthBar.inst()

    private val connectionField = ServerCommonPacketListenerImpl::class.java.declaredFields.first {
        it.type.simpleName == "Connection" || it.type.simpleName == "NetworkManager"
    }.apply {
        isAccessible = true
    }
    private val channelField by lazy {
        connectionField.type.declaredFields.first {
            io.netty.channel.Channel::class.java.isAssignableFrom(it.type)
        }.apply {
            isAccessible = true
        }
    }

    private val getConnection: (ServerCommonPacketListenerImpl) -> Any = {
        connectionField[it]
    }

    private val getEntityById: (LevelEntityGetter<net.minecraft.world.entity.Entity>, Int) -> net.minecraft.world.entity.Entity? = EntityLookup::class.java.declaredFields.first {
        ConcurrentLong2ReferenceChainedHashTable::class.java.isAssignableFrom(it.type)
    }.let {
        it.isAccessible = true
        { e, i ->
            (it[e] as ConcurrentLong2ReferenceChainedHashTable<*>)[i.toLong()] as? net.minecraft.world.entity.Entity
        }
    }
    private fun getClass(vararg names: String): Class<*> {
        for (name in names) {
            try {
                return Class.forName(name)
            } catch (_: ClassNotFoundException) {}
        }
        throw ClassNotFoundException("Could not find any of: ${names.joinToString()}")
    }

    private val chatComponentClass = getClass("net.minecraft.network.chat.Component", "net.minecraft.network.chat.IChatBaseComponent")
    private val moveEntityClass = getClass("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket", "net.minecraft.network.protocol.game.PacketPlayOutEntity")
    private val damageEventPacketClass = getClass("net.minecraft.network.protocol.game.ClientboundDamageEventPacket", "net.minecraft.network.protocol.game.PacketPlayOutDamageEvent")
    private val addEntityPacketClass = getClass("net.minecraft.network.protocol.game.ClientboundAddEntityPacket", "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity")
    private val setEntityDataPacketClass = getClass("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket", "net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata")
    private val entityPositionSyncPacketClass = getClass("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket", "net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport")
    private val removeEntitiesPacketClass = getClass("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket", "net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy")
    private val movePlayerPacketClass = getClass("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket", "net.minecraft.network.protocol.game.PacketPlayInFlying")


    private val getEntityFromMovePacket: (Any) -> Int = moveEntityClass.declaredFields.first {
        Integer.TYPE.isAssignableFrom(it.type)
    }.let {
        it.isAccessible = true
        { p ->
            it[p] as Int
        }
    }
    private val paperAdventureClass = try {
        Class.forName("io.papermc.paper.adventure.PaperAdventure")
    } catch (_: Exception) {
         null
    }
    private val asVanillaMethod = paperAdventureClass?.getMethod("asVanilla", net.kyori.adventure.text.Component::class.java)

    private val textVanilla: (net.kyori.adventure.text.Component) -> Any = {
        asVanillaMethod?.invoke(null, it) ?: throw RuntimeException("PaperAdventure or asVanilla not found")
    }

    private fun getEntityIdFromPacket(packet: Any): Int {
        try {
            return packet.javaClass.getMethod("entityId").invoke(packet) as Int
        } catch (_: Exception) {}
        try {
            return packet.javaClass.getField("entityId").getInt(packet)
        } catch (_: Exception) {}
        try {
            return packet.javaClass.getField("a").getInt(packet)
        } catch (_: Exception) {}
        try {
            // iterate int fields, maybe first one is entity id? Risky but common.
             packet.javaClass.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType }?.let {
                 it.isAccessible = true
                 return it.getInt(packet)
             }
        } catch (_: Exception) {}
        return 0
    }

    override fun foliaAdapt(player: Player): Player {
        player as CraftPlayer
        fun vanillaPlayer() = player.handleRaw as ServerPlayer
        return object : CraftPlayer(Bukkit.getServer() as CraftServer, vanillaPlayer()) {
            override fun getPersistentDataContainer(): CraftPersistentDataContainer {
                return player.persistentDataContainer
            }
            override fun getHandle(): ServerPlayer {
                return vanillaPlayer()
            }
            override fun getHealth(): Double {
                return player.health
            }
            override fun getScaledHealth(): Float {
                return player.scaledHealth
            }
            override fun getFirstPlayed(): Long {
                return player.firstPlayed
            }
            override fun getInventory(): PlayerInventory {
                return player.inventory
            }
            override fun getEnderChest(): Inventory {
                return player.enderChest
            }

            override fun getCooldown(key: Key): Int {
                return player.getCooldown(key)
            }

            override fun setCooldown(key: Key, i: Int) {
                return player.setCooldown(key, i)
            }

            override fun isOp(): Boolean {
                return player.isOp
            }
            override fun getGameMode(): GameMode {
                return player.gameMode
            }
            override fun getEquipment(): EntityEquipment {
                return player.equipment
            }
            override fun hasPermission(name: String): Boolean {
                return player.hasPermission(name)
            }
            override fun hasPermission(perm: Permission): Boolean {
                return player.hasPermission(perm)
            }
            override fun isPermissionSet(name: String): Boolean {
                return player.isPermissionSet(name)
            }
            override fun isPermissionSet(perm: Permission): Boolean {
                return player.isPermissionSet(perm)
            }
            override fun hasPlayedBefore(): Boolean {
                return player.hasPlayedBefore()
            }
            override fun getWorldBorder(): WorldBorder? {
                return player.worldBorder
            }
            override fun showBossBar(bar: BossBar) {
                player.showBossBar(bar)
            }
            override fun hideBossBar(bar: BossBar) {
                player.hideBossBar(bar)
            }
            override fun sendMessage(message: String) {
                player.sendMessage(message)
            }
            override fun getLastDamageCause(): EntityDamageEvent? {
                return player.lastDamageCause
            }
            override fun pointers(): Pointers {
                return player.pointers()
            }
            override fun spigot(): Player.Spigot {
                return player.spigot()
            }
        }
    }

    override fun foliaAdapt(entity: org.bukkit.entity.LivingEntity): org.bukkit.entity.LivingEntity {
        entity as CraftLivingEntity
        fun vanillaEntity() = entity.handleRaw as LivingEntity
        return object : CraftLivingEntity(Bukkit.getServer() as CraftServer, vanillaEntity()) {
            override fun getPersistentDataContainer(): CraftPersistentDataContainer {
                return entity.persistentDataContainer
            }
            override fun getHandle(): LivingEntity {
                return vanillaEntity()
            }
            override fun lookAt(p0: Double, p1: Double, p2: Double, p3: LookAnchor) {
                return entity.lookAt(p0, p1, p2, p3)
            }
            override fun getEquipment(): EntityEquipment {
                return entity.equipment
            }
            override fun hasPermission(name: String): Boolean {
                return entity.hasPermission(name)
            }
            override fun hasPermission(perm: Permission): Boolean {
                return entity.hasPermission(perm)
            }
            override fun isPermissionSet(name: String): Boolean {
                return entity.isPermissionSet(name)
            }
            override fun isPermissionSet(perm: Permission): Boolean {
                return entity.isPermissionSet(perm)
            }
            override fun showBossBar(bar: BossBar) {
                entity.showBossBar(bar)
            }
            override fun hideBossBar(bar: BossBar) {
                entity.hideBossBar(bar)
            }
            override fun sendMessage(message: String) {
                entity.sendMessage(message)
            }
            override fun getLastDamageCause(): EntityDamageEvent? {
                return entity.lastDamageCause
            }
            override fun pointers(): Pointers {
                return entity.pointers()
            }

            override fun spigot(): Entity.Spigot {
                return entity.spigot()
            }
        }
    }

    private fun net.minecraft.world.entity.Entity.moveTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) = snapTo(x, y, z, yaw, pitch)

    override fun createBundler(): PacketBundler = bundlerOf()
    @Suppress("UNCHECKED_CAST")
    override fun createTextDisplay(location: Location, component: net.kyori.adventure.text.Component): VirtualTextDisplay {
        val display = TextDisplay(EntityType.TEXT_DISPLAY, (location.world as CraftWorld).handle).apply {
            billboardConstraints = Display.BillboardConstraints.CENTER
            entityData.run {
                set(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, 3)
                set(TextDisplay.DATA_BACKGROUND_COLOR_ID, 0)
                set(TextDisplay.DATA_LINE_WIDTH_ID, Int.MAX_VALUE)
            }
            brightnessOverride = Brightness(15, 15)
            try {
                this.javaClass.getMethod("b", chatComponentClass).invoke(this, textVanilla(component))
            } catch (_: Exception) {
                this.javaClass.getMethod("setText", chatComponentClass).invoke(this, textVanilla(component))
            }
            viewRange = 10F
            moveTo(
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch
            )
        }
        return object : VirtualTextDisplay {
            override fun spawn(bundler: PacketBundler) {
                display.run {
                    try {
                        bundler += addEntityPacketClass.getConstructor(net.minecraft.world.entity.Entity::class.java).newInstance(this) as Packet<ClientGamePacketListener>
                    } catch (_: Exception) {
                        try {
                             bundler += addEntityPacketClass.getConstructor(net.minecraft.world.entity.Entity::class.java, Int::class.javaPrimitiveType).newInstance(this, 0) as Packet<ClientGamePacketListener>
                        } catch (e: Exception) {
                            try {
                                val vec3Class = getClass("net.minecraft.world.phys.Vec3")
                                val vec3 = vec3Class.getConstructor(Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType).newInstance(0.0, 0.0, 0.0)
                                bundler += addEntityPacketClass.getConstructor(
                                    Int::class.javaPrimitiveType,
                                    UUID::class.java,
                                    Double::class.javaPrimitiveType,
                                    Double::class.javaPrimitiveType,
                                    Double::class.javaPrimitiveType,
                                    Float::class.javaPrimitiveType,
                                    Float::class.javaPrimitiveType,
                                    EntityType::class.java,
                                    Int::class.javaPrimitiveType,
                                    vec3Class,
                                    Double::class.javaPrimitiveType
                                ).newInstance(
                                    id,
                                    uuid,
                                    x,
                                    y,
                                    z,
                                    xRot,
                                    yRot,
                                    type,
                                    0,
                                    vec3,
                                    yRot
                                ) as Packet<ClientGamePacketListener>
                            } catch (e2: Exception) {
                                e2.printStackTrace()
                            }
                        }
                    }
                    try {
                        bundler += setEntityDataPacketClass.getConstructor(Int::class.javaPrimitiveType, List::class.java).newInstance(id, entityData.nonDefaultValues!!) as Packet<ClientGamePacketListener>
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun shadowRadius(radius: Float) {
                display.shadowRadius = radius
            }
            override fun shadowStrength(strength: Float) {
                display.shadowStrength = strength
            }
            override fun update(bundler: PacketBundler) {
                try {
                    bundler += entityPositionSyncPacketClass.getConstructor(Int::class.javaPrimitiveType, PositionMoveRotation::class.java, Boolean::class.javaPrimitiveType)
                        .newInstance(display.id, PositionMoveRotation.of(display), display.onGround) as Packet<ClientGamePacketListener>
                } catch (_: Exception) {
                    // Fallback or ignore if packet doesn't exist
                }
                display.entityData.packDirty()?.let {
                    try {
                        bundler += setEntityDataPacketClass.getConstructor(Int::class.javaPrimitiveType, List::class.java).newInstance(display.id, it) as Packet<ClientGamePacketListener>
                    } catch (e: Exception) {
                         e.printStackTrace()
                    }
                }
            }
            override fun teleport(location: Location) {
                display.moveTo(
                    location.x,
                    location.y,
                    location.z,
                    location.yaw,
                    location.pitch
                )
            }

            override fun text(component: net.kyori.adventure.text.Component) {
                val vanilla = textVanilla(component.compact())
                try {
                    display.javaClass.getMethod("b", chatComponentClass).invoke(display, vanilla)
                } catch (_: Exception) {
                    display.javaClass.getMethod("setText", chatComponentClass).invoke(display, vanilla)
                }
            }

            override fun transformation(location: Vector, scale: Vector) {
                fun Vector.toVanilla() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
                display.setTransformation(Transformation(location.toVanilla(), null, scale.toVanilla(), null))
            }

            override fun remove(bundler: PacketBundler) {
                try {
                     // Varargs constructor usually?
                     bundler += removeEntitiesPacketClass.getConstructor(IntArray::class.java).newInstance(intArrayOf(display.id)) as Packet<ClientGamePacketListener>
                } catch (_: Exception) {
                    try {
                        // List?
                        bundler += removeEntitiesPacketClass.getConstructor(List::class.java).newInstance(listOf(display.id)) as Packet<ClientGamePacketListener>
                    } catch (e: Exception) {
                        // Single int?
                        try {
                            bundler += removeEntitiesPacketClass.getConstructor(Int::class.javaPrimitiveType).newInstance(display.id) as Packet<ClientGamePacketListener>
                        } catch (e2: Exception) {
                             e2.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private val injectionMap = ConcurrentHashMap<UUID, PlayerInjection>()

    override fun inject(player: HealthBarPlayer) {
        injectionMap.computeIfAbsent(player.player().uniqueId) {
            PlayerInjection(player)
        }
    }

    override fun uninject(player: HealthBarPlayer) {
        injectionMap.remove(player.player().uniqueId)?.uninject()
    }

    private inner class PlayerInjection(val player: HealthBarPlayer) : ChannelDuplexHandler() {
        private val serverPlayer = (player.player() as CraftPlayer).handle
        private val world = player.player().world
        private val connection = serverPlayer.connection
        private val foliaAdapted = foliaAdapt(player.player())
        private val taskQueue = ConcurrentLinkedQueue<() -> Unit>()
        private val task = BetterHealthBar.inst().scheduler().asyncTaskTimer(1, 1) {
            var task: (() -> Unit)?
            do {
                task = taskQueue.poll()?.also { 
                    it()
                }
            } while (task != null)
        }

        init {
            val channel = channelField[getConnection(connection)] as io.netty.channel.Channel
            val pipeLine = channel.pipeline()
            pipeLine.toMap().forEach {
                if (it.value::class.java.simpleName == "Connection" || it.value::class.java.simpleName == "NetworkManager") pipeLine.addBefore(it.key, BetterHealthBar.NAMESPACE, this)
            }
        }

        fun uninject() {
            task.cancel()
            val channel = channelField[getConnection(connection)] as io.netty.channel.Channel
            channel.eventLoop().submit {
                channel.pipeline().remove(BetterHealthBar.NAMESPACE)
            }
        }

        private fun Double.square() = this * this
        private fun show(handle: Any, trigger: HealthBarTriggerType, entity: net.minecraft.world.entity.Entity?) {
            if (entity is LivingEntity && !entity.isDeadOrDying && entity.removalReason == null) taskQueue.add task@ {
                val bukkit = entity.bukkitEntity as org.bukkit.entity.LivingEntity
                if (sqrt((serverPlayer.x - entity.x).square()  + (serverPlayer.y - entity.y).square() + (serverPlayer.z - entity.z).square()) > plugin.configManager().lookDistance()) return@task
                val set = plugin.healthBarManager().allHealthBars().filter {
                    it.triggers().contains(trigger)
                }.toSet()
                val adapt = plugin.mobManager().entity(
                    if (bukkit is Player) injectionMap[bukkit.uniqueId]?.foliaAdapted ?: return@task else foliaAdapt(bukkit)
                )
                val types = adapt.mob()?.configuration()?.types()
                val packet = PacketTrigger(trigger, handle)
                set.filter {
                    (adapt.mob()?.configuration()?.ignoreDefault() != true && it.isDefault) || (types != null && it.applicableTypes().any { t ->
                        types.contains(t)
                    })
                }.forEach {
                    player.showHealthBar(it, packet, adapt)
                }
                adapt.mob()?.configuration()?.healthBars()?.forEach {
                    player.showHealthBar(it, packet, adapt)
                }
            }
        }

        private fun getViewedEntity(): List<LivingEntity> {
            return getLevelGetter().all
                .asSequence()
                .mapNotNull { 
                    it as? LivingEntity
                }
                .filter { 
                    it !== serverPlayer && it.canSee()
                }
                .toList()
        }

        private fun net.minecraft.world.entity.Entity.canSee(): Boolean {
            val playerYaw = Math.toRadians(serverPlayer.yRot.toDouble())
            val playerPitch = Math.toRadians(-serverPlayer.xRot.toDouble())

            val degree = plugin.configManager().lookDegree()

            val x = this.z - serverPlayer.z
            val y = this.y - serverPlayer.y
            val z = -(this.x - serverPlayer.x)

            val dy = abs(atan2(y, abs(cos(playerYaw) * x - sin(playerYaw) * z)) - playerPitch)
            val dz = abs(atan2(z, x) - playerYaw)
            return (dy <= degree || dy >= 2 * PI - degree) && (dz <= degree || dz >= 2 * PI - degree)
        }

        @Suppress("UNCHECKED_CAST")
        private fun getLevelGetter(): LevelEntityGetter<net.minecraft.world.entity.Entity> {
            return serverPlayer.level().`moonrise$getEntityLookup`()
        }

        private fun Int.toEntity() = getEntityById(getLevelGetter(), this)

        override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
            if (msg != null) {
                if (moveEntityClass.isInstance(msg)) {
                    show(msg, HealthBarTriggerType.MOVE, getEntityFromMovePacket(msg).toEntity())
                } else if (damageEventPacketClass.isInstance(msg)) {
                     show(msg, HealthBarTriggerType.DAMAGE, getEntityIdFromPacket(msg).toEntity())
                }
            }
            super.write(ctx, msg, promise)
        }

        override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
            if (msg != null && movePlayerPacketClass.isInstance(msg)) {
                taskQueue.add {
                    getViewedEntity().forEach {
                        show(msg, HealthBarTriggerType.LOOK, it)
                    }
                }
            }
            super.channelRead(ctx, msg)
        }
    }
}