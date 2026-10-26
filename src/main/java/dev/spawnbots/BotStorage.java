package dev.spawnbots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;

final class BotStorage {
    private final SpawnBotsPlugin plugin;
    private final File file;
    private final Map<String, ItemStack[]> inventories = new HashMap<>();

    BotStorage(SpawnBotsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "inventories.yml");
        load();
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            ItemStack[] contents = new ItemStack[41];
            for (int slot = 0; slot < contents.length; slot++) contents[slot] = section.getItemStack(String.valueOf(slot));
            inventories.put(key.toLowerCase(Locale.ROOT), contents);
        }
    }

    void capture(Bot bot) {
        ItemStack[] source = bot.player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) copy[slot] = source[slot] == null ? null : source[slot].clone();
        inventories.put(bot.key, copy);
    }

    void restore(String name, org.bukkit.inventory.PlayerInventory inventory) {
        ItemStack[] saved = inventories.get(name.toLowerCase(Locale.ROOT));
        if (saved == null) return;
        ItemStack[] copy = new ItemStack[saved.length];
        for (int slot = 0; slot < saved.length; slot++) copy[slot] = saved[slot] == null ? null : saved[slot].clone();
        inventory.setContents(copy);
    }

    boolean has(String key) { return inventories.containsKey(key.toLowerCase(Locale.ROOT)); }
    int savedCount() { return inventories.size(); }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, ItemStack[]> entry : inventories.entrySet()) {
            ItemStack[] contents = entry.getValue();
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] != null && !contents[slot].getType().isAir()) {
                    yaml.set(entry.getKey() + "." + slot, contents[slot]);
                }
            }
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) throw new IOException("Datenordner konnte nicht erstellt werden");
            yaml.save(file);
        } catch (IOException exception) {
            plugin.recordError("Inventare konnten nicht gespeichert werden: " + exception.getMessage());
        }
    }
}
