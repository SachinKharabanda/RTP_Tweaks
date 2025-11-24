package Clustering;

import me.sachin.rTP_Tweaks.RTP_Tweaks;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * ClusterFinder handles the logic for finding or creating appropriate clusters
 * for player teleportation according to the smart clustering algorithm:
 *
 * 1. Scan the world for other players
 * 2. If players found, use their positions to create/find clusters
 * 3. Find clusters with available capacity (< 15 in 60x60 region)
 * 4. If no suitable cluster, create new cluster in region with 0 players
 * 5. If no 0-player region exists, find region with minimal players
 */
public class ClusterFinder {

    private final RTP_Tweaks plugin;
    private final PlayerClusterListener clusterListener;

    // Search parameters for finding new cluster locations
    private static final int SEARCH_RADIUS = 1000; // Search within 1000 blocks for new clusters
    private static final int MIN_CLUSTER_SEPARATION = 100; // Minimum distance between cluster anchors
    private static final int MAX_SEARCH_ATTEMPTS = 50; // Maximum attempts to find a suitable location

    private final Random random;

    public ClusterFinder(RTP_Tweaks plugin, PlayerClusterListener clusterListener) {
        this.plugin = plugin;
        this.clusterListener = clusterListener;
        this.random = new Random();
    }

    /**
     * Find the best cluster for a player to teleport to in the specified world
     *
     * Algorithm:
     * 1. Get all players in the target world
     * 2. If no players, return null (use Better RTP's default logic)
     * 3. If players exist, check existing clusters for available capacity
     * 4. If no cluster with capacity, create new cluster with minimal players
     *
     * @param world The world to find a cluster in
     * @return A suitable cluster, or null if no clusters should be used
     */
    public Cluster findBestCluster(World world) {
        // Get all online players in the target world
        List<Player> playersInWorld = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                playersInWorld.add(player);
            }
        }

        // If no players in world, use Better RTP's default logic
        if (playersInWorld.isEmpty()) {
            plugin.getLogger().info("No players found in " + world.getName() + ", using default RTP logic");
            return null;
        }

        plugin.getLogger().info("Found " + playersInWorld.size() + " players in " + world.getName());

        // Get or create clusters for existing players
        Set<Cluster> existingClusters = findClustersInWorld(world);

        // If no clusters exist, create one from first player's location
        if (existingClusters.isEmpty()) {
            Player firstPlayer = playersInWorld.get(0);
            Cluster newCluster = new Cluster(firstPlayer.getLocation());
            clusterListener.registerCluster(newCluster);
            plugin.getLogger().info("Created first cluster at " + firstPlayer.getLocation());
            return newCluster;
        }

        // Find cluster with available capacity
        Cluster availableCluster = findClusterWithCapacity(existingClusters);
        if (availableCluster != null) {
            plugin.getLogger().info("Found cluster with available capacity: " + availableCluster);
            return availableCluster;
        }

        // No cluster with capacity, need to create new cluster
        plugin.getLogger().info("All clusters at capacity, searching for new cluster location");
        return findOrCreateNewCluster(world, existingClusters);
    }

    /**
     * Find all clusters currently active in a world
     * Creates clusters for players not yet in any cluster
     *
     * @param world The world to search
     * @return Set of all clusters in the world
     */
    private Set<Cluster> findClustersInWorld(World world) {
        Set<Cluster> clustersInWorld = new HashSet<>();

        for (Cluster cluster : clusterListener.getActiveClusters()) {
            if (cluster.getWorld().equals(world)) {
                clustersInWorld.add(cluster);
            }
        }

        // Create clusters for players not yet in any cluster
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) continue;

            Cluster playerCluster = clusterListener.getPlayerCluster(player);
            if (playerCluster == null) {
                // Player not in any cluster, create one at their location
                Cluster newCluster = new Cluster(player.getLocation());
                clusterListener.registerCluster(newCluster);
                clustersInWorld.add(newCluster);
                plugin.getLogger().info("Created cluster for untracked player at " + player.getLocation());
            }
        }

        return clustersInWorld;
    }

    /**
     * Find a cluster that has available capacity (< 15 players in 60x60 region)
     *
     * @param clusters Set of clusters to check
     * @return A cluster with available capacity, or null if all are full
     */
    private Cluster findClusterWithCapacity(Set<Cluster> clusters) {
        for (Cluster cluster : clusters) {
            if (!clusterListener.isClusterAtCapacity(cluster)) {
                int currentCapacity = clusterListener.getPlayerCountInCapacityRadius(cluster);
                plugin.getLogger().info("Cluster at " + cluster.getAnchor() +
                        " has " + currentCapacity + "/" + Cluster.MAX_CAPACITY + " capacity");
                return cluster;
            }
        }
        return null;
    }

    /**
     * Find or create a new cluster in a region with minimal players
     *
     * Algorithm:
     * 1. Try to find a location with 0 players
     * 2. If not found after MAX_SEARCH_ATTEMPTS, find location with minimal players
     * 3. Create and register the new cluster
     *
     * @param world The world to create the cluster in
     * @param existingClusters Existing clusters to maintain separation from
     * @return A new cluster with minimal player count
     */
    private Cluster findOrCreateNewCluster(World world, Set<Cluster> existingClusters) {
        Location bestLocation = null;
        int minPlayersFound = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < MAX_SEARCH_ATTEMPTS; attempt++) {
            Location candidateLocation = generateRandomLocation(world, existingClusters);

            // Check if this location is far enough from existing clusters
            if (!isValidClusterLocation(candidateLocation, existingClusters)) {
                continue;
            }

            // Count players in the capacity check radius (60x60)
            int playersInRadius = countPlayersInRadius(candidateLocation, Cluster.CAPACITY_CHECK_RADIUS);

            // If we found a location with 0 players, use it immediately
            if (playersInRadius == 0) {
                plugin.getLogger().info("Found location with 0 players after " + (attempt + 1) + " attempts");
                bestLocation = candidateLocation;
                minPlayersFound = 0;
                break;
            }

            // Track the location with minimum players
            if (playersInRadius < minPlayersFound) {
                minPlayersFound = playersInRadius;
                bestLocation = candidateLocation;
            }
        }

        // Create cluster at best location found
        if (bestLocation == null) {
            // Fallback: use spawn location offset
            bestLocation = world.getSpawnLocation().clone();
            bestLocation.add(random.nextInt(2000) - 1000, 0, random.nextInt(2000) - 1000);
            plugin.getLogger().warning("Could not find suitable cluster location, using fallback");
        }

        Cluster newCluster = new Cluster(bestLocation);
        clusterListener.registerCluster(newCluster);

        plugin.getLogger().info("Created new cluster at " + bestLocation +
                " with " + minPlayersFound + " nearby players");

        return newCluster;
    }

    /**
     * Generate a random location in the world for a potential new cluster
     * Uses world spawn as center point with SEARCH_RADIUS
     *
     * @param world The world to generate location in
     * @param existingClusters Existing clusters to consider for positioning
     * @return A random location
     */
    private Location generateRandomLocation(World world, Set<Cluster> existingClusters) {
        Location center;

        // If there are existing clusters, pick one randomly and offset from it
        if (!existingClusters.isEmpty() && random.nextBoolean()) {
            Cluster randomCluster = existingClusters.iterator().next();
            center = randomCluster.getAnchor().clone();
        } else {
            // Use world spawn as center
            center = world.getSpawnLocation().clone();
        }

        // Generate random offset
        int offsetX = random.nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS;
        int offsetZ = random.nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS;

        Location location = center.clone();
        location.add(offsetX, 0, offsetZ);
        location.setY(world.getHighestBlockYAt(location));

        return location;
    }

    /**
     * Check if a location is valid for a new cluster
     * Must be at least MIN_CLUSTER_SEPARATION blocks from existing clusters
     *
     * @param location The location to check
     * @param existingClusters Existing clusters to check distance from
     * @return true if valid, false otherwise
     */
    private boolean isValidClusterLocation(Location location, Set<Cluster> existingClusters) {
        for (Cluster cluster : existingClusters) {
            double distance = cluster.getDistanceTo(location);
            if (distance < MIN_CLUSTER_SEPARATION) {
                return false;
            }
        }
        return true;
    }

    /**
     * Count the number of players within a radius of a location
     *
     * @param center The center location
     * @param radius The radius to check (in blocks)
     * @return Number of players within the radius
     */
    private int countPlayersInRadius(Location center, int radius) {
        int count = 0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(center.getWorld())) {
                continue;
            }

            Location playerLoc = player.getLocation();
            double deltaX = Math.abs(playerLoc.getX() - center.getX());
            double deltaZ = Math.abs(playerLoc.getZ() - center.getZ());

            if (deltaX <= radius && deltaZ <= radius) {
                count++;
            }
        }

        return count;
    }

    /**
     * Clean up empty clusters to save memory
     * Should be called periodically (e.g., every 5 minutes)
     */
    public void cleanupEmptyClusters() {
        Set<Cluster> toRemove = new HashSet<>();

        for (Cluster cluster : clusterListener.getActiveClusters()) {
            int playerCount = clusterListener.getPlayerCount(cluster);
            if (playerCount == 0) {
                toRemove.add(cluster);
            }
        }

        for (Cluster cluster : toRemove) {
            clusterListener.unregisterCluster(cluster);
            plugin.getLogger().info("Cleaned up empty cluster at " + cluster.getAnchor());
        }

        if (!toRemove.isEmpty()) {
            plugin.getLogger().info("Cleaned up " + toRemove.size() + " empty clusters");
        }
    }

    /**
     * Get statistics about clusters in a world
     *
     * @param world The world to get statistics for
     * @return A formatted string with cluster statistics
     */
    public String getClusterStats(World world) {
        Set<Cluster> clustersInWorld = new HashSet<>();
        int totalPlayers = 0;
        int clustersAtCapacity = 0;

        for (Cluster cluster : clusterListener.getActiveClusters()) {
            if (cluster.getWorld().equals(world)) {
                clustersInWorld.add(cluster);
                int playerCount = clusterListener.getPlayerCountInCapacityRadius(cluster);
                totalPlayers += playerCount;
                if (clusterListener.isClusterAtCapacity(cluster)) {
                    clustersAtCapacity++;
                }
            }
        }

        return String.format(
                "Cluster Stats for %s:\n" +
                        "  Active Clusters: %d\n" +
                        "  Clusters at Capacity: %d\n" +
                        "  Total Players: %d\n" +
                        "  Average Players per Cluster: %.1f",
                world.getName(),
                clustersInWorld.size(),
                clustersAtCapacity,
                totalPlayers,
                clustersInWorld.isEmpty() ? 0 : (double) totalPlayers / clustersInWorld.size()
        );
    }
}