package com.jamesrenrold.attributecommandpersist;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(AttributeCommandPersist.MOD_ID)
public final class AttributeCommandPersist {
    public static final String MOD_ID = "attributecommandpersist";

    public AttributeCommandPersist() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        Player original = event.getOriginal();
        Player replacement = event.getEntity();
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance oldInstance = original.getAttribute(attribute);
            AttributeInstance newInstance = replacement.getAttribute(attribute);
            if (oldInstance == null || newInstance == null) continue;

            // AttributeInstance.save() contains its base and permanent
            // modifiers. Temporary effect modifiers are transient and are
            // intentionally excluded from that serialized snapshot.
            newInstance.load(oldInstance.save());
        }
    }
}
