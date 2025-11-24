package Clustering;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Random;

/**
 * Represents a cluster region for player grouping in RTP
 * A cluster is a 80x80 region centered on an anchor point
 * Maximum capacity is 15 players within a 90x90 check radius
 */
public class Cluster {

    private final Location anchor;
    private final World world;
    private final Random random;

    // Cluster region dimensions (50x50)
    public static final int CLUSTER_RADIUS = 40; // ±40 blocks from anchor

    // Capacity check dimensions (60x60)
    public static final int CAPACITY_CHECK_RADIUS = 45; // ±45 blocks from anchor

    // Maximum players allowed in the capacity check region
    public static final int MAX_CAPACITY = 15;

    /**
     * Create a new cluster with the specified anchor location
     * @param anchor The center point of the cluster
     */
    public Cluster(Location anchor) {
        this.anchor = anchor.clone();
        this.world = anchor.getWorld();
        this.random = new Random();
    }

    /**
     * Get the anchor (center) location of this cluster
     * @return The anchor location
     */
    public Location getAnchor() {
        return anchor.clone();
    }

    /**
     * Get the world this cluster is in
     * @return The world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Check if this cluster is at maximum capacity
     * Checks if there are 15 or more players within a 60x60 region from the anchor
     *
     * Note: When a PlayerClusterListener is available, use isAtCapacity(PlayerClusterListener)
     * for better performance (O(1) vs O(n))
     *
     * @param players Collection of all online players to check
     * @return true if the cluster is at or above maximum capacity, false otherwise
     */
    public boolean isAtCapacity(Collection<? extends Player> players) {
        return getPlayerCountInRadius(players, CAPACITY_CHECK_RADIUS) >= MAX_CAPACITY;
    }

    /**
     * Check if this cluster is at maximum capacity using the listener for efficient lookup
     * Time Complexity: O(p) where p = online players (must check capacity radius)
     * Recommended over isAtCapacity(Collection) when listener is available
     *
     * @param listener The PlayerClusterListener tracking cluster memberships
     * @return true if the cluster is at or above maximum capacity, false otherwise
     */
    public boolean isAtCapacity(PlayerClusterListener listener) {
        return listener.isClusterAtCapacity(this);
    }

    /**
     * Get the number of players within the cluster region (50x50)
     *
     * Note: When a PlayerClusterListener is available, use getPlayerCount(PlayerClusterListener)
     * for better performance (O(1) vs O(n))
     *
     * @param players Collection of all online players to check
     * @return The count of players in the cluster region
     */
    public int getPlayerCount(Collection<? extends Player> players) {
        return getPlayerCountInRadius(players, CLUSTER_RADIUS);
    }

    /**
     * Get the number of players within the cluster region using the listener for efficient lookup
     * Time Complexity: O(1)
     * Recommended over getPlayerCount(Collection) when listener is available
     *
     * @param listener The PlayerClusterListener tracking cluster memberships
     * @return The count of players in the cluster region
     */
    public int getPlayerCount(PlayerClusterListener listener) {
        return listener.getPlayerCount(this);
    }

    /**
     * Get the number of players within a specific radius from the anchor
     * @param players Collection of all online players to check
     * @param radius The radius to check (in blocks)
     * @return The count of players within the specified radius
     */
    public int getPlayerCountInRadius(Collection<? extends Player> players, int radius) {
        int count = 0;

        for (Player player : players) {
            if (player.getWorld().equals(world)) {
                Location playerLoc = player.getLocation();

                // Check if player is within the radius (square region check for performance)
                double deltaX = Math.abs(playerLoc.getX() - anchor.getX());
                double deltaZ = Math.abs(playerLoc.getZ() - anchor.getZ());

                if (deltaX <= radius && deltaZ <= radius) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Check if a location is within the cluster bounds (50x50 region)
     * @param location The location to check
     * @return true if the location is within cluster bounds, false otherwise
     */
    public boolean isInClusterBounds(Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }

        double deltaX = Math.abs(location.getX() - anchor.getX());
        double deltaZ = Math.abs(location.getZ() - anchor.getZ());

        return deltaX <= CLUSTER_RADIUS && deltaZ <= CLUSTER_RADIUS;
    }

    /**
     * Get a random location within the cluster bounds
     * This returns coordinates that Better RTP can use as center/bounds for its teleportation logic
     * @return A location within the cluster region
     */
    public Location getRandomLocationInCluster() {
        // Generate random offsets within the cluster radius
        double offsetX = (random.nextDouble() * 2 - 1) * CLUSTER_RADIUS; // Random between -25 and +25
        double offsetZ = (random.nextDouble() * 2 - 1) * CLUSTER_RADIUS; // Random between -25 and +25

        Location randomLoc = anchor.clone();
        randomLoc.add(offsetX, 0, offsetZ);

        return randomLoc;
    }

    /**
     * Get the minimum X coordinate of the cluster
     * @return Minimum X coordinate
     */
    public double getMinX() {
        return anchor.getX() - CLUSTER_RADIUS;
    }

    /**
     * Get the maximum X coordinate of the cluster
     * @return Maximum X coordinate
     */
    public double getMaxX() {
        return anchor.getX() + CLUSTER_RADIUS;
    }

    /**
     * Get the minimum Z coordinate of the cluster
     * @return Minimum Z coordinate
     */
    public double getMinZ() {
        return anchor.getZ() - CLUSTER_RADIUS;
    }

    /**
     * Get the maximum Z coordinate of the cluster
     * @return Maximum Z coordinate
     */
    public double getMaxZ() {
        return anchor.getZ() + CLUSTER_RADIUS;
    }

    /**
     * Check if a player is within the cluster bounds
     * @param player The player to check
     * @return true if player is within cluster bounds, false otherwise
     */
    public boolean containsPlayer(Player player) {
        return isInClusterBounds(player.getLocation());
    }

    /**
     * Get a string representation of this cluster
     * @return String representation with anchor coordinates
     */
    @Override
    public String toString() {
        return String.format("Cluster[anchor=(%.1f, %.1f, %.1f), world=%s, region=%dx%d]",
                anchor.getX(), anchor.getY(), anchor.getZ(),
                world.getName(),
                CLUSTER_RADIUS * 2, CLUSTER_RADIUS * 2);
    }

    /**
     * Calculate the distance between this cluster's anchor and a location
     * Uses 2D distance (X and Z only, ignoring Y)
     * @param location The location to measure distance to
     * @return The 2D distance in blocks
     */
    public double getDistanceTo(Location location) {
        if (!location.getWorld().equals(world)) {
            return Double.MAX_VALUE;
        }

        double deltaX = location.getX() - anchor.getX();
        double deltaZ = location.getZ() - anchor.getZ();

        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }
}