# RTP_Tweaks

**Smart Player Clustering for Better RTP**

RTP_Tweaks is a Paper 1.21.4 plugin that intelligently groups players together when they use `/rtp`, creating a more social and interactive survival experience. Instead of scattering players randomly across the world, this plugin uses a sophisticated clustering algorithm to keep players near each other while preventing overcrowding.

---

## 🌟 Key Features

### Smart Clustering Algorithm
- **Automatic Player Grouping**: When players use `/rtp`, they're intelligently assigned to clusters with other players
- **Capacity Management**: Each cluster has a maximum capacity of 15 players in a 90×90 block region
- **Dynamic Cluster Creation**: Automatically creates new clusters when existing ones reach capacity
- **Zero-Player Zone Preference**: New clusters are created in areas with no players for optimal distribution

### Performance Optimized
- **O(1) Cluster Lookups**: Lightning-fast player-to-cluster mapping using bidirectional hash maps
- **Efficient Movement Tracking**: Only checks cluster membership when players move more than 2 blocks
- **Memory Efficient**: ~10KB memory usage for 100 players across 10 clusters
- **Automatic Cleanup**: Empty clusters are automatically removed every 5 minutes

### Player Experience
- **Seamless Integration**: Works transparently with Better RTP - no player-side changes needed
- **/cinfo Command**: View detailed information about your current cluster and nearby players
- **Cross-World Support**: Works across multiple worlds independently

---

## 📋 Requirements

