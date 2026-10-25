package com.jamesrenrold.deathbecomes.client;

import com.jamesrenrold.deathbecomes.DeathBecomes;
import com.jamesrenrold.deathbecomes.entity.HunterEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DeathBecomes.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private static volatile long blackScreenUntil;

    private ClientModEvents() { }

    public static void flashBlackScreen(int durationTicks) {
        blackScreenUntil = System.currentTimeMillis() + Math.max(1, durationTicks) * 50L;
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeathBecomes.HUNTER.get(), HunterRenderer::new);
    }

    @Mod.EventBusSubscriber(modid = DeathBecomes.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class CameraEvents {
        private CameraEvents() { }

        @SubscribeEvent
        public static void shakeCamera(ViewportEvent.ComputeCameraAngles event) {
            if (!(event.getCamera().getEntity() instanceof Player player)) return;
            var hunters = player.level().getEntitiesOfClass(HunterEntity.class, player.getBoundingBox().inflate(12.0D),
                    hunter -> hunter.getTargetEntityId() == player.getId() && hunter.isShakeActive());
            if (hunters.isEmpty()) return;
            float wave = (float) Math.sin((player.tickCount + event.getPartialTick()) * 2.4F);
            event.setYaw(event.getYaw() + wave * 0.45F);
            event.setPitch(event.getPitch() + Math.abs(wave) * 0.35F);
            event.setRoll(event.getRoll() + wave * 0.7F);
        }

        @SubscribeEvent
        public static void hideDepartureWithBlackScreen(RenderGuiOverlayEvent.Pre event) {
            if (System.currentTimeMillis() >= blackScreenUntil) return;
            event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(), 0xFF000000);
            event.setCanceled(true);
        }
    }
}
