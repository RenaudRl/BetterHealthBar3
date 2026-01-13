package kr.toxicity.healthbar.version

import org.bukkit.Bukkit

data class MinecraftVersion(
    val first: Int,
    val second: Int,
    val third: Int,
) : Comparable<MinecraftVersion> {
    companion object {
        private val comparator = Comparator.comparing { v: MinecraftVersion ->
            v.first
        }.thenComparing { v: MinecraftVersion ->
            v.second
        }.thenComparing { v: MinecraftVersion ->
            v.third
        }

        val current = MinecraftVersion(Bukkit.getBukkitVersion()
            .substringBefore('-'))

        val version1_21_11 = MinecraftVersion(1, 21, 11)

        private val packVersion = mapOf(
            version1_21_11 to 75
        )
    }

    constructor(string: String): this(string.split('.'))
    constructor(string: List<String>): this(
        if (string.isNotEmpty()) string[0].toInt() else 0,
        if (string.size > 1) string[1].toInt() else 0,
        if (string.size > 2) string[2].toInt() else 0
    )
    override fun compareTo(other: MinecraftVersion): Int {
        return comparator.compare(this, other)
    }

    fun packVersion() = packVersion[this] ?: 7

    override fun toString(): String {
        return if (third != 0) "$first.$second.$third" else "$first.$second"
    }
}