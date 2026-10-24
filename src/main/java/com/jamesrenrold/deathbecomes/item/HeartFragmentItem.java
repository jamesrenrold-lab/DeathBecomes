package com.jamesrenrold.deathbecomes.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class HeartFragmentItem extends Item {
    public HeartFragmentItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (!level.isClientSide && maxHealth != null) {
            maxHealth.addPermanentModifier(new AttributeModifier(UUID.randomUUID(), "Death Becomes heart fragment", 1.0D, AttributeModifier.Operation.ADDITION));
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0F));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
