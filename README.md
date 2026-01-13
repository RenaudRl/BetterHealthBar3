# BetterHealthBar

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Folia%20/%20Paper/BTC--CORE%20-blue)

**BetterHealthBar** is a high-performance, strictly optimized fork of **BetterHealthBar3**, engineered specifically for the **BTC Studio** infrastructure. This fork drops support for legacy platforms (Spigot, versions older than 1.21.11) to provide native, blazingly fast integration with **Paper** and **Folia**.

> [!WARNING]
> **PLATFORM COMPATIBILITY NOTICE**
> This fork is **STRICTLY** for Paper 1.21.11+ and Folia 1.21.11+. Legacy compatibility layers have been removed to maximize performance. If you are not running modern Paper/Folia, this plugin **will not function**.

---

## 🚀 Key Features in Detail

### ⚡ Concurrency & Threading (Folia Native)
- **Folia Native Support**: Leverages `FoliaScheduler` for correct Regionized Multithreading support, ensuring health bars update safely and efficiently across parallel regions.
- **Asynchronous Processing**: Heavy lifting like health bar creation and packet construction is handled asynchronously to prevent main-thread stalls.

### 🛠️ Core Optimizations & Debloating
- **Java 21 Native**: Leveraging the latest JVM optimizations for maximum throughput and memory efficiency.
- **Paper/Folia Exclusive**: Removed legacy NMS adapters (1.19.4 - 1.21.9) and Spigot compatibility code. The plugin now interacts directly with the server internals for 1.21.11+.
- **Efficient Sight Tracing**: ray-tracing logic optimized for performance (`look-degree`, `look-distance`) to ensure health bars appear only when relevant without lag.

### 🎨 Visuals & Customization
- **Core Shaders**: Custom shader support to fix z-fighting issues, ensuring health bars render cleanly.
- **Stack System**: Advanced stack system support for buff/debuff visualization.
- **ModelEngine Compatibility**: Native support for ModelEngine with automatic height detection.

---

## ⚙️ Configuration

BetterHealthBar is primarily tuned via `config.yml`.

### Key Settings
| Key | Default | Description |
|-----|---------|-------------|
| `pack-type` | `FOLDER` | How the resource pack is generated (`FOLDER` or `ZIP`). |
| `look-distance` | `15.0` | Maximum distance to render health bars. |
| `look-degree` | `20.0` | Angle field of view for showing health bars. |
| `use-core-shaders` | `true` | Enables custom shaders to fix z-fighting (Recommended). |
| `enable-self-host` | `false` | Enables built-in HTTP server for resource pack hosting. |
| `self-host-port` | `8163` | Port for the self-hosted resource pack server. |
| `show-me-healthbar` | `true` | Toggles visibility of your own health bar. |
| `disable-to-invisible-mob` | `true` | Hides health bars for invisible mobs. |

---

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clean and compile the project
./gradlew clean build
```

---

## 🤝 Credits & Inspiration
This project is built upon the innovation of the broader Minecraft development community:
- **[BetterHealthBar3](https://github.com/toxicity188/BetterHealthBar3)** - The original project by toxicity188.

---

## 📜 License
- **Custom BTC-CORE Patches**: Proprietary to **BTC Studio**.
- **Upstream Source**: Original licenses apply to their respective components from BetterHealthBar3.

---
**Fork maintained by BTCSTUDIO**
