package com.jamesrenrold.deathbecomes;

import com.jamesrenrold.deathbecomes.entity.HunterEntity;
import com.jamesrenrold.deathbecomes.item.ModItems;
import com.jamesrenrold.deathbecomes.network.DeathBecomesNetwork;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DeathBecomes.MOD_ID)
public class DeathBecomes {
    public static final String MOD_ID = "deathbecomes";
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    public static final RegistryObject<EntityType<HunterEntity>> HUNTER = ENTITY_TYPES.register("hunter",
            () -> EntityType.Builder.of(HunterEntity::new, MobCategory.MONSTER)
                    .sized(0.72F, 2.16F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(new ResourceLocation(MOD_ID, "hunter").toString()));

    public static final RegistryObject<SoundEvent> CHASE_MUSIC = SOUND_EVENTS.register("chase_music",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "chase_music")));

    public DeathBecomes() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITY_TYPES.register(modBus);
        SOUND_EVENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::registerAttributes);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(DeathBecomes::onPlayerTick);
        DeathBecomesNetwork.register();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        AttributeSupplier.Builder attributes = HunterEntity.createAttributes();
        event.put(HUNTER.get(), attributes.build());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deathbecomes")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> {
                                    Player target = EntityArgument.getPlayer(context, "target");
                                    if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                                        context.getSource().sendFailure(Component.literal("The target must be in a server world."));
                                        return 0;
                                    }
                                    float yaw = target.getYRot() * ((float) Math.PI / 180F);
                                    double x = target.getX() - Math.sin(yaw) * 6.0D;
                                    double z = target.getZ() + Math.cos(yaw) * 6.0D;
                                    BlockPos ground = BlockPos.containing(x, target.getY(), z);
                                    while (ground.getY() > level.getMinBuildHeight() + 1 && level.getBlockState(ground).isAir()) {
                                        ground = ground.below();
                                    }
                                    BlockPos spawnPos = ground.above();
                                    HunterEntity hunter = DeathBecomes.HUNTER.get().create(level);
                                    if (hunter == null) {
                                        context.getSource().sendFailure(Component.translatable("deathbecomes.command.failed"));
                                        return 0;
                                    }
                                    hunter.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, target.getYRot() + 180.0F, 0.0F);
                                    hunter.beginHunt(target);
                                    if (!level.addFreshEntity(hunter)) {
                                        context.getSource().sendFailure(Component.translatable("deathbecomes.command.failed"));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.translatable("deathbecomes.command.spawned", target.getDisplayName()), true);
                                    return 1;
                                }))));
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        var data = event.player.getPersistentData();
        String key = "DeathBecomesPendingEffectsAt";
        if (data.contains(key) && event.player.level().getGameTime() >= data.getLong(key)) {
            event.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 45 * 20, 2));
            event.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 45 * 20, 1));
            data.remove(key);
        }
    }
}
