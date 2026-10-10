package com.okapimod.okapi.gametest;

import com.okapimod.okapi.OkapiMod;
import com.okapimod.okapi.entity.OkapiEntity;
import com.okapimod.okapi.entity.OkapiForaging;
import com.okapimod.okapi.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(OkapiMod.MODID)
@PrefixGameTestTemplate(false)
public class OkapiGameTests {
    private static final String TEMPLATE = "empty";
    private static final int NEVER = 1_000_000;

    /** A grass floor at y = 1, so the okapi (which cannot fly) has something to stand on. */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }
    }

    private static OkapiEntity spawnOkapi(GameTestHelper helper, int x, int z) {
        floor(helper);
        OkapiEntity okapi = helper.spawn(ModEntities.OKAPI.get(), x, 2, z);
        okapi.setForageCooldown(0);
        return okapi;
    }

    private static OkapiEntity spawnTamed(GameTestHelper helper, Player owner, int x, int z) {
        OkapiEntity okapi = spawnOkapi(helper, x, z);
        okapi.tame(owner);
        okapi.setOrderedToSit(false);
        return okapi;
    }

    /** A ripe cocoa pod at (12, 2, 14), hanging on a jungle log at (12, 2, 15). */
    private static BlockPos placeRipeCocoa(GameTestHelper helper) {
        BlockPos pod = new BlockPos(12, 2, 14);
        helper.setBlock(new BlockPos(12, 2, 15), Blocks.JUNGLE_LOG);
        helper.setBlock(pod, Blocks.COCOA.defaultBlockState()
                .setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        return pod;
    }

    // ---- befriending ----

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void survivesIdle(GameTestHelper helper) {
        OkapiEntity okapi = spawnOkapi(helper, 12, 12);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(okapi.isAlive(), "Okapi died while idle");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void carelessGiftOnlyStartles(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.JUNGLE_LEAVES, 64));
        OkapiEntity okapi = spawnOkapi(helper, 12, 12);
        for (int i = 0; i < 10; i++) {
            okapi.interact(player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(!okapi.isTame(), "A careless gift tamed the okapi");
        helper.assertTrue(okapi.getTrust() == 0, "A careless gift built trust");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 64, "A careless gift was used up");
        helper.succeed();
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void threeSneakingGiftsTame(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.JUNGLE_LEAVES, 64));
        OkapiEntity okapi = spawnOkapi(helper, 12, 12);

        okapi.interact(player, InteractionHand.MAIN_HAND);
        okapi.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!okapi.isTame() && okapi.getTrust() == 2, "Two gifts should build trust but not tame");
        okapi.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(okapi.isTame(), "Three gifts never tamed the okapi");
        helper.assertTrue(okapi.isOwnedBy(player), "Okapi has the wrong owner");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 61, "Gifts were not used up");
        helper.succeed();
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void emptyHandTogglesSitting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        OkapiEntity okapi = spawnTamed(helper, player, 12, 12);
        okapi.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(okapi.isOrderedToSit(), "First click should sit the okapi");
        okapi.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!okapi.isOrderedToSit(), "Second click should release the okapi");
        helper.succeed();
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void sneakingEmptyHandReturnsThePack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        OkapiEntity okapi = spawnTamed(helper, player, 12, 12);
        okapi.getPack().addItem(new ItemStack(Items.COCOA_BEANS, 5));
        player.setShiftKeyDown(true);
        okapi.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(okapi.getPack().isEmpty(), "The pack was not emptied");
        helper.assertTrue(!okapi.isOrderedToSit(), "Collecting the pack must not sit the okapi");
        helper.assertItemEntityPresent(Items.COCOA_BEANS, new BlockPos(12, 2, 12), 8.0D);
        helper.succeed();
    }

    // ---- what a tongue can take ----

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void ripeCropsAreHarvestedAndReset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);

        BlockPos pod = placeRipeCocoa(helper);
        List<ItemStack> beans = OkapiForaging.harvest(level, helper.absolutePos(pod),
                helper.getBlockState(pod), level.getRandom());
        helper.assertTrue(beans.size() == 1 && beans.get(0).is(Items.COCOA_BEANS) && beans.get(0).getCount() >= 2,
                "Cocoa did not give beans");
        helper.assertTrue(helper.getBlockState(pod).getValue(CocoaBlock.AGE) == 0, "Cocoa was not reset to a young pod");

        BlockPos bush = new BlockPos(8, 2, 8);
        helper.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        List<ItemStack> berries = OkapiForaging.harvest(level, helper.absolutePos(bush),
                helper.getBlockState(bush), level.getRandom());
        helper.assertTrue(berries.size() == 1 && berries.get(0).is(Items.SWEET_BERRIES), "Bush did not give berries");
        helper.assertTrue(helper.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) == 1, "Bush was not cut back");

        BlockPos vines = new BlockPos(16, 6, 16);
        helper.setBlock(vines, Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true));
        BlockState vineState = helper.getBlockState(vines);
        List<ItemStack> glow = OkapiForaging.harvest(level, helper.absolutePos(vines), vineState, level.getRandom());
        helper.assertTrue(glow.size() == 1 && glow.get(0).is(Items.GLOW_BERRIES), "Vines did not give glow berries");
        helper.assertTrue(!helper.getBlockState(vines).getValue(BlockStateProperties.BERRIES), "Berries were not picked");
        helper.succeed();
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void unripeCropsAreLeftAlone(GameTestHelper helper) {
        BlockPos pod = new BlockPos(12, 2, 14);
        helper.setBlock(new BlockPos(12, 2, 15), Blocks.JUNGLE_LOG);
        helper.setBlock(pod, Blocks.COCOA.defaultBlockState()
                .setValue(CocoaBlock.AGE, 1)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        helper.assertTrue(OkapiForaging.kindOf(helper.getBlockState(pod)) == null, "A young pod counted as ripe");
        helper.succeed();
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void trimmingLeavesNeverRemovesThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos leaf = new BlockPos(12, 3, 12);
        helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        int items = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack stack : OkapiForaging.harvest(level, helper.absolutePos(leaf), helper.getBlockState(leaf), level.getRandom())) {
                helper.assertTrue(stack.is(Items.APPLE) || stack.is(Items.OAK_SAPLING) || stack.is(Items.STICK),
                        "Unexpected item from oak leaves: " + stack);
                items++;
            }
        }
        helper.assertTrue(helper.getBlockState(leaf).is(Blocks.OAK_LEAVES), "The leaf block was removed");
        helper.assertTrue(items > 0, "400 trims gave nothing at all");
        helper.succeed();
    }

    // ---- foraging and delivery ----

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE, timeoutTicks = 900)
    public static void tamedOkapiWalksToCocoaAndHarvestsIt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        BlockPos pod = placeRipeCocoa(helper);
        OkapiEntity okapi = spawnTamed(helper, player, 12, 8);
        helper.succeedWhen(() -> {
            helper.assertTrue(okapi.getPack().countItem(Items.COCOA_BEANS) >= 2, "No cocoa beans in the pack");
            helper.assertTrue(helper.getBlockState(pod).getValue(CocoaBlock.AGE) == 0, "The pod was not reset");
        });
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void wildOkapiNeverHarvests(GameTestHelper helper) {
        BlockPos pod = placeRipeCocoa(helper);
        OkapiEntity okapi = spawnOkapi(helper, 12, 12);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(helper.getBlockState(pod).getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE,
                    "A wild okapi harvested a pod");
            helper.assertTrue(okapi.getPack().isEmpty(), "A wild okapi carried something");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void sittingOkapiStaysOffDuty(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        BlockPos pod = placeRipeCocoa(helper);
        OkapiEntity okapi = spawnTamed(helper, player, 12, 12);
        okapi.setOrderedToSit(true);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(helper.getBlockState(pod).getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE,
                    "A sitting okapi harvested a pod");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE, timeoutTicks = 900)
    public static void fullPackGoesIntoTheBarrel(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        BlockPos barrel = new BlockPos(12, 2, 16);
        floor(helper);
        helper.setBlock(barrel, Blocks.BARREL);
        OkapiEntity okapi = spawnTamed(helper, player, 12, 9);
        okapi.setForageCooldown(NEVER);
        okapi.getPack().addItem(new ItemStack(Items.COCOA_BEANS, 20));
        helper.succeedWhen(() -> {
            BarrelBlockEntity container = (BarrelBlockEntity) helper.getBlockEntity(barrel);
            helper.assertTrue(container.countItem(Items.COCOA_BEANS) == 20, "The barrel did not receive the haul");
            helper.assertTrue(okapi.getPack().isEmpty(), "The okapi kept part of the haul");
        });
    }

    // ---- pacifism ----

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void deathDropsOnlyTheHaul(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        OkapiEntity okapi = spawnTamed(helper, player, 12, 12);
        okapi.getPack().addItem(new ItemStack(Items.STICK, 3));
        okapi.hurt(okapi.damageSources().generic(), 1000.0F);
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(new BlockPos(12, 2, 12))).inflate(6.0D));
        helper.assertTrue(!dropped.isEmpty(), "The haul was lost");
        for (ItemEntity item : dropped) {
            helper.assertTrue(item.getItem().is(Items.STICK), "Unexpected death drop: " + item.getItem());
        }
        helper.succeed();
    }

    // ---- world generation ----

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void biomeModifierAddsJungleSpawns(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String id : List.of("jungle", "sparse_jungle", "bamboo_jungle")) {
            Biome biome = biomes.get(new ResourceLocation("minecraft", id));
            boolean listed = biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                    .anyMatch(data -> data.type == ModEntities.OKAPI.get());
            helper.assertTrue(listed, "Okapi is not in the spawn list of " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = OkapiMod.MODID, template = TEMPLATE)
    public static void spawnRulePassesOnLitGrass(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(new BlockPos(12, 4, 12), Blocks.GLOWSTONE);
        // the light engine needs a few ticks to catch up with the new block
        helper.runAfterDelay(20, () -> {
            boolean allowed = SpawnPlacements.checkSpawnRules(ModEntities.OKAPI.get(), helper.getLevel(),
                    MobSpawnType.NATURAL, helper.absolutePos(new BlockPos(12, 2, 12)), helper.getLevel().getRandom());
            helper.assertTrue(allowed, "Natural spawn rule rejected a lit grass position");
            helper.succeed();
        });
    }
}
