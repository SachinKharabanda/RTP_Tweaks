package Clustering;

import me.sachin.rTP_Tweaks.RTP_Tweaks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efficiently tracks which players are in which clusters using bidirectional mapping
 *
 * Time Complexity Analysis:
 * - Player join/quit: O(1)
 * - Player move check: O(c) where c = number of active clusters (typically small)
 * - Get cluster player count: O(1)
 * - Check if cluster at capacity: O(1)
 *
 * Memory Complexity Analysis:
 * - O(p + c) where p = number of online players and c = number of active clusters
 * - Each player: 1 UUID + 1 Location reference + 1 Cluster reference ≈ 64 bytes
 * - Each cluster: 1 HashSet<UUID> ≈ 16 bytes + (16 bytes per player in cluster)
 * - Total for 100 players in 10 clusters: ~10 KB
 */
public class PlayerClusterListener implements Listener {

    private final RTP_Tweaks plugin;

    // Bidirectional mappings for O(1) lookups
    // Maps player UUID to their current cluster (if any)
    private final Map<UUID, Cluster> playerToCluster;

    // Maps cluster to set of player UUIDs in that cluster
    private final Map<Cluster, Set<UUID>> clusterToPlayers;

    // Cache last checked location for each player to avoid unnecessary recalculations
    private final Map<UUID, Location> lastCheckedLocation;

    // List of active clusters to check against
    private final Set<Cluster> activeClusters;

    // Movement threshold - only check cluster membership if player moved this many blocks
    private static final double MOVEMENT_THRESHOLD = 2.0;

    // Capacity check radius (60x60 region = ±30 blocks)
    private static final int CAPACITY_RADIUS = 30;

    public PlayerClusterListener(RTP_Tweaks plugin) {
        this.plugin = plugin;
        this.playerToCluster = new ConcurrentHashMap<>();
        this.clusterToPlayers = new ConcurrentHashMap<>();
        this.lastCheckedLocation = new ConcurrentHashMap<>();
        this.activeClusters = ConcurrentHashMap.newKeySet();
    }

    /**
     * Register a cluster to be tracked
     * @param cluster The cluster to track
     */
    public void registerCluster(Cluster cluster) {
        activeClusters.add(cluster);
        clusterToPlayers.putIfAbsent(cluster, ConcurrentHashMap.newKeySet());
    }

    /**
     * Unregister a cluster from tracking
     * @param cluster The cluster to stop tracking
     */
    public void unregisterCluster(Cluster cluster) {
        activeClusters.remove(cluster);

        // Remove players from this cluster
        Set<UUID> playersInCluster = clusterToPlayers.remove(cluster);
        if (playersInCluster != null) {
            for (UUID playerId : playersInCluster) {
                if (playerToCluster.get(playerId) == cluster) {
                    playerToCluster.remove(playerId);
                }
            }
        }
    }

    /**
     * Clear all tracked clusters
     */
    public void clearClusters() {
        activeClusters.clear();
        clusterToPlayers.clear();
        playerToCluster.clear();
    }

    /**
     * Get the number of players in a specific cluster
     * Time Complexity: O(1)
     * @param cluster The cluster to check
     * @return Number of players in the cluster
     */
    public int getPlayerCount(Cluster cluster) {
        Set<UUID> players = clusterToPlayers.get(cluster);
        return players != null ? players.size() : 0;
    }

