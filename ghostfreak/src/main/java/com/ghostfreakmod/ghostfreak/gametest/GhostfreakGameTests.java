package com.ghostfreakmod.ghostfreak.gametest;

import com.ghostfreakmod.ghostfreak.GhostfreakMod;
import com.ghostfreakmod.ghostfreak.entity.GhostfreakEntity;
import com.ghostfreakmod.ghostfreak.registry.ModEntities;
import com.ghostfreakmod.ghostfreak.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(GhostfreakMod.MODID)
@PrefixGameTestTemplate(false)
public class GhostfreakGameTests {
    private static final String TEMPLATE = "empty";

    private static GhostfreakEntity tamed(GameTestHelper helper, Player owner, int x, int y, int z) {
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), x, y, z);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SOUL_SAND, 4));
        ghost.offerSoul(owner, owner.getMainHandItem(), 1.0F);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return ghost;
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void survivesIdle(GameTestHelper helper) {
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(ghost.isAlive(), "Ghostfreak died while idle");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void isAMonsterThatNeverDropsAnything(GameTestHelper helper) {
        helper.assertTrue(ModEntities.GHOSTFREAK.get().getCategory() == MobCategory.MONSTER, "Not a monster");
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        ghost.kill();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    ghost.getBoundingBox().inflate(8.0D)).isEmpty(), "A Ghostfreak dropped something");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void soulSandTamesAndCalms(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        GhostfreakEntity wild = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        wild.offerSoul(player, new ItemStack(Items.SOUL_SAND), 0.0F);
        helper.assertTrue(!wild.isTame(), "Tamed on a zero chance");
        helper.assertTrue(wild.isCalm(), "An offering did not calm it");
        wild.setTarget(helper.spawnWithNoFreeWill(EntityType.VILLAGER, 14, 2, 12));
        helper.assertTrue(wild.getTarget() == null, "A calm Ghostfreak took a target");

        GhostfreakEntity friend = tamed(helper, player, 10, 2, 10);
        helper.assertTrue(friend.isTame() && friend.isOwnedBy(player), "Not tamed on a certain chance");
        helper.succeed();
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void ownerGetsEctoplasmWithABottle(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        Player stranger = helper.makeMockPlayer();
        GhostfreakEntity ghost = tamed(helper, owner, 12, 2, 12);

        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        ghost.mobInteract(stranger, InteractionHand.MAIN_HAND);
        helper.assertTrue(!stranger.getInventory().contains(new ItemStack(ModItems.ECTOPLASM_BOTTLE.get())),
                "A stranger got ectoplasm");

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
        ghost.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getInventory().contains(new ItemStack(ModItems.ECTOPLASM_BOTTLE.get())),
                "The owner got no ectoplasm");
        helper.assertTrue(owner.getMainHandItem().getCount() == 1, "The empty bottle was not used up");

        ghost.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getMainHandItem().getCount() == 1, "A second bottle was filled during the cooldown");
        helper.succeed();
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void emptyHandCyclesFollowSitWander(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        GhostfreakEntity ghost = tamed(helper, owner, 12, 2, 12);
        helper.assertTrue(ghost.getMode() == GhostfreakEntity.MODE_FOLLOW, "Did not start in follow mode");
        ghost.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(ghost.getMode() == GhostfreakEntity.MODE_SIT && ghost.isOrderedToSit(), "Not sitting");
        ghost.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(ghost.getMode() == GhostfreakEntity.MODE_WANDER && !ghost.isOrderedToSit(), "Not wandering");
        ghost.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(ghost.getMode() == GhostfreakEntity.MODE_FOLLOW, "Did not return to follow");
        helper.succeed();
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void sittingGhostfreakStaysPut(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        GhostfreakEntity ghost = tamed(helper, owner, 12, 4, 12);
        ghost.setMode(GhostfreakEntity.MODE_SIT);
        BlockPos start = ghost.blockPosition();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(ghost.blockPosition().distSqr(start) <= 4.0D, "A sitting Ghostfreak wandered off");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void phasesThroughAWallToReachItsTarget(GameTestHelper helper) {
        for (int x = 0; x < 24; x++) {
            for (int y = 1; y < 12; y++) {
                for (int z = 11; z < 14; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.OBSIDIAN);
                }
            }
        }
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 2, 18);
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 6);
        ghost.setTarget(villager);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "The target was not reached through the wall");
            helper.assertBlock(new BlockPos(12, 5, 12), b -> b == Blocks.OBSIDIAN, "The wall was damaged");
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void phasedGhostfreakCannotBeHurt(GameTestHelper helper) {
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        ghost.setPhased(true);
        float before = ghost.getHealth();
        ghost.hurt(ghost.damageSources().generic(), 5.0F);
        helper.assertTrue(ghost.getHealth() == before, "A phased Ghostfreak took damage");
        ghost.setPhased(false);
        ghost.hurt(ghost.damageSources().generic(), 5.0F);
        helper.assertTrue(ghost.getHealth() < before, "A solid Ghostfreak took no damage");
        helper.succeed();
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void noteBlockMakesItDanceAndForgetItsTarget(GameTestHelper helper) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 14, 2, 12);
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        ghost.setTarget(villager);
        helper.assertTrue(ghost.getTarget() == villager, "Setup: no target");
        BlockPos noteBlock = helper.absolutePos(new BlockPos(12, 1, 14));
        MinecraftForge.EVENT_BUS.post(new NoteBlockEvent.Play(helper.getLevel(), noteBlock,
                Blocks.NOTE_BLOCK.defaultBlockState(), 5, net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(ghost.isDancing(), "It did not dance to a note block");
            helper.assertTrue(ghost.getTarget() == null, "It kept its target while dancing");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void aWildOneFleesLightIntoTheDark(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, 3, 12), Blocks.GLOWSTONE);
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(ghost.isInLight(), "Light level " + ghost.lightAt() + " was not enough to bother it");
            helper.succeedWhen(() -> helper.assertTrue(!ghost.isInLight(), "It stayed in the light"));
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void lingeringInBrightLightMakesAWildOneFadeAway(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, 3, 12), Blocks.GLOWSTONE);
        GhostfreakEntity ghost = helper.spawn(ModEntities.GHOSTFREAK.get(), 12, 2, 12);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(ghost.lightAt() >= GhostfreakEntity.LIGHT_FATAL,
                    "Light level " + ghost.lightAt() + " is below the fatal level");
            ghost.setLightExposure(GhostfreakEntity.LIGHT_FATAL_TICKS - 1);
            ghost.updateLightExposure();
            helper.assertTrue(ghost.isRemoved(), "It did not fade away");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = GhostfreakMod.MODID, template = TEMPLATE)
    public static void aTameOneIgnoresLight(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        helper.setBlock(new BlockPos(12, 3, 12), Blocks.GLOWSTONE);
        GhostfreakEntity ghost = tamed(helper, owner, 12, 2, 12);
        helper.runAfterDelay(5, () -> {
            ghost.setLightExposure(GhostfreakEntity.LIGHT_FATAL_TICKS - 1);
            ghost.updateLightExposure();
            helper.assertTrue(!ghost.isRemoved(), "A tame Ghostfreak faded away in the light");
            helper.succeed();
        });
    }
}
