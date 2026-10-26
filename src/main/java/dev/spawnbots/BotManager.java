package dev.spawnbots;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.profile.PlayerProfile;
import java.util.*;

final class BotManager {
    private static final int RADIUS = 2;
    private final SpawnBotsPlugin plugin;
    private final Map<String, Bot> bots = new HashMap<>();
    private BotActions actions;

    BotManager(SpawnBotsPlugin plugin) { this.plugin = plugin; }
    void setActions(BotActions actions) { this.actions = actions; }

    Bot spawn(PlayerProfile profile, Location location) {
        ServerPlayer fake = null;
        Set<Bot.ChunkKey> tickets = new HashSet<>();
        try {
            fake = FakePlayerFactory.spawn(profile, location);
            addTickets(location.getWorld(), location, tickets);
            Bot bot = new Bot(profile.getName(), fake, location.getWorld(), tickets);
            plugin.storage().restore(bot.name, bot.player.getInventory());
            bots.put(bot.key, bot);
            return bot;
        } catch (RuntimeException | Error exception) {
            releaseTickets(location.getWorld(), tickets);
            if (fake != null) fake.level().getServer().getPlayerList().remove(fake);
            throw exception;
        }
    }

    Bot byName(String name) { return bots.get(name.toLowerCase(Locale.ROOT)); }
    Bot byUuid(UUID uuid) {
        for (Bot bot : bots.values()) if (bot.player.getUniqueId().equals(uuid)) return bot;
        return null;
    }
    Collection<Bot> all() { return bots.values(); }
    long activeActions() { return bots.values().stream().filter(bot -> bot.action != Bot.Action.NONE).count(); }

    void refreshTickets(Bot bot, Location target) {
        releaseTickets(bot.world, bot.tickets);
        bot.world = target.getWorld();
        addTickets(bot.world, target, bot.tickets);
    }

    private void addTickets(World world, Location location, Set<Bot.ChunkKey> tickets) {
        int cx = location.getBlockX() >> 4, cz = location.getBlockZ() >> 4;
        for (int x = cx - RADIUS; x <= cx + RADIUS; x++) for (int z = cz - RADIUS; z <= cz + RADIUS; z++) {
            world.addPluginChunkTicket(x, z, plugin);
            tickets.add(new Bot.ChunkKey(x, z));
        }
    }

    void remove(Bot bot) {
        if (!bots.remove(bot.key, bot)) return;
        actions.stop(bot);
        if (!bot.player.isDead()) {
            plugin.storage().capture(bot);
            plugin.storage().save();
        }
        try { bot.handle.level().getServer().getPlayerList().remove(bot.handle); }
        finally { releaseTickets(bot.world, bot.tickets); }
    }

    void forget(Bot bot) {
        if (!bots.remove(bot.key, bot)) return;
        actions.stop(bot);
        if (!bot.player.isDead()) {
            plugin.storage().capture(bot);
            plugin.storage().save();
        }
        releaseTickets(bot.world, bot.tickets);
    }

    void shutdown() {
        for (Bot bot : new ArrayList<>(bots.values())) remove(bot);
        bots.clear();
        for (World world : Bukkit.getWorlds()) world.removePluginChunkTickets(plugin);
    }

    private void releaseTickets(World world, Set<Bot.ChunkKey> tickets) {
        for (Bot.ChunkKey chunk : tickets) {
            try { world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin); }
            catch (RuntimeException exception) { plugin.recordError("Chunk-Ticket " + chunk + ": " + exception.getMessage()); }
        }
        tickets.clear();
    }
}
