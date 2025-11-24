package Hooks;

import me.sachin.rTP_Tweaks.RTP_Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Hook class for Better RTP integration
 * Handles connection to Better RTP plugin and provides API access
 */
public class RTPHook {

    private final RTP_Tweaks plugin;
    private Plugin betterRTP;
    private boolean hooked;

    public RTPHook(RTP_Tweaks plugin) {
        this.plugin = plugin;
        this.hooked = false;
    }

    /**
     * Initialize the hook connection to Better RTP
     * @return true if successfully hooked, false otherwise
     */
    public boolean hook() {
        Plugin betterRTPPlugin = Bukkit.getPluginManager().getPlugin("BetterRTP");

        if (betterRTPPlugin == null) {
            plugin.getLogger().severe("Better RTP plugin not found! RTP_Tweaks requires Better RTP to function.");
            return false;
        }

        if (!betterRTPPlugin.isEnabled()) {
            plugin.getLogger().severe("Better RTP is installed but not enabled!");
            return false;
        }

        this.betterRTP = betterRTPPlugin;
        this.hooked = true;

        plugin.getLogger().info("Successfully hooked into Better RTP v" + betterRTP.getDescription().getVersion());
        return true;
    }

    /**
     * Check if the hook is successfully connected
     * @return true if hooked, false otherwise
     */
    public boolean isHooked() {
        return hooked;
    }

    /**
     * Get the Better RTP plugin instance
     * @return Better RTP plugin instance or null if not hooked
     */
    public Plugin getBetterRTP() {
        return betterRTP;
    }

    /**
     * Unhook from Better RTP (cleanup)
     */
    public void unhook() {
        this.hooked = false;
        this.betterRTP = null;
        plugin.getLogger().info("Unhooked from Better RTP");
    }
}