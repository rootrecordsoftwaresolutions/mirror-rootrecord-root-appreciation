package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Join MOTD reminder when /bonus is available. */
public final class AppreciationJoinListener implements Listener {

    private final RootAppreciationPlugin plugin;

    public AppreciationJoinListener(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.config().enabled() || !plugin.config().motdRemind()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.service().bonusAvailable(player.getUniqueId())) {
                player.sendMessage(plugin.msg("motd-bonus"));
            }
        }, plugin.config().motdDelayTicks());
    }
}
