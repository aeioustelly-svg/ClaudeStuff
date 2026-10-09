package com.alienfauna.gametest;

import com.alienfauna.AlienFauna;
import com.alienfauna.entity.CannonboltEntity;
import com.alienfauna.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.List;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(AlienFauna.MODID)
@PrefixGameTestTemplate(false)
public class CannonboltGameTests {
    private static final String TEMPLATE = "empty";

    /** The empty arena has no floor of its own that these tests can rely on. */
    private static void floor(GameTestHelper helper) {
        for (int x = 2; x < 22; x++) {
            for (int z = 2; z < 22; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static Villager target(GameTestHelper helper, int x, int y, int z) {
        return helper.spawnWithNoFreeWill(EntityType.VILLAGER, x, y, z);
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void survivesIdle(GameTestHelper helper) {
        floor(helper);
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        helper.runAfterDelay(100, () -> {
            // It may well have curled up and rolled for fun by now: that is allowed, so only life is checked.
            helper.assertTrue(cannonbolt.isAlive(), "Cannonbolt died while idle");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void rollAttackHurtsTargetAndLeavesItDizzy(GameTestHelper helper) {
        floor(helper);
        Villager villager = target(helper, 12, 2, 12);
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 19);
        cannonbolt.setTarget(villager);
        boolean[] hurt = {false};
        helper.onEachTick(() -> {
            if (villager.getHealth() < villager.getMaxHealth()) hurt[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt[0], "Target was not hit by the rolling Cannonbolt");
            helper.assertTrue(cannonbolt.isDizzy(), "Cannonbolt was not dizzy after the impact");
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void babyIsSmallerAndNeverFights(GameTestHelper helper) {
        floor(helper);
        Villager villager = target(helper, 12, 2, 12);
        CannonboltEntity adult = helper.spawnWithNoFreeWill(ModEntities.CANNONBOLT.get(), 4, 2, 4);
        CannonboltEntity baby = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 18);
        baby.setBaby(true);
        baby.setTarget(villager);
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(baby.isBaby(), "The baby grew up in a few seconds");
            helper.assertTrue(baby.getBbHeight() < adult.getBbHeight(), "The baby is not smaller than the adult");
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "A baby Cannonbolt hurt its target");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void feedingATameBabyMakesItGrow(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MELON_SLICE, 4));
        CannonboltEntity baby = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        baby.setBaby(true);
        baby.tame(player);
        baby.setOrderedToSit(false);
        int before = baby.getAge();
        baby.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(baby.getAge() > before, "Feeding a tame baby did not move it towards adulthood");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3, "The melon slice was not used");
        helper.succeed();
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void leansForwardBeforeBecomingABall(GameTestHelper helper) {
        floor(helper);
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        cannonbolt.startRolling();
        helper.assertTrue(cannonbolt.isCurling(), "Cannonbolt did not start the curling animation");
        helper.assertTrue(!cannonbolt.isRolling(), "Cannonbolt became a ball with no curling animation");
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(!cannonbolt.isCurling(), "Cannonbolt was still curling after the animation time");
            helper.assertTrue(cannonbolt.isRolling(), "Cannonbolt was not a ball after curling up");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void ballIsSmallerThanTheStandingBody(GameTestHelper helper) {
        floor(helper);
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        float standing = cannonbolt.getBbHeight();
        cannonbolt.startRolling();
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(cannonbolt.isRolling(), "Cannonbolt did not curl up");
            helper.assertTrue(cannonbolt.getBbHeight() < standing, "Ball is not shorter than the standing body");
            cannonbolt.stopRolling(0);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(!cannonbolt.isRolling(), "Cannonbolt did not uncurl in open space");
                helper.assertTrue(cannonbolt.getBbHeight() == standing, "Standing height did not come back");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void staysCurledUpWhereItCannotStand(GameTestHelper helper) {
        floor(helper);
        // A top slab 1.5 blocks over the floor clears the 1.4 high ball but not the standing body.
        for (int x = 11; x <= 13; x++) {
            for (int z = 11; z <= 13; z++) {
                helper.setBlock(new BlockPos(x, 3, z),
                        Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
        }
        CannonboltEntity cannonbolt = helper.spawnWithNoFreeWill(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        cannonbolt.startRolling();
        helper.runAfterDelay(15, () -> cannonbolt.stopRolling(0));
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(cannonbolt.isRolling(), "Cannonbolt uncurled inside a space too low to stand");
            for (int x = 11; x <= 13; x++) {
                for (int z = 11; z <= 13; z++) {
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 60, "Waiting for the ceiling to go");
            helper.assertTrue(!cannonbolt.isRolling(), "Cannonbolt did not stand up once there was room");
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void ballTakesLessProjectileDamage(GameTestHelper helper) {
        floor(helper);
        CannonboltEntity standing = helper.spawnWithNoFreeWill(ModEntities.CANNONBOLT.get(), 8, 2, 12);
        CannonboltEntity ball = helper.spawnWithNoFreeWill(ModEntities.CANNONBOLT.get(), 16, 2, 12);
        ball.startRolling();
        helper.runAfterDelay(15, () -> {
            Snowball snowball = new Snowball(helper.getLevel(), 0, 0, 0);
            DamageSource thrown = helper.getLevel().damageSources().thrown(snowball, null);
            standing.hurt(thrown, 10.0F);
            ball.hurt(thrown, 10.0F);
            float lostStanding = standing.getMaxHealth() - standing.getHealth();
            float lostBall = ball.getMaxHealth() - ball.getHealth();
            helper.assertTrue(lostStanding > 0.0F, "Standing Cannonbolt took no damage");
            helper.assertTrue(lostBall < lostStanding, "Ball took as much projectile damage as the standing body");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void melonSliceCalmsAnAngryCannonbolt(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        ItemStack melon = new ItemStack(Items.MELON_SLICE, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, melon);
        Villager villager = target(helper, 12, 2, 12);
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 18);
        cannonbolt.setTarget(villager);
        cannonbolt.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(cannonbolt.getTarget() == null, "Melon slice did not calm the Cannonbolt");
        helper.assertTrue(melon.getCount() == 3, "The melon slice was not used up");
        helper.assertTrue(!cannonbolt.isTame(), "Calming should not tame by itself");
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "A calmed Cannonbolt still hurt the target");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void melonSlicesTameACannonbolt(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MELON_SLICE, 64));
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        helper.onEachTick(() -> {
            if (!cannonbolt.isTame()) {
                cannonbolt.interact(player, InteractionHand.MAIN_HAND);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cannonbolt.isTame(), "Melon slices never tamed the Cannonbolt");
            helper.assertTrue(cannonbolt.isOwnedBy(player), "Cannonbolt has the wrong owner");
            helper.assertTrue(cannonbolt.isOrderedToSit(), "Newly tamed Cannonbolt should sit, like a wolf");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() < 64, "No melon slice was used up");
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void emptyHandTogglesSitting(GameTestHelper helper) {
        floor(helper);
        Player player = helper.makeMockPlayer();
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        cannonbolt.tame(player);
        cannonbolt.setOrderedToSit(false);
        cannonbolt.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(cannonbolt.isOrderedToSit(), "First click should sit the Cannonbolt");
        cannonbolt.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!cannonbolt.isOrderedToSit(), "Second click should release the Cannonbolt");
        helper.succeed();
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void strangersCannotSitATameCannonbolt(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer();
        Player stranger = helper.makeMockPlayer();
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        cannonbolt.tame(owner);
        cannonbolt.setOrderedToSit(false);
        cannonbolt.interact(stranger, InteractionHand.MAIN_HAND);
        helper.assertTrue(!cannonbolt.isOrderedToSit(), "A stranger told the Cannonbolt to sit");
        helper.succeed();
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void dropsNothingOnDeath(GameTestHelper helper) {
        floor(helper);
        CannonboltEntity cannonbolt = helper.spawn(ModEntities.CANNONBOLT.get(), 12, 2, 12);
        cannonbolt.kill();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    cannonbolt.getBoundingBox().inflate(8.0D)).isEmpty(), "A Cannonbolt dropped something on death");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void biomeModifierAddsSpawns(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String id : List.of("savanna", "savanna_plateau", "windswept_savanna",
                "badlands", "wooded_badlands", "eroded_badlands")) {
            Biome biome = biomes.get(new ResourceLocation("minecraft", id));
            boolean listed = biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                    .anyMatch(data -> data.type == ModEntities.CANNONBOLT.get());
            helper.assertTrue(listed, "Cannonbolt is not in the spawn list of " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = AlienFauna.MODID, template = TEMPLATE)
    public static void spawnRulePassesOnGrass(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, 0, 12), Blocks.GRASS_BLOCK);
        boolean allowed = SpawnPlacements.checkSpawnRules(ModEntities.CANNONBOLT.get(), helper.getLevel(),
                MobSpawnType.NATURAL, helper.absolutePos(new BlockPos(12, 1, 12)), helper.getLevel().getRandom());
        helper.assertTrue(allowed, "Natural spawn rule rejected an open grass position");
        helper.succeed();
    }
}
