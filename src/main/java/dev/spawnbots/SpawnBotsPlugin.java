package dev.spawnbots;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class SpawnBotsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private final Set<String> pendingProfiles = new HashSet<>();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Deque<String> errors = new ArrayDeque<>();
    private BotManager manager;
    private BotActions actions;
    private BotGui gui;
    private BotStorage storage;

    @Override public void onEnable() {
        storage = new BotStorage(this);
        manager = new BotManager(this);
        actions = new BotActions(this, manager);
        manager.setActions(actions);
        gui = new BotGui(this, manager);
        getCommand("bot").setExecutor(this);
        getCommand("bot").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(new BotListener(this, manager), this);
        Bukkit.getPluginManager().registerEvents(gui, this);
    }

    @Override public void onDisable() {
        pendingProfiles.clear();
        if (manager != null) manager.shutdown();
        if (storage != null) storage.save();
    }

    BotStorage storage() { return storage; }

    void recordError(String message) {
        String stamped = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + message;
        errors.addLast(stamped);
        while (errors.size() > 8) errors.removeFirst();
        getLogger().warning(message);
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl verwenden.");
            return true;
        }
        handleBotCommand(player, label, args);
        return true;
    }

    private boolean allowed(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        player.sendMessage("§cKeine Berechtigung: " + permission);
        return false;
    }

    private void handleBotCommand(Player executor, String label, String[] args) {
        if (args.length == 0) { sendUsage(executor, label); return; }
        if (args[0].equalsIgnoreCase("player")) {
            if (allowed(executor, "spawnbots.spawn")) handleSpawnCommand(executor, label, args);
            return;
        }
        if (args[0].equalsIgnoreCase("equip")) {
            if (!allowed(executor, "spawnbots.equip")) return;
            if (args.length == 1) {
                if (manager.all().isEmpty()) executor.sendMessage("Es sind keine Bots aktiv.");
                else gui.openSelector(executor);
            } else if (args.length == 2) {
                Bot bot = manager.byName(args[1]);
                if (bot == null) executor.sendMessage("Dieser Bot ist nicht aktiv.");
                else gui.openInventory(executor, bot);
            } else sendUsage(executor, label);
            return;
        }
        if (args[0].equalsIgnoreCase("log")) {
            if (args.length == 1 && allowed(executor, "spawnbots.log")) showLog(executor);
            else if (args.length != 1) sendUsage(executor, label);
            return;
        }
        if (args.length < 2) { sendUsage(executor, label); return; }
        Bot bot = manager.byName(args[0]);
        if (bot == null) { executor.sendMessage("Dieser Bot ist nicht aktiv."); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("tp")) {
            if (allowed(executor, "spawnbots.tp")) teleport(executor, bot, args);
            return;
        }
        if (args.length != 2) { sendUsage(executor, label); return; }
        if (!allowed(executor, "spawnbots." + action)) return;
        switch (action) {
            case "attack" -> {
                if (bot.action == Bot.Action.ATTACK) { actions.stop(bot); executor.sendMessage(bot.name + " greift nicht mehr an."); }
                else { actions.startAttack(bot); executor.sendMessage(bot.name + " greift sein Sichtziel an (bis /bot " + bot.name + " stop)."); }
            }
            case "mine" -> {
                if (bot.action == Bot.Action.MINE) { actions.stop(bot); executor.sendMessage(bot.name + " baut nicht mehr ab."); }
                else { actions.startMining(bot); executor.sendMessage(bot.name + " baut seinen anvisierten Block ab."); }
            }
            case "eat" -> {
                if (actions.startEating(bot)) executor.sendMessage(bot.name + " isst.");
                else executor.sendMessage("Keine essbare Nahrung in Haupt-/Nebenhand oder Hunger ist voll.");
            }
            case "stop" -> { actions.stop(bot); executor.sendMessage(bot.name + " wurde gestoppt."); }
            case "undying" -> { bot.player.setInvulnerable(true); executor.sendMessage(bot.name + " ist jetzt unverwundbar."); }
            case "dying" -> { bot.player.setInvulnerable(false); executor.sendMessage(bot.name + " kann jetzt Schaden nehmen und sterben."); }
            default -> sendUsage(executor, label);
        }
    }

    private void teleport(Player executor, Bot bot, String[] args) {
        Location target;
        if (args.length == 2) target = executor.getLocation();
        else if (args.length == 5) {
            try {
                target = new Location(executor.getWorld(), Double.parseDouble(args[2]),
                        Double.parseDouble(args[3]), Double.parseDouble(args[4]),
                        executor.getLocation().getYaw(), executor.getLocation().getPitch());
            } catch (NumberFormatException exception) {
                executor.sendMessage("§cVerwendung: /bot " + bot.name + " tp [x y z]");
                return;
            }
        } else {
            executor.sendMessage("§cVerwendung: /bot " + bot.name + " tp [x y z]");
            return;
        }
        bot.player.teleport(target);
        bot.handle.absSnapTo(target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
        bot.handle.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        manager.refreshTickets(bot, target);
        executor.sendMessage(bot.name + " wurde teleportiert; Chunk-Tickets wurden aktualisiert.");
    }

    private void showLog(Player player) {
        player.sendMessage("§6SpawnBots Diagnose");
        player.sendMessage("§7Aktive Bots: §f" + manager.all().size() + " §7| Aktionen: §f" + manager.activeActions()
                + " §7| Chunk-Tickets: §f" + (manager.all().size() * 25));
        player.sendMessage("§7Bot-Task: Ø §f" + actions.averageMillis() + " ms §7| Maximum: §f"
                + actions.maxMillis() + " ms §7| Anteil am 50-ms-Tick: §f" + actions.tickPercent() + "%");
        player.sendMessage("§7Gespeicherte Inventare: §f" + storage.savedCount());
        if (errors.isEmpty()) player.sendMessage("§aKeine Fehler protokolliert.");
        else {
            player.sendMessage("§cLetzte Fehler:");
            for (String error : errors) player.sendMessage("§7- §f" + error);
        }
    }

    private void handleSpawnCommand(Player executor, String label, String[] args) {
        if (args.length != 2) { executor.sendMessage("Verwendung: /" + label + " player <Name>"); return; }
        String name = args[1], key = name.toLowerCase(Locale.ROOT);
        Bot existing = manager.byName(name);
        if (existing != null) {
            manager.remove(existing);
            executor.sendMessage("Bot " + existing.name + " entfernt; Inventar gespeichert und Chunks freigegeben.");
            return;
        }
        if (pendingProfiles.contains(key)) { executor.sendMessage("Das Skin-Profil für " + name + " wird bereits geladen."); return; }
        if (Bukkit.getPlayerExact(name) != null) { executor.sendMessage("Ein echter Spieler mit diesem Namen ist online."); return; }
        try { Bukkit.createProfile(name); }
        catch (IllegalArgumentException exception) { executor.sendMessage("Ungültiger Spielername: " + exception.getMessage()); return; }
        pendingProfiles.add(key);
        Location location = executor.getLocation();
        executor.sendMessage("Lade Skin-Profil von " + name + " …");
        loadMojangProfile(name).orTimeout(20, TimeUnit.SECONDS).whenComplete((profile, error) -> {
            if (!isEnabled()) return;
            Bukkit.getScheduler().runTask(this, () -> finishSpawn(executor, key, name, location, profile, error));
        });
    }

    private CompletableFuture<PlayerProfile> loadMojangProfile(String requestedName) {
        HttpRequest identity = HttpRequest.newBuilder(URI.create(
                "https://api.mojang.com/users/profiles/minecraft/" + requestedName))
                .timeout(Duration.ofSeconds(15)).GET().build();
        return fetchJson(identity).thenCompose(idResponse -> {
            if (!idResponse.has("id") || !idResponse.has("name")) throw new IllegalArgumentException("Mojang-Spieler nicht gefunden");
            String id = idResponse.get("id").getAsString(), name = idResponse.get("name").getAsString();
            HttpRequest skin = HttpRequest.newBuilder(URI.create(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            return fetchJson(skin).thenApply(session -> {
                JsonObject textures = null;
                if (session.has("properties")) for (var element : session.getAsJsonArray("properties")) {
                    JsonObject property = element.getAsJsonObject();
                    if (property.get("name").getAsString().equals("textures")) { textures = property; break; }
                }
                if (textures == null) throw new IllegalStateException("Mojang lieferte keine Skin-Texturen");
                UUID mojangId = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                PlayerProfile profile = Bukkit.createProfileExact(mojangId, name);
                String signature = textures.has("signature") && !textures.get("signature").isJsonNull()
                        ? textures.get("signature").getAsString() : null;
                ((com.destroystokyo.paper.profile.PlayerProfile) profile).setProperty(
                        new ProfileProperty("textures", textures.get("value").getAsString(), signature));
                return profile;
            });
        });
    }

    private CompletableFuture<JsonObject> fetchJson(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("Mojang HTTP " + response.statusCode());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }

    private void finishSpawn(Player executor, String key, String requestedName, Location location,
                             PlayerProfile profile, Throwable error) {
        pendingProfiles.remove(key);
        if (error != null || profile == null || !profile.isComplete() || profile.getUniqueId() == null || profile.getName() == null) {
            executor.sendMessage("Skin-Profil für " + requestedName + " konnte nicht geladen werden.");
            recordError("Profil " + requestedName + ": " + (error == null ? "unvollständiges Profil" : error.getMessage()));
            return;
        }
        if (Bukkit.getPlayerExact(profile.getName()) != null) { executor.sendMessage("Ein echter Spieler mit diesem Namen ist inzwischen online."); return; }
        try {
            UUID botUuid = UUID.nameUUIDFromBytes(("SpawnBots:" + key).getBytes(StandardCharsets.UTF_8));
            PlayerProfile botProfile = Bukkit.createProfileExact(botUuid, profile.getName());
            botProfile.setTextures(profile.getTextures());
            ((com.destroystokyo.paper.profile.PlayerProfile) botProfile).setProperties(
                    ((com.destroystokyo.paper.profile.PlayerProfile) profile).getProperties());
            Bot bot = manager.spawn(botProfile, location);
            executor.sendMessage("Bot " + bot.name + (storage.has(bot.key) ? " mit gespeichertem Inventar" : "")
                    + " mit Skin und 5x5 Chunks gespawnt.");
        } catch (RuntimeException | Error exception) {
            recordError("Bot " + requestedName + " konnte nicht gespawnt werden: " + exception);
            executor.sendMessage("Bot konnte nicht gespawnt werden; siehe /bot log.");
        }
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage("§6SpawnBots Befehle:");
        if (player.hasPermission("spawnbots.spawn")) player.sendMessage("§f/" + label + " player <Name>");
        if (player.hasPermission("spawnbots.equip")) player.sendMessage("§f/" + label + " equip [Name]");
        if (player.hasPermission("spawnbots.log")) player.sendMessage("§f/" + label + " log");
        player.sendMessage("§f/" + label + " <Name> <attack|mine|eat|stop|undying|dying|tp>");
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("spawnbots.spawn")) result.add("player");
            if (sender.hasPermission("spawnbots.equip")) result.add("equip");
            if (sender.hasPermission("spawnbots.log")) result.add("log");
            if (hasAnyActionPermission(sender)) for (Bot bot : manager.all()) result.add(bot.name);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            if (sender.hasPermission("spawnbots.equip")) for (Bot bot : manager.all()) result.add(bot.name);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            if (sender.hasPermission("spawnbots.spawn")) for (Player player : Bukkit.getOnlinePlayers()) result.add(player.getName());
        } else if (args.length >= 2 && manager.byName(args[0]) != null) {
            if (args.length == 2) {
                for (String action : List.of("attack", "mine", "eat", "stop", "undying", "dying", "tp")) {
                    if (sender.hasPermission("spawnbots." + action)) result.add(action);
                }
            } else if (args[1].equalsIgnoreCase("tp") && args.length >= 3 && args.length <= 5
                    && sender.hasPermission("spawnbots.tp") && sender instanceof Player player) {
                Location l = player.getLocation();
                result.add(switch (args.length) {
                    case 3 -> formatCoordinate(l.getX());
                    case 4 -> formatCoordinate(l.getY());
                    default -> formatCoordinate(l.getZ());
                });
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return result.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static boolean hasAnyActionPermission(CommandSender sender) {
        for (String action : List.of("attack", "mine", "eat", "stop", "undying", "dying", "tp")) {
            if (sender.hasPermission("spawnbots." + action)) return true;
        }
        return false;
    }

    private static String formatCoordinate(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }
}
