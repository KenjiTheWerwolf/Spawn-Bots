/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.destroystokyo.paper.profile.CraftPlayerProfile
 *  com.destroystokyo.paper.profile.PlayerProfile
 *  com.mojang.authlib.GameProfile
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.ChannelPipeline
 *  io.netty.channel.embedded.EmbeddedChannel
 *  net.minecraft.network.Connection
 *  net.minecraft.network.DisconnectionDetails
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.PacketFlow
 *  net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.dedicated.DedicatedServer
 *  net.minecraft.server.level.ClientInformation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.network.CommonListenerCookie
 *  net.minecraft.server.network.ServerGamePacketListenerImpl
 *  net.minecraft.world.entity.MoverType
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.craftbukkit.CraftServer
 *  org.bukkit.craftbukkit.CraftWorld
 *  org.bukkit.craftbukkit.entity.CraftPlayer
 *  org.bukkit.profile.PlayerProfile
 *  org.jetbrains.annotations.Nullable
 */
package dev.spawnbots;

import com.destroystokyo.paper.profile.CraftPlayerProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.Nullable;

final class FakePlayerFactory {
    private FakePlayerFactory() {
    }

    static ServerPlayer spawn(org.bukkit.profile.PlayerProfile profile, Location location) {
        DedicatedServer server = ((CraftServer)Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld)location.getWorld()).getHandle();
        GameProfile gameProfile = CraftPlayerProfile.asAuthlibCopy((PlayerProfile)((PlayerProfile)profile));
        ServerPlayer player = new ServerPlayer((MinecraftServer)server, level, gameProfile, ClientInformation.createDefault());
        player.getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        FakeConnection connection = new FakeConnection();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial((GameProfile)gameProfile, (boolean)false);
        boolean placed = false;
        try {
            server.getPlayerList().placeNewPlayer((Connection)connection, player, cookie);
            placed = true;
            player.connection = new FakePacketListener((MinecraftServer)server, connection, player, cookie);
            player.absSnapTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            CraftPlayer bukkitPlayer = player.getBukkitEntity();
            bukkitPlayer.setInvulnerable(true);
            bukkitPlayer.setGravity(true);
            bukkitPlayer.setCanPickupItems(true);
            bukkitPlayer.setCollidable(true);
            bukkitPlayer.setPersistent(false);
            return player;
        }
        catch (Error | RuntimeException exception) {
            if (placed) {
                server.getPlayerList().remove(player);
            } else {
                player.discard();
            }
            throw exception;
        }
    }

    private static final class FakeConnection
    extends Connection {
        private FakeConnection() {
            super(PacketFlow.SERVERBOUND);
            this.channel = new EmbeddedChannel();
            this.address = this.channel.remoteAddress();
            Connection.configureSerialization((ChannelPipeline)this.channel.pipeline(), (PacketFlow)PacketFlow.SERVERBOUND, (boolean)false, null);
        }

        public boolean isConnected() {
            return true;
        }

        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        }

        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        }

        public void send(Packet<?> packet) {
        }
    }

    private static final class FakePacketListener
    extends ServerGamePacketListenerImpl {
        private FakePacketListener(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        public void send(Packet<?> packet) {
            ClientboundSetEntityMotionPacket motion;
            if (packet instanceof ClientboundSetEntityMotionPacket && (motion = (ClientboundSetEntityMotionPacket)packet).id() == this.player.getId()) {
                this.player.lerpMotion(motion.movement());
                this.player.move(MoverType.SELF, motion.movement());
            }
        }

        public void disconnect(DisconnectionDetails details) {
        }
    }
}

