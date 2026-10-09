package com.dacianmod.draco.gametest;

import com.dacianmod.draco.DacianDraco;
import com.dacianmod.draco.entity.DracoEntity;
import com.dacianmod.draco.registry.ModEntities;
import com.dacianmod.draco.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(DacianDraco.MODID)
@PrefixGameTestTemplate(false)
public class DracoGameTests {
    private static final String TEMPLATE = "empty";
    private static final int NEVER = 1_000_000;

    private static Villager target(GameTestHelper helper, int x, int y, int z) {
        return helper.spawnWithNoFreeWill(EntityType.VILLAGER, x, y, z);
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void survivesIdle(GameTestHelper helper) {
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(draco.isAlive(), "Draco died while idle");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void shedsSkinWithoutBeingKilled(GameTestHelper helper) {
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        draco.setShedTimer(5);
        helper.succeedWhen(() ->
                helper.assertItemEntityPresent(ModItems.SHED_SKIN.get(), new BlockPos(12, 2, 12), 16.0D));
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void howlSlowsTarget(GameTestHelper helper) {
        Villager villager = target(helper, 12, 2, 12);
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 17);
        draco.setDiveCooldown(NEVER);
        draco.setLashCooldown(NEVER);
        draco.setTarget(villager);
        helper.succeedWhen(() ->
                helper.assertTrue(villager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Target was not slowed by the howl"));
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void tailLashHurtsAndBlinds(GameTestHelper helper) {
        Villager villager = target(helper, 12, 2, 12);
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 14);
        draco.setDiveCooldown(NEVER);
        draco.setHowlCooldown(NEVER);
        draco.setTarget(villager);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "Target was not hurt by the lash");
            helper.assertTrue(villager.hasEffect(MobEffects.BLINDNESS), "Target was not blinded by the lash");
        });
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE, timeoutTicks = 1200)
    public static void diveHitsTarget(GameTestHelper helper) {
        Villager villager = target(helper, 12, 2, 12);
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 20);
        draco.setHowlCooldown(NEVER);
        draco.setLashCooldown(NEVER);
        draco.setTarget(villager);
        helper.succeedWhen(() ->
                helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "Target was not hurt by the dive"));
    }
}
