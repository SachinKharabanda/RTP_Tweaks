package me.sachin.rTP_Tweaks;

import Clustering.ClusterFinder;
import Clustering.PlayerClusterListener;
import Clustering.TeleportListener;
import Commands.Commands;
import Hooks.RTPHook;
import org.bukkit.plugin.java.JavaPlugin;

public final class RTP_Tweaks extends JavaPlugin {

    private RTPHook rtpHook;
    private PlayerClusterListener clusterListener;
    private ClusterFinder clusterFinder;
    private TeleportListener teleportListener;
    private Commands commands;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("RTP_Tweaks is starting up...");

        // Initialize and hook into Better RTP
        rtpHook = new RTPHook(this);
        if (!rtpHook.hook()) {
            getLogger().severe("Failed to hook into Better RTP! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize cluster tracking system
        clusterListener = new PlayerClusterListener(this);
        getServer().getPluginManager().registerEvents(clusterListener, this);
        getLogger().info("Player cluster listener registered");

        // Initialize cluster finder
        clusterFinder = new ClusterFinder(this, clusterListener);
        getLogger().info("Cluster finder initialized");

        // Initialize and register teleport listener
        teleportListener = new TeleportListener(this, clusterFinder, clusterListener);
        teleportListener.registerBetterRTPListener();
        getLogger().info("Teleport listener registered");

        // Initialize and register commands
        commands = new Commands(this);
        commands.register(this);
        getLogger().info("Commands registered");

        // Schedule periodic cluster cleanup (every 5 minutes)
        getServer().getScheduler().runTaskTimer(this, () -> {
            clusterFinder.cleanupEmptyClusters();
        }, 6000L, 6000L); // 6000 ticks = 5 minutes

        getLogger().info("RTP_Tweaks has been enabled successfully!");
        getLogger().info("Smart clustering is now active!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

        // Unhook from Better RTP
        if (rtpHook != null && rtpHook.isHooked()) {
            rtpHook.unhook();
        }

        // Clear all cluster data
        if (clusterListener != null) {
            clusterListener.clearClusters();
        }

        getLogger().info("RTP_Tweaks has been disabled!");
    }

    /**
     * Get the RTP Hook instance
     * @return RTPHook instance
     */
    public RTPHook getRTPHook() {
        return rtpHook;
    }

    /**
     * Get the Player Cluster Listener instance
     * @return PlayerClusterListener instance
     */
    public PlayerClusterListener getClusterListener() {
        return clusterListener;
    }

    /**
     * Get the Cluster Finder instance
     * @return ClusterFinder instance
     */
    public ClusterFinder getClusterFinder() {
        return clusterFinder;
    }

    /**
     * Get the Teleport Listener instance
     * @return TeleportListener instance
     */
    public TeleportListener getTeleportListener() {
        return teleportListener;
    }

    /**
     * Get the Commands instance
     * @return Commands instance
     */
    public Commands getCommands() {
        return commands;
    }
}