package Commands;

import Clustering.Cluster;
import Clustering.ClusterFinder;
import Clustering.PlayerClusterListener;
import me.sachin.rTP_Tweaks.RTP_Tweaks;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Command handler for cluster-related commands
 * Provides information about clusters and player assignments
 */
public class Commands implements CommandExecutor, TabCompleter {

    private final RTP_Tweaks plugin;
    private final PlayerClusterListener clusterListener;
    private final ClusterFinder clusterFinder;

    public Commands(RTP_Tweaks plugin) {
        this.plugin = plugin;
        this.clusterListener = plugin.getClusterListener();
        this.clusterFinder = plugin.getClusterFinder();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if command is /cinfo
        if (command.getName().equalsIgnoreCase("cinfo")) {
            return handleClusterInfo(sender);
        }

        return false;
    }

    /**
     * Handle the /cinfo command
     * Shows the player's current cluster and all players in it
     *
     * @param sender The command sender
     * @return true if command was handled successfully
     */
    private boolean handleClusterInfo(CommandSender sender) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("clusters.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            player.sendMessage(ChatColor.RED + "Required permission: " + ChatColor.YELLOW + "clusters.admin");
            return true;
        }

        // Get player's current cluster
        Cluster playerCluster = clusterListener.getPlayerCluster(player);

        // Send header
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Cluster Information ===");
        player.sendMessage("");

        // If player is not in a cluster
        if (playerCluster == null) {
            player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.RED + "Not in any cluster");
            player.sendMessage(ChatColor.GRAY + "You are currently outside of all active cluster regions.");
            player.sendMessage("");

            // Show overall cluster statistics
            int totalClusters = clusterListener.getActiveClusters().size();
            player.sendMessage(ChatColor.AQUA + "Total Active Clusters: " + ChatColor.WHITE + totalClusters);

            if (totalClusters > 0) {
                player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/rtp " +
                        ChatColor.GRAY + "to be assigned to a cluster!");
            }

            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "==========================");
            return true;
        }

        // Player is in a cluster - show detailed information
        player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.GREEN + "In a cluster");
        player.sendMessage("");

        // Cluster location information
        player.sendMessage(ChatColor.AQUA + "Cluster Details:");
        player.sendMessage(ChatColor.GRAY + "  Anchor Location: " + ChatColor.WHITE +
                String.format("X: %.1f, Y: %.1f, Z: %.1f",
                        playerCluster.getAnchor().getX(),
                        playerCluster.getAnchor().getY(),
                        playerCluster.getAnchor().getZ()));
        player.sendMessage(ChatColor.GRAY + "  World: " + ChatColor.WHITE + playerCluster.getWorld().getName());
        player.sendMessage(ChatColor.GRAY + "  Region Size: " + ChatColor.WHITE +
                (Cluster.CLUSTER_RADIUS * 2) + "x" + (Cluster.CLUSTER_RADIUS * 2) + " blocks");
        player.sendMessage("");

        // Get players in this cluster
        Set<UUID> playersInCluster = clusterListener.getPlayersInCluster(playerCluster);
        int playerCount = playersInCluster.size();
        int capacityCount = clusterListener.getPlayerCountInCapacityRadius(playerCluster);

        // Capacity information
        boolean atCapacity = clusterListener.isClusterAtCapacity(playerCluster);
        ChatColor capacityColor = atCapacity ? ChatColor.RED :
                (capacityCount >= Cluster.MAX_CAPACITY * 0.75 ? ChatColor.YELLOW : ChatColor.GREEN);

        player.sendMessage(ChatColor.AQUA + "Capacity:");
        player.sendMessage(ChatColor.GRAY + "  Cluster Members: " + ChatColor.WHITE + playerCount + " players");
        player.sendMessage(ChatColor.GRAY + "  Capacity Region: " + capacityColor + capacityCount +
                ChatColor.WHITE + "/" + Cluster.MAX_CAPACITY + " players");

        if (atCapacity) {
            player.sendMessage(ChatColor.RED + "  Status: FULL - New players will be assigned elsewhere");
        } else {
            int remaining = Cluster.MAX_CAPACITY - capacityCount;
            player.sendMessage(ChatColor.GREEN + "  Status: " + remaining + " slots available");
        }

        player.sendMessage("");

        // List all players in the cluster
        player.sendMessage(ChatColor.AQUA + "Players in this cluster:");

        if (playerCount == 0) {
            player.sendMessage(ChatColor.GRAY + "  No players currently in this cluster region");
            player.sendMessage(ChatColor.GRAY + "  (Note: You may be in the capacity check radius)");
        } else {
            List<String> playerNames = new ArrayList<>();

            for (UUID playerId : playersInCluster) {
                Player clusterPlayer = plugin.getServer().getPlayer(playerId);
                if (clusterPlayer != null && clusterPlayer.isOnline()) {
                    String playerName = clusterPlayer.getName();

                    // Highlight the current player
                    if (clusterPlayer.equals(player)) {
                        playerNames.add(ChatColor.GREEN + playerName + " (You)" + ChatColor.WHITE);
                    } else {
                        playerNames.add(ChatColor.WHITE + playerName);
                    }
                }
            }

            // Display player names (2 per line for readability)
            for (int i = 0; i < playerNames.size(); i++) {
                if (i % 2 == 0) {
                    if (i + 1 < playerNames.size()) {
                        player.sendMessage(ChatColor.GRAY + "  • " + playerNames.get(i) +
                                ChatColor.GRAY + "  • " + playerNames.get(i + 1));
                    } else {
                        player.sendMessage(ChatColor.GRAY + "  • " + playerNames.get(i));
                    }
                }
            }
        }

        player.sendMessage("");

        // Distance from anchor
        double distance = player.getLocation().distance(playerCluster.getAnchor());
        player.sendMessage(ChatColor.AQUA + "Your Distance from Anchor: " +
                ChatColor.WHITE + String.format("%.1f blocks", distance));

        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "==========================");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion needed for /cinfo (no arguments)
        return new ArrayList<>();
    }

    /**
     * Register this command handler with the plugin
     *
     * @param plugin The plugin instance
     */
    public void register(RTP_Tweaks plugin) {
        plugin.getCommand("cinfo").setExecutor(this);
        plugin.getCommand("cinfo").setTabCompleter(this);
        plugin.getLogger().info("Registered /cinfo command");
    }
}