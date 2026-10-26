/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.craftbukkit.block.CraftBlock
 *  org.bukkit.craftbukkit.entity.CraftItem
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package dev.spawnbots;

import dev.spawnbots.Bot;
import dev.spawnbots.BotManager;
import dev.spawnbots.SpawnBotsPlugin;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class BotActions {
    private final BotManager manager;
    private final BukkitTask task;
    private double averageNanos;
    private long maxNanos;

    BotActions(SpawnBotsPlugin plugin, BotManager manager) {
        this.manager = manager;
        this.task = plugin.getServer().getScheduler().runTaskTimer((Plugin)plugin, this::tick, 1L, 1L);
    }

    void startAttack(Bot bot) {
        this.stop(bot);
        bot.action = Bot.Action.ATTACK;
    }

    void startMining(Bot bot) {
        this.stop(bot);
        bot.action = Bot.Action.MINE;
    }

    boolean startEating(Bot bot) {
        this.stop(bot);
        ServerLevel level = bot.handle.level();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = bot.handle.getItemInHand(hand);
            if (stack.isEmpty() || !bot.handle.gameMode.useItem(bot.handle, (Level)level, stack, hand).consumesAction()) continue;
            if (hand == InteractionHand.MAIN_HAND) {
                bot.player.swingMainHand();
            } else {
                bot.player.swingOffHand();
            }
            bot.eatingTicks = stack.getUseDuration((net.minecraft.world.entity.LivingEntity)bot.handle);
            bot.action = Bot.Action.EAT;
            return true;
        }
        return false;
    }

    void stop(Bot bot) {
        if (bot.miningPos != null) {
            this.abortMining(bot, bot.miningPos);
        }
        bot.miningPos = null;
        bot.miningProgress = 0.0f;
        bot.miningFreeze = 0;
        bot.attackCooldown = 0;
        bot.eatingTicks = 0;
        bot.action = Bot.Action.NONE;
        if (bot.handle.isUsingItem()) {
            bot.handle.releaseUsingItem();
        }
    }

    private void tick() {
        long started = System.nanoTime();
        for (Bot bot : new ArrayList<Bot>(this.manager.all())) {
            if (!bot.player.isOnline() || bot.player.isDead()) continue;
            this.tickPushMovement(bot);
            this.collectNearbyItems(bot);
            switch (bot.action) {
                case ATTACK: {
                    this.tickAttack(bot);
                    break;
                }
                case MINE: {
                    this.tickMining(bot);
                    break;
                }
                case EAT: {
                    this.tickEating(bot);
                }
            }
        }
        long elapsed = System.nanoTime() - started;
        averageNanos = averageNanos == 0 ? elapsed : averageNanos * 0.99 + elapsed * 0.01;
        maxNanos = Math.max(maxNanos, elapsed);
    }

    private void tickPushMovement(Bot bot) {
        var bounds = bot.handle.getBoundingBox();
        for (Entity entity : bot.player.getNearbyEntities(0.8, 1.2, 0.8)) {
            if (!(entity instanceof org.bukkit.entity.LivingEntity) || entity == bot.player || !entity.isValid()) continue;
            var other = ((CraftEntity) entity).getHandle();
            if (!bounds.intersects(other.getBoundingBox())) continue;
            double dx = bot.handle.getX() - other.getX();
            double dz = bot.handle.getZ() - other.getZ();
            if (dx * dx + dz * dz < 0.0001) {
                var direction = entity.getLocation().getDirection();
                dx = direction.getX();
                dz = direction.getZ();
            }
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.0001) { dx = 1; length = 1; }
            double push = 0.16;
            Vec3 movement = new Vec3(dx / length * push, 0, dz / length * push);
            bot.handle.move(MoverType.SELF, movement);
            bot.handle.setDeltaMovement(movement.scale(0.5));
            bounds = bot.handle.getBoundingBox();
        }
    }

    String averageMillis() { return String.format(java.util.Locale.ROOT, "%.3f", averageNanos / 1_000_000.0); }
    String maxMillis() { return String.format(java.util.Locale.ROOT, "%.3f", maxNanos / 1_000_000.0); }
    String tickPercent() { return String.format(java.util.Locale.ROOT, "%.3f", averageNanos / 500_000.0); }

    private void collectNearbyItems(Bot bot) {
        for (Entity entity : bot.player.getNearbyEntities(0.9, 1.5, 0.9)) {
            Item item;
            if (!(entity instanceof Item) || !(item = (Item)entity).isValid()) continue;
            ((CraftItem)item).getHandle().playerTouch((Player)bot.handle);
        }
    }

    private void tickEating(Bot bot) {
        if (!bot.handle.isUsingItem()) {
            bot.action = Bot.Action.NONE;
            return;
        }
        if (--bot.eatingTicks <= 0) {
            bot.handle.completeUsingItem();
            bot.action = Bot.Action.NONE;
        }
    }

    private void tickAttack(Bot bot) {
        if (bot.attackCooldown-- > 0) {
            return;
        }
        bot.attackCooldown = 10;
        Entity target = bot.player.getTargetEntity(4, false);
        if (target instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)target;
            if (target != bot.player && !living.isDead()) {
                bot.player.swingMainHand();
                bot.player.attack((Entity)living);
                bot.handle.resetLastActionTime();
            }
        }
    }

    private void tickMining(Bot bot) {
        if (bot.miningFreeze > 0) {
            --bot.miningFreeze;
            return;
        }
        Block target = bot.player.getTargetBlockExact(5);
        BlockFace face = bot.player.getTargetBlockFace(5);
        if (target == null || face == null || target.isEmpty()) {
            this.stopMiningOnly(bot);
            return;
        }
        BlockPos pos = ((CraftBlock)target).getPosition();
        Direction side = Direction.valueOf((String)face.name());
        ServerLevel level = bot.handle.level();
        if (bot.handle.blockActionRestricted((Level)level, pos, bot.handle.gameMode.getGameModeForPlayer())) {
            return;
        }
        if (bot.miningPos != null && level.getBlockState(bot.miningPos).isAir()) {
            bot.miningPos = null;
            bot.miningProgress = 0.0f;
        }
        BlockState state = level.getBlockState(pos);
        if (bot.handle.gameMode.isCreative()) {
            bot.handle.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, level.getMaxY(), -1);
            bot.miningPos = null;
            bot.miningFreeze = 5;
        } else if (bot.miningPos == null || !bot.miningPos.equals((Object)pos)) {
            if (bot.miningPos != null) {
                this.abortMining(bot, bot.miningPos);
            }
            bot.handle.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, level.getMaxY(), -1);
            if (!state.isAir() && bot.miningProgress == 0.0f) {
                state.attack((Level)level, pos, (Player)bot.handle);
            }
            if (!state.isAir() && state.getDestroyProgress((Player)bot.handle, (BlockGetter)level, pos) >= 1.0f) {
                bot.handle.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, level.getMaxY(), -1);
                bot.miningPos = null;
                bot.miningFreeze = 5;
            } else {
                bot.miningPos = pos;
                bot.miningProgress = 0.0f;
            }
        } else {
            bot.miningProgress += state.getDestroyProgress((Player)bot.handle, (BlockGetter)level, pos);
            if (bot.miningProgress >= 1.0f) {
                bot.handle.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, level.getMaxY(), -1);
                bot.miningPos = null;
                bot.miningProgress = 0.0f;
                bot.miningFreeze = 5;
            }
            level.destroyBlockProgress(bot.handle.getId(), pos, (int)(bot.miningProgress * 10.0f));
        }
        bot.handle.resetLastActionTime();
        bot.player.swingMainHand();
    }

    private void stopMiningOnly(Bot bot) {
        if (bot.miningPos != null) {
            this.abortMining(bot, bot.miningPos);
        }
        bot.miningPos = null;
        bot.miningProgress = 0.0f;
    }

    private void abortMining(Bot bot, BlockPos pos) {
        ServerLevel level = bot.handle.level();
        level.destroyBlockProgress(bot.handle.getId(), pos, -1);
        bot.handle.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.DOWN, level.getMaxY(), -1);
    }
}

