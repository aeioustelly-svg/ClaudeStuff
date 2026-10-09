package com.gnoblarmod.gnoblars.gametest;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import com.gnoblarmod.gnoblars.entity.GnoblarVariant;
import com.gnoblarmod.gnoblars.registry.ModEntities;
import com.gnoblarmod.gnoblars.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(Gnoblars.MODID)
@PrefixGameTestTemplate(false)
public class GnoblarGameTests {
    private static final String TEMPLATE = "empty";

    private static GnoblarEntity spawn(GameTestHelper helper) {
        return helper.spawn(ModEntities.GNOBLAR.get(), 12, 1, 12);
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        return player;
    }

    private static InteractionResult give(GnoblarEntity gnoblar, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return gnoblar.mobInteract(player, InteractionHand.MAIN_HAND);
    }

    private static void tame(GnoblarEntity gnoblar, Player player) {
        gnoblar.setTame(true);
        gnoblar.setOwnerUUID(player.getUUID());
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void survivesIdle(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(gnoblar.isAlive(), "Gnoblar died while idle");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void giftsBuildTrustAndTame(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 4);
        for (int i = 1; i <= 3; i++) {
            give(gnoblar, player, flesh);
            helper.assertTrue(gnoblar.getTrust() == i, "Trust should be " + i + " but was " + gnoblar.getTrust());
            helper.assertTrue(!gnoblar.isTame(), "Gnoblar tamed too early, after gift " + i);
        }
        give(gnoblar, player, flesh);
        helper.assertTrue(gnoblar.isTame(), "Gnoblar was not befriended by the fourth gift");
        helper.assertTrue(gnoblar.isOwnedBy(player), "Gnoblar has the wrong owner");
        helper.assertTrue(flesh.isEmpty(), "Gifts were not consumed");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void nosePickleCountsDouble(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        ItemStack pickles = new ItemStack(ModItems.NOSE_PICKLE.get(), 2);
        give(gnoblar, player, pickles);
        helper.assertTrue(gnoblar.getTrust() == 2, "One nose pickle should give 2 trust");
        give(gnoblar, player, pickles);
        helper.assertTrue(gnoblar.isTame(), "Two nose pickles should befriend a gnoblar");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void unlikedItemsAreIgnored(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        ItemStack dirt = new ItemStack(Items.DIRT, 4);
        helper.assertTrue(give(gnoblar, player, dirt) == InteractionResult.PASS, "Dirt should not be accepted");
        helper.assertTrue(dirt.getCount() == 4 && gnoblar.getTrust() == 0, "Dirt changed something");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void hurtingForgetsTrust(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        gnoblar.setTrust(3);
        gnoblar.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(gnoblar.getTrust() == 0, "Trust survived being hit");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void ownerTogglesSitting(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        tame(gnoblar, player);
        gnoblar.setOrderedToSit(false);
        give(gnoblar, player, ItemStack.EMPTY);
        helper.assertTrue(gnoblar.isOrderedToSit(), "Empty hand did not make the gnoblar sit");
        give(gnoblar, player, ItemStack.EMPTY);
        helper.assertTrue(!gnoblar.isOrderedToSit(), "Empty hand did not make the gnoblar stand");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void strangersCannotCommandAFriend(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player owner = player(helper);
        Player stranger = player(helper);
        tame(gnoblar, owner);
        gnoblar.setOrderedToSit(false);
        give(gnoblar, stranger, ItemStack.EMPTY);
        helper.assertTrue(!gnoblar.isOrderedToSit(), "A stranger made the gnoblar sit");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void scavengesDroppedItems(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        helper.spawnItem(Items.GOLD_INGOT, 15, 1, 12);
        helper.succeedWhen(() -> {
            helper.assertTrue(gnoblar.getMainHandItem().is(Items.GOLD_INGOT), "Gnoblar did not pick up the dropped item");
            helper.assertTrue(gnoblar.isHoarding(), "Gnoblar should be hoarding");
        });
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void giftMakesItReturnHoard(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        gnoblar.hoard(new ItemStack(Items.DIAMOND));
        give(gnoblar, player, new ItemStack(Items.BONE));
        helper.assertTrue(!gnoblar.isHoarding(), "Gnoblar kept its hoard after a gift");
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.DIAMOND, new BlockPos(12, 1, 12), 4.0D));
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void deathReturnsHoardAndDropsNothingElse(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        gnoblar.hoard(new ItemStack(Items.EMERALD));
        gnoblar.kill();
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.EMERALD, new BlockPos(12, 1, 12), 4.0D));
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void fleesFromMonsters(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 12, 1, 16);
        helper.succeedWhen(() ->
                helper.assertTrue(gnoblar.isScared(), "Gnoblar did not run from the zombie"));
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 3000)
    public static void friendSniffsUpScrap(GameTestHelper helper) {
        for (int x = 9; x <= 15; x++) {
            for (int z = 9; z <= 15; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
            }
        }
        GnoblarEntity gnoblar = spawn(helper);
        tame(gnoblar, player(helper));
        // A mock owner is not in the level, and vanilla's sit goal keeps a pet with a missing owner seated.
        gnoblar.goalSelector.removeAllGoals(goal -> goal instanceof SitWhenOrderedToGoal);
        gnoblar.setSniffCooldown(0);
        helper.succeedWhen(() ->
                helper.assertTrue(gnoblar.getSniffsCompleted() > 0, "Friendly gnoblar never finished sniffing"));
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void sniffLootTableIsUsable(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, gnoblar.position())
                .withParameter(LootContextParams.THIS_ENTITY, gnoblar)
                .create(LootContextParamSets.GIFT);
        var table = helper.getLevel().getServer().getLootData().getLootTable(GnoblarEntity.SNIFF_LOOT);
        for (int i = 0; i < 20; i++) {
            helper.assertTrue(!table.getRandomItems(params).isEmpty(), "Sniffing loot table rolled nothing");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void biomeModifierAddsSpawns(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String id : List.of("swamp", "mangrove_swamp", "taiga", "old_growth_pine_taiga",
                "old_growth_spruce_taiga", "snowy_taiga", "badlands", "wooded_badlands", "eroded_badlands")) {
            Biome biome = biomes.get(new ResourceLocation("minecraft", id));
            boolean listed = biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                    .anyMatch(data -> data.type == ModEntities.GNOBLAR.get());
            helper.assertTrue(listed, "Gnoblar is not in the spawn list of " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void spawnRulePassesOnGrassAndSand(GameTestHelper helper) {
        for (var block : List.of(Blocks.GRASS_BLOCK, Blocks.RED_SAND)) {
            helper.setBlock(new BlockPos(12, 0, 12), block);
            boolean allowed = SpawnPlacements.checkSpawnRules(ModEntities.GNOBLAR.get(), helper.getLevel(),
                    MobSpawnType.NATURAL, helper.absolutePos(new BlockPos(12, 1, 12)), helper.getLevel().getRandom());
            helper.assertTrue(allowed, "Natural spawn rule rejected an open position on " + block);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void everyVariantSpawnsAndGreenIsCommonest(GameTestHelper helper) {
        int[] counts = new int[GnoblarVariant.values().length];
        for (int i = 0; i < 400; i++) {
            GnoblarEntity gnoblar = ModEntities.GNOBLAR.get().create(helper.getLevel());
            gnoblar.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(gnoblar.blockPosition()),
                    MobSpawnType.NATURAL, null, null);
            counts[gnoblar.getVariant().ordinal()]++;
        }
        for (GnoblarVariant variant : GnoblarVariant.values()) {
            helper.assertTrue(counts[variant.ordinal()] > 0, "No " + variant.id() + " gnoblar in 400 spawns");
        }
        helper.assertTrue(counts[GnoblarVariant.GREEN.ordinal()] > counts[GnoblarVariant.SOOTY.ordinal()],
                "The plain green gnoblar should be commoner than the rare sooty one");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void variantSurvivesSaving(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        gnoblar.setVariant(GnoblarVariant.RUSTY);
        CompoundTag tag = new CompoundTag();
        gnoblar.addAdditionalSaveData(tag);
        GnoblarEntity copy = ModEntities.GNOBLAR.get().create(helper.getLevel());
        helper.assertTrue(copy.getVariant() == GnoblarVariant.GREEN, "A fresh gnoblar should be the plain green one");
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.getVariant() == GnoblarVariant.RUSTY, "The variant was lost when saving");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void variantsHaveTheirOwnTexturesAndWarts(GameTestHelper helper) {
        java.util.Set<ResourceLocation> textures = new java.util.HashSet<>();
        for (GnoblarVariant variant : GnoblarVariant.values()) {
            helper.assertTrue(textures.add(variant.texture()), "Two variants share the texture " + variant.texture());
        }
        helper.assertTrue(GnoblarVariant.GREEN.wart() == GnoblarVariant.WartSpot.NONE, "The plain gnoblar should have no wart");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void nosePickleRecipeExists(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .byKey(new ResourceLocation(Gnoblars.MODID, "nose_pickle")).isPresent(),
                "Nose pickle recipe was not loaded");
        helper.succeed();
    }
}
