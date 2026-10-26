package com.jamesrenrold.deathbecomes.network;

import com.jamesrenrold.deathbecomes.DeathBecomes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class DeathBecomesNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DeathBecomes.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private DeathBecomesNetwork() { }

    public static void register() {
        if (registered) return;
        CHANNEL.registerMessage(0, ChaseMusicMessage.class, ChaseMusicMessage::encode, ChaseMusicMessage::new, ChaseMusicMessage::handle);
        CHANNEL.registerMessage(1, DepartureFlashMessage.class, DepartureFlashMessage::encode, DepartureFlashMessage::new, DepartureFlashMessage::handle);
        CHANNEL.registerMessage(2, ChaseTimerMessage.class, ChaseTimerMessage::encode, ChaseTimerMessage::new, ChaseTimerMessage::handle);
        registered = true;
    }

    public static void sendChaseMusic(ServerPlayer player, boolean start) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseMusicMessage(start));
    }

    public static void sendDepartureFlash(ServerPlayer player, int durationTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DepartureFlashMessage(durationTicks));
    }

    public static void sendChaseTimer(ServerPlayer player, int secondsRemaining) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ChaseTimerMessage(secondsRemaining));
    }

    private static final class ChaseMusicMessage {
        private final boolean playing;

        private ChaseMusicMessage(boolean playing) { this.playing = playing; }
        private ChaseMusicMessage(FriendlyByteBuf buffer) { this.playing = buffer.readBoolean(); }
        private void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(playing); }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jamesrenrold.deathbecomes.client.ClientMusic.setPlaying(playing)));
            context.setPacketHandled(true);
        }
    }

    private static final class ChaseTimerMessage {
        private final int secondsRemaining;

        private ChaseTimerMessage(int secondsRemaining) { this.secondsRemaining = secondsRemaining; }
        private ChaseTimerMessage(FriendlyByteBuf buffer) { this.secondsRemaining = buffer.readVarInt(); }
        private void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(secondsRemaining); }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jamesrenrold.deathbecomes.client.ClientModEvents.setChaseTimer(secondsRemaining)));
            context.setPacketHandled(true);
        }
    }

    private static final class DepartureFlashMessage {
        private final int durationTicks;

        private DepartureFlashMessage(int durationTicks) { this.durationTicks = durationTicks; }
        private DepartureFlashMessage(FriendlyByteBuf buffer) { this.durationTicks = buffer.readVarInt(); }
        private void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(durationTicks); }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jamesrenrold.deathbecomes.client.ClientModEvents.flashBlackScreen(durationTicks)));
            context.setPacketHandled(true);
        }
    }
}