    /**
     * Get the number of players within the capacity check radius (60x60) of a cluster
     * This is more accurate than just checking cluster membership for capacity checks
     * Time Complexity: O(p) where p = number of players in and around the cluster
     * @param cluster The cluster to check
     * @return Number of players within capacity check radius
     */
    public int getPlayerCountInCapacityRadius(Cluster cluster) {
        int count = 0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(cluster.getWorld())) {
                Location playerLoc = player.getLocation();
                double deltaX = Math.abs(playerLoc.getX() - cluster.getAnchor().getX());
                double deltaZ = Math.abs(playerLoc.getZ() - cluster.getAnchor().getZ());

                if (deltaX <= CAPACITY_RADIUS && deltaZ <= CAPACITY_RADIUS) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Check if a cluster is at or above maximum capacity (60x60 region check)
     * Time Complexity: O(p) where p = number of online players
     * @param cluster The cluster to check
     * @return true if at or above capacity, false otherwise
     */
    public boolean isClusterAtCapacity(Cluster cluster) {
        return getPlayerCountInCapacityRadius(cluster) >= Cluster.MAX_CAPACITY;
    }

    /**
     * Get all players currently in a cluster
     * Time Complexity: O(n) where n = number of players in cluster
     * @param cluster The cluster to check
     * @return Set of player UUIDs in the cluster
     */
    public Set<UUID> getPlayersInCluster(Cluster cluster) {
        Set<UUID> players = clusterToPlayers.get(cluster);
        return players != null ? new HashSet<>(players) : Collections.emptySet();
    }

    /**
     * Get the cluster a player is currently in
     * Time Complexity: O(1)
     * @param player The player to check
     * @return The cluster the player is in, or null if not in any cluster
     */
    public Cluster getPlayerCluster(Player player) {
        return playerToCluster.get(player.getUniqueId());
    }

    /**
     * Get all active clusters being tracked
     * @return Set of active clusters
     */
    public Set<Cluster> getActiveClusters() {
        return new HashSet<>(activeClusters);
    }

    /**
     * Update a player's cluster membership based on their current location
     * Time Complexity: O(c) where c = number of active clusters
     * @param player The player to update
     */
    private void updatePlayerCluster(Player player) {
        UUID playerId = player.getUniqueId();
        Location currentLoc = player.getLocation();
        Cluster currentCluster = playerToCluster.get(playerId);

        // Find which cluster (if any) the player is now in
        Cluster newCluster = null;
        for (Cluster cluster : activeClusters) {
            if (cluster.isInClusterBounds(currentLoc)) {
                newCluster = cluster;
                break;
            }
        }

        // If cluster hasn't changed, no update needed
        if (currentCluster == newCluster) {
            return;
        }

        // Remove from old cluster
        if (currentCluster != null) {
            Set<UUID> playersInOldCluster = clusterToPlayers.get(currentCluster);
            if (playersInOldCluster != null) {
                playersInOldCluster.remove(playerId);
            }
        }

        // Add to new cluster
        if (newCluster != null) {
            playerToCluster.put(playerId, newCluster);
            clusterToPlayers.computeIfAbsent(newCluster, k -> ConcurrentHashMap.newKeySet()).add(playerId);
        } else {
            playerToCluster.remove(playerId);
        }

        // Update last checked location
        lastCheckedLocation.put(playerId, currentLoc.clone());
    }

    /**
     * Handle player movement - check if they've moved enough to warrant cluster update
     * Time Complexity: O(1) if movement below threshold, O(c) if above threshold
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location to = event.getTo();

        if (to == null) return;

        // Check if player has moved significantly since last check
        Location lastLoc = lastCheckedLocation.get(playerId);
        if (lastLoc != null && lastLoc.getWorld().equals(to.getWorld())) {
            double distanceSquared = lastLoc.distanceSquared(to);
            if (distanceSquared < MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD) {
                return; // Not moved enough to warrant a check
            }
        }

        // Update cluster membership
        updatePlayerCluster(player);
    }

    /**
     * Handle player teleportation - always update cluster membership
     * Time Complexity: O(c) where c = number of active clusters
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Always update on teleport since position changed significantly
        updatePlayerCluster(event.getPlayer());
    }

    /**
     * Handle player joining - add them to tracking
     * Time Complexity: O(c) where c = number of active clusters
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerCluster(event.getPlayer());
    }

    /**
     * Handle player quitting - remove them from tracking
     * Time Complexity: O(1)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Remove from cluster
        Cluster cluster = playerToCluster.remove(playerId);
        if (cluster != null) {
            Set<UUID> playersInCluster = clusterToPlayers.get(cluster);
            if (playersInCluster != null) {
                playersInCluster.remove(playerId);
            }
        }

        // Remove cached location
        lastCheckedLocation.remove(playerId);
    }

    /**
     * Force an immediate update of all online players' cluster memberships
     * Useful after registering new clusters
     * Time Complexity: O(p * c) where p = players, c = clusters
     */
    public void refreshAllPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerCluster(player);
        }
    }

    /**
     * Get memory usage statistics
     * @return String with memory usage information
     */
    public String getMemoryStats() {
        int totalPlayers = playerToCluster.size();
        int totalClusters = activeClusters.size();
        int totalMappings = clusterToPlayers.values().stream()
                .mapToInt(Set::size)
                .sum();

        // Rough memory calculation
        long playerMapMemory = totalPlayers * 64; // UUID + reference + location
        long clusterMapMemory = totalClusters * 16; // HashSet overhead
        long mappingMemory = totalMappings * 16; // UUID references in sets
        long totalBytes = playerMapMemory + clusterMapMemory + mappingMemory;

        return String.format(
                "PlayerClusterListener Memory Stats:\n" +
                        "  Tracked Players: %d\n" +
                        "  Active Clusters: %d\n" +
                        "  Total Mappings: %d\n" +
                        "  Estimated Memory: %.2f KB",
                totalPlayers, totalClusters, totalMappings, totalBytes / 1024.0
        );
    }
}