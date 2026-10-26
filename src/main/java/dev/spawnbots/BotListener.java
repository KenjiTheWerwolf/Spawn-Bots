package dev.spawnbots;

import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class BotListener implements Listener {
    private final SpawnBotsPlugin plugin;
    private final BotManager manager;
    BotListener(SpawnBotsPlugin plugin, BotManager manager) { this.plugin = plugin; this.manager = manager; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Bot bot = manager.byUuid(event.getPlayer().getUniqueId());
        if (bot == null) return;
        plugin.storage().capture(bot);
        plugin.storage().save();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.remove(bot);
            Bukkit.broadcastMessage("§e" + bot.name + " hat das Spiel verlassen.");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Bot bot = manager.byUuid(event.getPlayer().getUniqueId());
        if (bot != null) manager.forget(bot);
    }
}
