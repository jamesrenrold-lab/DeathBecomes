package com.jamesrenrold.deathbecomes.item;

import com.jamesrenrold.deathbecomes.DeathBecomes;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DeathBecomes.MOD_ID);
    public static final RegistryObject<Item> HEART_FRAGMENT = ITEMS.register("heart_fragment", HeartFragmentItem::new);

    private ModItems() { }
}
