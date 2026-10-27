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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;
import java.util.Locale;

@Mod(DeathBecomes.MOD_ID)
public class DeathBecomes {
    public static final String MOD_ID = "deathbecomes";
    public static final String HEAL_LOCK_KEY = "DeathBecomesHealLockedUntil";
    private static final UUID MAX_MANA_DEBUFF_ID = UUID.fromString("d3747d07-1dc4-4d93-92a0-dc063a672860");
    private static final UUID MANA_REGEN_DEBUFF_ID = UUID.fromString("c90bab70-f641-4d9a-9aa6-3e3d79906659");
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    public static final RegistryObject<EntityType<HunterEntity>> HUNTER = ENTITY_TYPES.register("hunter",
            () -> EntityType.Builder.of(HunterEntity::new, MobCategory.CREATURE)
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
        MinecraftForge.EVENT_BUS.addListener(DeathBecomes::onPlayerClone);
        MinecraftForge.EVENT_BUS.addListener(DeathBecomes::onLivingHeal);
        MinecraftForge.EVENT_BUS.addListener(DeathBecomes::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(DeathBecomes::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(DeathBecomes::onBlockBreak);
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

        if (data.contains("DeathBecomesManaDebuffUntil")) {
            removeManaDebuff(event.player);
            data.remove("DeathBecomesManaDebuffUntil");
        }

        if (isInHunterChase(event.player) && event.player.isFallFlying()) {
            event.player.stopFallFlying();
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelWarpStoneUse(event, event.getItemStack());
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelWarpStoneUse(event, event.getItemStack());
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!player.level().isClientSide && isInHunterChase(player)) {
            event.setCanceled(true);
        }
    }

    private static void cancelWarpStoneUse(PlayerInteractEvent event, ItemStack stack) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !isInHunterChase(player) || !isWarpStone(stack)) return;
        event.setCanceled(true);
    }

    private static boolean isWarpStone(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String registryId = itemId == null ? "" : itemId.toString().toLowerCase(Locale.ROOT);
        String itemName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return (registryId.contains("warp") && registryId.contains("stone")) || itemName.contains("warp stone");
    }

    private static boolean isInHunterChase(Player player) {
        return !player.level().getEntitiesOfClass(HunterEntity.class,
                player.getBoundingBox().inflate(128.0D), hunter -> hunter.restrictsEscapeAbilities(player)).isEmpty();
    }

    private static void removeManaDebuff(Player player) {
        if (!ModList.get().isLoaded("irons_spellbooks")) return;
        removeModifier(player, "max_mana", MAX_MANA_DEBUFF_ID);
        removeModifier(player, "mana_regen", MANA_REGEN_DEBUFF_ID);
    }

    private static void removeModifier(Player player, String attributePath, UUID id) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", attributePath));
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        var oldData = event.getOriginal().getPersistentData();
        var newData = event.getEntity().getPersistentData();
        String[] keys = {com.jamesrenrold.deathbecomes.item.HeartFragmentItem.HEART_COUNT_KEY,
                HEAL_LOCK_KEY, "DeathBecomesPendingEffectsAt"};
        for (String key : keys) {
            if (oldData.contains(key)) newData.put(key, oldData.get(key).copy());
        }
    }

    private static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        long lockedUntil = player.getPersistentData().getLong(HEAL_LOCK_KEY);
        if (lockedUntil == 0L) return;
        if (player.level().getGameTime() < lockedUntil) {
            event.setCanceled(true);
        } else {
            player.getPersistentData().remove(HEAL_LOCK_KEY);
        }
    }
}
