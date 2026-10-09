package com.dacianmod.draco.item;

import com.dacianmod.draco.DacianDraco;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

public class DacianFeltMaterial implements ArmorMaterial {
    public static final DacianFeltMaterial INSTANCE = new DacianFeltMaterial();

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return 80;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return 1;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ItemTags.WOOL);
    }

    @Override
    public String getName() {
        return DacianDraco.MODID + ":dacian_felt";
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
