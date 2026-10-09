package com.dacianmod.draco.gametest;

import com.dacianmod.draco.DacianDraco;
import com.dacianmod.draco.entity.DracoEntity;
import com.dacianmod.draco.registry.ModEntities;
import com.dacianmod.draco.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
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

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void biomeModifierAddsSpawns(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String id : List.of("windswept_hills", "windswept_gravelly_hills", "windswept_forest",
                "jagged_peaks", "stony_peaks", "meadow")) {
            Biome biome = biomes.get(new ResourceLocation("minecraft", id));
            boolean listed = biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                    .anyMatch(data -> data.type == ModEntities.DRACO.get());
            helper.assertTrue(listed, "Draco is not in the spawn list of " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void spawnRulePassesOnGrass(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, 0, 12), Blocks.GRASS_BLOCK);
        boolean allowed = SpawnPlacements.checkSpawnRules(ModEntities.DRACO.get(), helper.getLevel(),
                MobSpawnType.NATURAL, helper.absolutePos(new BlockPos(12, 1, 12)), helper.getLevel().getRandom());
        helper.assertTrue(allowed, "Natural spawn rule rejected an open grass position");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void capRecipeExists(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .byKey(new ResourceLocation(DacianDraco.MODID, "dacian_felt_cap")).isPresent(),
                "Dacian felt cap recipe was not loaded");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void capIsRecognisedWhenWorn(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        helper.assertTrue(!DracoEntity.isWearingCap(player), "Bare-headed player counted as wearing the cap");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.DACIAN_FELT_CAP.get()));
        helper.assertTrue(DracoEntity.isWearingCap(player), "Cap on the head was not recognised");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void bowlTradeGivesMamaliga(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        ItemStack bowls = new ItemStack(Items.BOWL, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, bowls);
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);

        draco.givePolenta(player, bowls);

        helper.assertTrue(bowls.getCount() == 2, "A bowl was not consumed");
        helper.assertTrue(player.getInventory().countItem(ModItems.MAMALIGA.get()) == 1, "No mamaliga was given");
        helper.assertTrue(draco.getPolentaCooldown() > 0, "No cooldown after a successful trade");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void capDoublesPolentaChance(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        float bare = draco.getPolentaChance(player);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.DACIAN_FELT_CAP.get()));
        helper.assertTrue(draco.getPolentaChance(player) == bare * 2.0F, "Cap did not double the chance");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void bowlInteractionEventuallySucceeds(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 64));
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        draco.tame(player);
        draco.setOrderedToSit(false);
        helper.onEachTick(() -> draco.interact(player, InteractionHand.MAIN_HAND));
        helper.succeedWhen(() -> helper.assertTrue(
                player.getInventory().countItem(ModItems.MAMALIGA.get()) > 0, "Right-click never produced mamaliga"));
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void bonesTameADraco(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 64));
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        helper.onEachTick(() -> {
            if (!draco.isTame()) {
                draco.interact(player, InteractionHand.MAIN_HAND);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(draco.isTame(), "Bones never tamed the Draco");
            helper.assertTrue(draco.isOwnedBy(player), "Draco has the wrong owner");
            helper.assertTrue(draco.isOrderedToSit(), "Newly tamed Draco should sit, like a wolf");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() < 64, "No bone was used up");
        });
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void wildDracoGivesNoPolenta(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL, 64));
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        for (int i = 0; i < 200; i++) {
            draco.interact(player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(!draco.isTame(), "A bowl tamed the Draco");
        helper.assertTrue(player.getInventory().countItem(ModItems.MAMALIGA.get()) == 0, "A wild Draco gave mamaliga");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE)
    public static void emptyHandTogglesSitting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 2, 12);
        draco.tame(player);
        draco.setOrderedToSit(false);
        draco.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(draco.isOrderedToSit(), "First click should sit the Draco");
        draco.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!draco.isOrderedToSit(), "Second click should release the Draco");
        helper.succeed();
    }

    @GameTest(templateNamespace = DacianDraco.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void sittingDracoLandsAndPerches(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        DracoEntity draco = helper.spawn(ModEntities.DRACO.get(), 12, 8, 12);
        draco.tame(player);
        draco.setOrderedToSit(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(draco.onGround(), "Sitting Draco never reached the ground");
            helper.assertTrue(draco.isInSittingPose(), "Landed Draco is not in the perch pose");
        });
    }
}
