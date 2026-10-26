/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerPlayer
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 */
package dev.spawnbots;

import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class Bot {
    final String name;
    final String key;
    final ServerPlayer handle;
    final Player player;
    World world;
    final Set<ChunkKey> tickets;
    Action action = Action.NONE;
    int attackCooldown;
    int miningFreeze;
    int eatingTicks;
    BlockPos miningPos;
    float miningProgress;

    Bot(String name, ServerPlayer handle, World world, Set<ChunkKey> tickets) {
        this.name = name;
        this.key = name.toLowerCase(Locale.ROOT);
        this.handle = handle;
        this.player = handle.getBukkitEntity();
        this.world = world;
        this.tickets = tickets;
    }

    static enum Action {
        NONE,
        ATTACK,
        MINE,
        EAT;

    }

    record ChunkKey(int x, int z) {
    }
}