- **Server**: Paper/Spigot 1.21.4 (or compatible)
- **Dependencies**: [Better RTP](https://www.spigotmc.org/resources/betterrtp-random-wild-teleport.36081/)
- **Java**: Java 17 or higher

---

## 🔧 Compilation Instructions

This plugin uses **Maven** for dependency management and compilation.

### Step 1: Clone the Repository
```bash
git clone https://github.com/yourusername/RTP_Tweaks.git
cd RTP_Tweaks
```

### Step 2: Set Up Dependencies
1. **Create the lib folder** in your project root:
   ```bash
   mkdir -p src/lib
   ```

2. **Download Better RTP**:
   - Visit: https://www.spigotmc.org/resources/betterrtp-random-wild-teleport.36081/
   - Download the latest version (v4.0+)

3. **Place the JAR file** in `src/lib/` and rename it to exactly:
   ```
   BetterRTP.jar
   ```

### Step 3: Compile with Maven
```bash
mvn clean package
```

The compiled plugin will be in `target/RTP_Tweaks-1.0-SNAPSHOT.jar`

### Troubleshooting Compilation
- **Error: "Cannot find BetterRTP.jar"**: Make sure the file is named exactly `BetterRTP.jar` (case-sensitive) and is in `src/lib/`
- **Maven not found**: Install Maven from https://maven.apache.org/download.cgi
- **Java version error**: Ensure you're using Java 17 or higher

---

## 📦 Installation

1. **Install Better RTP** on your server first
2. **Place RTP_Tweaks.jar** in your `plugins/` folder
3. **Restart the server**
4. The plugin will automatically hook into Better RTP

### Verification
Check your console for:
```
[RTP_Tweaks] Successfully hooked into Better RTP v4.x.x
[RTP_Tweaks] Smart clustering is now active!
```

---

## 🎮 How It Works

### The Clustering Algorithm

When a player uses `/rtp`, RTP_Tweaks intercepts the teleportation and applies this logic:

1. **Scan for Players**: Check if other players exist in the target world
   - ✅ **Players found** → Proceed to cluster assignment
   - ❌ **No players** → Use Better RTP's default random location

2. **Find Available Cluster**: Look for clusters with capacity
   - Each cluster is a **80×80 block region** centered on an anchor point
   - Capacity is checked in a **90×90 block radius** (allows for 15 players max)
   - ✅ **Found cluster with space** → Teleport player there
   - ❌ **All clusters full** → Create new cluster

3. **Create New Cluster** (if needed):
   - Search for a location with **0 nearby players** (optimal)
   - If no empty location found after 50 attempts, use location with **minimum players**
   - New cluster must be at least **100 blocks** from existing clusters

4. **Teleport Player**: Random location within the selected cluster's 80×80 region

### Cluster Regions Explained

```
┌─────────────────────────────────────┐
│   Capacity Check Radius (90×90)    │  ← 15 player maximum
│  ┌───────────────────────────────┐  │
│  │  Cluster Region (80×80)       │  │  ← Players spawn here
│  │                               │  │
│  │            ◆ Anchor           │  │
│  │                               │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

---

## 📖 Commands

### `/cinfo`
Displays detailed information about your current cluster.

**Permission**: `clusters.admin` (default: OP)

**Output includes**:
- Your current cluster status (in cluster or not)
- Cluster anchor location and world
- Number of players in cluster region vs capacity region
- Capacity status (available slots or full)
- List of all players in your cluster
- Your distance from cluster anchor

**Example Output**:
```
=== Cluster Information ===

Status: In a cluster

Cluster Details:
  Anchor Location: X: 2543.0, Y: 64.0, Z: -1829.0
  World: world
  Region Size: 80x80 blocks

Capacity:
  Cluster Members: 8 players
  Capacity Region: 12/15 players
  Status: 3 slots available

Players in this cluster:
  • Steve (You)  • Alex
  • Notch        • Herobrine

Your Distance from Anchor: 23.4 blocks
==========================
```

---

## 🎯 Cool Technical Features

### 1. **Bidirectional Mapping System**
Uses concurrent hash maps for O(1) lookups:
- `playerToCluster`: UUID → Cluster (instant player cluster lookup)
- `clusterToPlayers`: Cluster → Set<UUID> (instant cluster population count)

This is **much faster** than scanning all players every time!

### 2. **Smart Movement Detection**
The plugin doesn't spam-check your location every tick:
- Caches your last checked position
- Only recalculates cluster membership if you've moved **2+ blocks**
- Massive performance improvement for servers with many players

### 3. **Async-Safe Teleport Interception**
Better RTP uses async teleports, which most plugins can't detect. RTP_Tweaks:
- Monitors **all PLUGIN-caused teleports**
- Filters for long-distance teleports (100+ blocks)
- Only intercepts actual RTP events, not other plugin teleports
- Uses a processing lock to prevent infinite teleport loops

### 4. **Automatic Memory Management**
Every 5 minutes, the plugin:
- Scans for empty clusters
- Removes clusters with 0 players
- Frees up memory automatically
- Logs cleanup operations

### 5. **Cross-World Intelligence**
The clustering system:
- Maintains separate cluster sets per world
- Handles cross-world teleports correctly
- Never mixes players from different dimensions

---

## 🔍 Use Cases

### Survival Servers
- Encourages player interaction and cooperation
- Creates natural "settlement zones" where communities form
- Players can easily find others to trade with or team up

### RP Servers
- Groups players into towns/villages naturally
- Maintains immersion by preventing extreme isolation
- Creates organic population distribution

### PvP Servers
- Ensures players spawn near others for interaction
- Prevents the "I can never find anyone" problem
- Balances cluster capacity to avoid spawn camping

---

## 🐛 Known Limitations

- **Better RTP Required**: This is a hard dependency - the plugin won't run without it
- **Paper/Spigot Only**: Requires Paper/Spigot API 1.21.4
- **Manual BetterRTP Compilation**: Must manually add BetterRTP.jar to lib folder for compilation

---

## 📊 Configuration

Currently, RTP_Tweaks works with **hardcoded values** optimized for most servers:

| Setting | Value | Description |
|---------|-------|-------------|
| Cluster Size | 80×80 blocks | Region where players spawn |
| Capacity Check | 90×90 blocks | Region used for capacity calculation |
| Max Capacity | 15 players | Maximum players per capacity zone |
| Min Separation | 100 blocks | Minimum distance between clusters |
| Search Radius | 1000 blocks | Search range for new clusters |
| Movement Threshold | 2 blocks | Distance before rechecking cluster |
| Cleanup Interval | 5 minutes | How often empty clusters are removed |

*Future versions may include a config.yml file for customization*

---

## 🤝 Contributing

Contributions are welcome! Areas for improvement:
- Add configuration file support
- Create admin commands for cluster management
- Add cluster visualization (particle effects, dynmap markers)
- Implement cluster "themes" or biome preferences
- Add metrics/statistics tracking

---

## 🙏 Credits

- **Better RTP**: [SuperRonanCraft](https://www.spigotmc.org/resources/betterrtp-random-wild-teleport.36081/)
- **Algorithm Design**: Smart clustering with capacity-based distribution

---

---

**Made with ❤️ for the Minecraft community**

