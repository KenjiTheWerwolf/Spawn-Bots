package dev.spawnbots;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.*;

final class BotGui implements Listener {
    private static final int SIZE = 54;
    private final SpawnBotsPlugin plugin;
    private final BotManager manager;
    BotGui(SpawnBotsPlugin plugin, BotManager manager) { this.plugin = plugin; this.manager = manager; }

    void openSelector(Player viewer) {
        SelectionHolder holder = new SelectionHolder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, "§8SpawnBots auswählen");
        List<Bot> bots = manager.all().stream().sorted(Comparator.comparing(bot -> bot.key)).toList();
        int slot = 0;
        for (Bot bot : bots) {
            if (slot >= SIZE) break;
            holder.bots[slot] = bot;
            holder.inventory.setItem(slot++, head(bot));
        }
        viewer.openInventory(holder.inventory);
    }

    void openInventory(Player viewer, Bot bot) { viewer.openInventory(new EquipmentHolder(bot).inventory); }

    private ItemStack head(Bot bot) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(bot.player.getPlayerProfile());
        meta.setDisplayName("§e" + bot.name);
        meta.setLore(List.of(bot.player.isInvulnerable() ? "§7Undying" : "§7Dying",
                "§7Aktion: §f" + bot.action.name().toLowerCase(Locale.ROOT), "§8Klicken zum Ausrüsten"));
        head.setItemMeta(meta);
        return head;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        InventoryHolder type = event.getInventory().getHolder();
        if (type instanceof SelectionHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player viewer)) return;
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= SIZE || holder.inventory != event.getInventory()) return;
            Bot bot = holder.bots[slot];
            if (bot != null && manager.byUuid(bot.player.getUniqueId()) == bot) {
                Bukkit.getScheduler().runTask(plugin, () -> openInventory(viewer, bot));
            }
            return;
        }
        if (!(type instanceof EquipmentHolder holder)) return;
        int raw = event.getRawSlot();
        if (event.getClick() == ClickType.DOUBLE_CLICK) event.setCancelled(true);
        if (raw >= 0 && raw < SIZE && !holder.slots.containsKey(raw)) event.setCancelled(true);
        if (raw >= SIZE && event.isShiftClick()) event.setCancelled(true);
        if (!event.isCancelled()) Bukkit.getScheduler().runTask(plugin, holder::syncToBot);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof EquipmentHolder holder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < SIZE && !holder.slots.containsKey(slot)) { event.setCancelled(true); return; }
        }
        Bukkit.getScheduler().runTask(plugin, holder::syncToBot);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EquipmentHolder holder) holder.syncToBot();
    }

    private static ItemStack label(Material material, String name, String description) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(description));
        item.setItemMeta(meta);
        return item;
    }

    private static final class SelectionHolder implements InventoryHolder {
        private final Bot[] bots = new Bot[SIZE];
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class EquipmentHolder implements InventoryHolder {
        private final Bot bot;
        private final Map<Integer, Integer> slots = new HashMap<>();
        private final Inventory inventory;

        private EquipmentHolder(Bot bot) {
            this.bot = bot;
            this.inventory = Bukkit.createInventory(this, SIZE, "§8" + bot.name + " – Equipment");
            for (int slot = 0; slot < SIZE; slot++) {
                inventory.setItem(slot, label(Material.GRAY_STAINED_GLASS_PANE, " ", "§8SpawnBots"));
            }
            int held = bot.player.getInventory().getHeldItemSlot();
            slots.put(0, held);
            slots.put(2, 40);
            slots.put(9, 39);
            slots.put(11, 38);
            slots.put(13, 37);
            slots.put(15, 36);
            inventory.setItem(1, label(Material.IRON_SWORD, "§eHaupthand", "§7Linkes Feld: Waffe, Werkzeug oder Nahrung"));
            inventory.setItem(3, label(Material.SHIELD, "§eNebenhand", "§7Linkes Feld: Schild, Nahrung oder anderes Item"));
            inventory.setItem(5, label(Material.NETHERITE_CHESTPLATE, "§eRüstung", "§7Die vier beschrifteten Felder darunter"));
            inventory.setItem(10, label(Material.IRON_HELMET, "§eHelm", "§7Linkes Feld"));
            inventory.setItem(12, label(Material.IRON_CHESTPLATE, "§eBrustplatte", "§7Linkes Feld"));
            inventory.setItem(14, label(Material.IRON_LEGGINGS, "§eHose", "§7Linkes Feld"));
            inventory.setItem(16, label(Material.IRON_BOOTS, "§eStiefel", "§7Linkes Feld"));
            for (int hotbar = 0; hotbar < 9; hotbar++) {
                int guiSlot = 18 + hotbar;
                if (hotbar == held) {
                    inventory.setItem(guiSlot, label(Material.LIME_STAINED_GLASS_PANE, "§aAktive Schnellzugriffs-Leiste",
                            "§7Dieses Item wird oben bei der Haupthand bearbeitet"));
                } else {
                    slots.put(guiSlot, hotbar);
                }
            }
            for (int botSlot = 9; botSlot <= 35; botSlot++) slots.put(27 + botSlot - 9, botSlot);
            for (Map.Entry<Integer, Integer> entry : slots.entrySet()) {
                inventory.setItem(entry.getKey(), bot.player.getInventory().getItem(entry.getValue()));
            }
        }

        private void syncToBot() {
            for (Map.Entry<Integer, Integer> entry : slots.entrySet()) {
                bot.player.getInventory().setItem(entry.getValue(), inventory.getItem(entry.getKey()));
            }
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
