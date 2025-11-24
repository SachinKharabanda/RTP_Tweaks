package Clustering;

import me.sachin.rTP_Tweaks.RTP_Tweaks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * TeleportListener intercepts Better RTP teleport events and redirects players
 * to appropriate clusters based on the smart clustering algorithm.
 *
 * Better RTP uses async teleports, so we can't rely on stack traces to detect it.
 * Instead, we process all PLUGIN-caused teleports and apply smart filtering.
 */
public class TeleportListener implements Listener {

    private final RTP_Tweaks plugin;
    private final ClusterFinder clusterFinder;
    private final PlayerClusterListener clusterListener;

    // Track which teleports we've already processed to avoid infinite loops
    private final java.util.Set<java.util.UUID> processingTeleports;

    // Minimum distance (in blocks) to consider a teleport as "random" (likely RTP)
    private static final double MIN_RTP_DISTANCE = 100.0;

    public TeleportListener(RTP_Tweaks plugin, ClusterFinder clusterFinder, PlayerClusterListener clusterListener) {
        this.plugin = plugin;
        this.clusterFinder = clusterFinder;
        this.clusterListener = clusterListener;
        this.processingTeleports = java.util.concurrent.ConcurrentHashMap.newKeySet();
    }

    /**
     * Primary handler for Bukkit teleport events
     * Processes PLUGIN-caused teleports that look like RTP (long distance)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Only process PLUGIN-caused teleports
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }

        // Don't process teleports we're already handling (prevent infinite loops)
        if (processingTeleports.contains(player.getUniqueId())) {
            return;
        }

        // Check if this is a long-distance teleport (likely RTP)
        // This helps avoid interfering with other plugins that do short teleports
        if (from.getWorld().equals(to.getWorld())) {
            // Same world - check distance
            double distance = from.distance(to);
            if (distance < MIN_RTP_DISTANCE) {
                // Short distance teleport, probably not RTP
                return;
            }
        }
        // Different world teleports are always processed (likely cross-world RTP)

        String distanceInfo;
        if (from.getWorld().equals(to.getWorld())) {
            distanceInfo = String.format("%.1f blocks", from.distance(to));
        } else {
            distanceInfo = "cross-world (" + from.getWorld().getName() + " → " + to.getWorld().getName() + ")";
        }

        plugin.getLogger().info("Detected RTP teleport for " + player.getName() +
                " (distance: " + distanceInfo + ")");

        processingTeleports.add(player.getUniqueId());

        try {
            // Don't process if player is staying in same cluster
            Cluster currentCluster = clusterListener.getPlayerCluster(player);
            if (currentCluster != null && currentCluster.isInClusterBounds(to)) {
                plugin.getLogger().info("Player " + player.getName() + " staying in same cluster");
                return;
            }

            // Find the best cluster for this player
            Cluster targetCluster = clusterFinder.findBestCluster(to.getWorld());

            if (targetCluster == null) {
                // No cluster needed (no other players), use RTP's default location
                plugin.getLogger().info("Player " + player.getName() +
                        " teleporting to default RTP location (no other players online)");
                return;
            }

            // Generate a random location within the cluster
            Location clusterLocation = targetCluster.getRandomLocationInCluster();

            // Copy pitch and yaw from original location
            clusterLocation.setPitch(to.getPitch());
            clusterLocation.setYaw(to.getYaw());

            // Set the highest block Y at the cluster location
            clusterLocation.setY(to.getWorld().getHighestBlockYAt(clusterLocation) + 1);

            // Modify the teleport destination
            event.setTo(clusterLocation);

            plugin.getLogger().info("✓ Redirected " + player.getName() +
                    " to cluster at " + formatLocation(clusterLocation));

            // Log cluster assignment
            int playerCount = clusterListener.getPlayerCountInCapacityRadius(targetCluster);
            plugin.getLogger().info(String.format("Cluster capacity: %d/%d players",
                    playerCount, Cluster.MAX_CAPACITY));

        } catch (Exception e) {
            plugin.getLogger().severe("Error during cluster assignment for " + player.getName());
            e.printStackTrace();
        } finally {
            // Remove from processing set after a short delay
            final java.util.UUID finalPlayerUUID = player.getUniqueId();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                processingTeleports.remove(finalPlayerUUID);
            }, 20L); // 1 second delay
        }
    }

    /**
     * Format a location for logging
     */
    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        return String.format("%s (%.1f, %.1f, %.1f)",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Register the teleport listener with Bukkit
     * This should be called during plugin initialization
     */
    public void registerBetterRTPListener() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Successfully registered RTP teleport listener");
        plugin.getLogger().info("Clustering system active - will intercept long-distance PLUGIN teleports");
    }

    /**
     * Get the cluster a player is currently assigned to
     *
     * @param player The player to check
     * @return The player's current cluster, or null if not in any cluster
     */
    public Cluster getPlayerCluster(Player player) {
        return clusterListener.getPlayerCluster(player);
    }

    /**
     * Force a player into a specific cluster by teleporting them
     *
     * @param player The player to teleport
     * @param cluster The cluster to teleport to
     */
    public void teleportToCluster(Player player, Cluster cluster) {
        Location clusterLocation = cluster.getRandomLocationInCluster();
        clusterLocation.setY(cluster.getWorld().getHighestBlockYAt(clusterLocation) + 1);

        processingTeleports.add(player.getUniqueId());

        player.teleport(clusterLocation);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            processingTeleports.remove(player.getUniqueId());
        }, 20L);

        plugin.getLogger().info("Manually teleported " + player.getName() +
                " to cluster at " + clusterLocation);
    }

    /**
     * Get statistics about current teleport processing
     *
     * @return A string with current processing statistics
     */
    public String getProcessingStats() {
        return String.format("Currently processing %d teleports", processingTeleports.size());
    }
}