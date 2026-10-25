package com.jamesrenrold.deathbecomes.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class HeartFragmentItem extends Item {
    public static final String HEART_COUNT_KEY = "DeathBecomesBonusHearts";
    private static final UUID HEART_MODIFIER_ID = UUID.fromString("8e34dc37-7bce-4b36-98fd-60e6a5b14b46");
    private static final String LEGACY_MODIFIER_NAME = "Death Becomes heart fragment";

    public HeartFragmentItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            addHeart(player);
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2.0F));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static int getHeartCount(Player player) {
        var data = player.getPersistentData();
        if (!data.contains(HEART_COUNT_KEY)) {
            AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
            List<UUID> legacyIds = new ArrayList<>();
            if (maxHealth != null) {
                for (AttributeModifier modifier : maxHealth.getModifiers()) {
                    if (LEGACY_MODIFIER_NAME.equals(modifier.getName())) legacyIds.add(modifier.getId());
                }
                legacyIds.forEach(maxHealth::removeModifier);
            }
            data.putInt(HEART_COUNT_KEY, legacyIds.size());
            if (!legacyIds.isEmpty()) syncHeartModifier(player, legacyIds.size());
        }
        return Math.max(0, data.getInt(HEART_COUNT_KEY));
    }

    public static void addHeart(Player player) {
        int count = getHeartCount(player) + 1;
        player.getPersistentData().putInt(HEART_COUNT_KEY, count);
        syncHeartModifier(player, count);
    }

    public static boolean removeHeart(Player player) {
        int count = getHeartCount(player);
        if (count <= 0) return false;
        count--;
        player.getPersistentData().putInt(HEART_COUNT_KEY, count);
        syncHeartModifier(player, count);
        return true;
    }

    private static void syncHeartModifier(Player player, int count) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.removeModifier(HEART_MODIFIER_ID);
        if (count > 0) {
            maxHealth.addPermanentModifier(new AttributeModifier(HEART_MODIFIER_ID,
                    "Death Becomes bonus hearts", count * 2.0D, AttributeModifier.Operation.ADDITION));
        }
    }
}
