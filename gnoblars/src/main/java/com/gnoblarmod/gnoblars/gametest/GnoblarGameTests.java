package com.gnoblarmod.gnoblars.gametest;

import com.gnoblarmod.gnoblars.Gnoblars;
import com.gnoblarmod.gnoblars.entity.GnoblarEntity;
import com.gnoblarmod.gnoblars.entity.GnoblarMode;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.gnoblarmod.gnoblars.entity.goal.GnoblarSleepGoal;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.DyeColor;
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
    public static void ownerCyclesFollowSitWander(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        tame(gnoblar, player);
        helper.assertTrue(gnoblar.getMode() == GnoblarMode.FOLLOW, "A new friend should follow");
        give(gnoblar, player, ItemStack.EMPTY);
        helper.assertTrue(gnoblar.getMode() == GnoblarMode.SIT && gnoblar.isOrderedToSit(), "First click should sit");
        give(gnoblar, player, ItemStack.EMPTY);
        helper.assertTrue(gnoblar.getMode() == GnoblarMode.WANDER && !gnoblar.isOrderedToSit(), "Second click should wander");
        helper.assertTrue(gnoblar.hasRestriction(), "A wandering friend should be tied to its spot");
        give(gnoblar, player, ItemStack.EMPTY);
        helper.assertTrue(gnoblar.getMode() == GnoblarMode.FOLLOW && !gnoblar.hasRestriction(), "Third click should follow again");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 600)
    public static void wanderingFriendStaysNearItsSpot(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        tame(gnoblar, player);
        give(gnoblar, player, ItemStack.EMPTY);   // sit
        give(gnoblar, player, ItemStack.EMPTY);   // wander
        net.minecraft.world.phys.Vec3 home = gnoblar.position();
        // a mock owner is not in the level, and vanilla's sit goal keeps a pet with a missing owner seated
        gnoblar.goalSelector.removeAllGoals(goal -> goal instanceof SitWhenOrderedToGoal);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(gnoblar.position().distanceTo(home) <= GnoblarEntity.WANDER_RADIUS + 2.0D,
                    "A wandering friend strayed too far from its spot");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void hurtSittingFriendGetsUpAndFollows(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        tame(gnoblar, player);
        gnoblar.setMode(GnoblarMode.SIT);
        gnoblar.hurt(helper.getLevel().damageSources().generic(), 1.0F);
        helper.assertTrue(gnoblar.getMode() == GnoblarMode.FOLLOW && !gnoblar.isOrderedToSit(), "A hurt friend should get up");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void modeSurvivesSaving(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        tame(gnoblar, player(helper));
        gnoblar.setMode(GnoblarMode.WANDER);
        CompoundTag tag = new CompoundTag();
        gnoblar.addAdditionalSaveData(tag);
        GnoblarEntity copy = ModEntities.GNOBLAR.get().create(helper.getLevel());
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.getMode() == GnoblarMode.WANDER && copy.hasRestriction(), "Wander mode was lost when saving");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void friendRidesOnOwnersBackAndIsPutDown(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player owner = player(helper);
        tame(gnoblar, owner);
        owner.setShiftKeyDown(true);
        give(gnoblar, owner, ItemStack.EMPTY);
        helper.assertTrue(gnoblar.getVehicle() == owner, "Sneaking and clicking should put the gnoblar on the owner's back");
        gnoblar.rideTick();
        helper.assertTrue(gnoblar.position().distanceTo(owner.position().add(0.0D, 0.9D, 0.0D)) < 0.6D,
                "A riding gnoblar should sit on the owner's back");
        helper.assertTrue(GnoblarEntity.putDownPassengers(owner), "There should be a gnoblar to put down");
        helper.assertTrue(!gnoblar.isPassenger(), "The gnoblar should be on the ground again");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void strangersCannotCarryAFriend(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player owner = player(helper);
        Player stranger = player(helper);
        tame(gnoblar, owner);
        stranger.setShiftKeyDown(true);
        give(gnoblar, stranger, ItemStack.EMPTY);
        helper.assertTrue(!gnoblar.isPassenger(), "A stranger picked up somebody else's gnoblar");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void campStructurePlacesWithItsFeatures(GameTestHelper helper) {
        var template = helper.getLevel().getStructureManager().get(new ResourceLocation(Gnoblars.MODID, "camp"))
                .orElse(null);
        helper.assertTrue(template != null, "The camp structure file was not found");
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        boolean placed = template.placeInWorld(helper.getLevel(), origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                helper.getLevel().getRandom(), 2);
        helper.assertTrue(placed, "The camp could not be placed");
        int campfires = 0, cauldrons = 0, lootContainers = 0, totems = 0;
        var size = template.getSize();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            var state = helper.getLevel().getBlockState(pos);
            if (state.is(Blocks.CAMPFIRE)) campfires++;
            if (state.is(Blocks.WATER_CAULDRON)) cauldrons++;
            if (state.is(Blocks.CARVED_PUMPKIN)) totems++;
            var entity = helper.getLevel().getBlockEntity(pos);
            if (entity != null && entity.saveWithFullMetadata().getString("LootTable").equals("gnoblars:chests/camp")) {
                lootContainers++;
            }
        }
        helper.assertTrue(campfires == 1 && cauldrons == 1, "The camp should have one fire with one cooking pot");
        helper.assertTrue(totems == 1, "The camp should have its pumpkin totem");
        int beds = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (GnoblarSleepGoal.isBed(helper.getLevel(), pos)) beds++;
        }
        helper.assertTrue(beds >= 2, "The camp should have gnoblar beds, found " + beds);
        helper.assertTrue(lootContainers >= 2, "The camp should have loot containers, found " + lootContainers);
        var residents = helper.getLevel().getEntitiesOfClass(GnoblarEntity.class,
                new net.minecraft.world.phys.AABB(origin, origin.offset(size.getX(), size.getY(), size.getZ())));
        helper.assertTrue(residents.size() >= 4, "The camp should have gnoblars living in it, found " + residents.size());
        helper.assertTrue(residents.stream().map(GnoblarEntity::getVariant).distinct().count() >= 3,
                "The residents should look different from each other");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void campLootIsPeacefulAndNeverEmpty(GameTestHelper helper) {
        var table = helper.getLevel().getServer().getLootData().getLootTable(new ResourceLocation(Gnoblars.MODID, "chests/camp"));
        LootParams params = new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
        for (int i = 0; i < 30; i++) {
            helper.assertTrue(!table.getRandomItems(params).isEmpty(), "A camp chest rolled nothing");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void campStructureGeneratesAStart(GameTestHelper helper) {
        var level = helper.getLevel();
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(new ResourceLocation(Gnoblars.MODID, "gnoblar_camp"));
        helper.assertTrue(structure != null, "The camp structure is not registered");
        var generator = level.getChunkSource().getGenerator();
        var start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                new net.minecraft.world.level.ChunkPos(40, 40), 0, level, biome -> true);
        helper.assertTrue(start.isValid(), "The camp did not produce a structure start (the jigsaw set-up is wrong)");
        helper.assertTrue(start.getPieces().size() == 1, "The camp should be a single piece, found " + start.getPieces().size());
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void campIsRegisteredForWorldGeneration(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(new ResourceLocation(Gnoblars.MODID, "gnoblar_camp")),
                "The camp structure is not registered");
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(new ResourceLocation(Gnoblars.MODID, "gnoblar_camps")),
                "The camp structure set is not registered");
        helper.assertTrue(access.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(new ResourceLocation(Gnoblars.MODID, "camp/start")),
                "The camp template pool is not registered");
        var tag = net.minecraft.tags.TagKey.create(Registries.BIOME, new ResourceLocation(Gnoblars.MODID, "has_structure/gnoblar_camp"));
        var biomes = access.registryOrThrow(Registries.BIOME);
        helper.assertTrue(biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                new ResourceLocation("minecraft", "swamp"))).is(tag), "Swamps should be able to hold camps");
        helper.assertTrue(!biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                new ResourceLocation("minecraft", "plains"))).is(tag), "Plains should not hold camps");
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
                {
            helper.assertTrue(gnoblar.getSniffsCompleted() > 0, "Friendly gnoblar never finished sniffing");
            helper.assertTrue(gnoblar.isMuddy(), "Digging should leave a gnoblar muddy");
        });
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

    // ---- kindness: brush, wash, dye, banner hat, bed, dance, cake ------------------------------------------

    private static ItemStack waterBottle() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void brushingWinsTrustOnceInAWhile(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        ItemStack brush = new ItemStack(Items.BRUSH);
        give(gnoblar, player, brush);
        helper.assertTrue(gnoblar.getTrust() == 1, "A brushing should earn a point of trust");
        helper.assertTrue(brush.getDamageValue() == 1, "Brushing should wear the brush");
        give(gnoblar, player, brush);
        helper.assertTrue(gnoblar.getTrust() == 1, "A second brushing straight away should earn nothing");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void brushingCanBefriendAGnoblar(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        gnoblar.setTrust(GnoblarEntity.TAME_TRUST - 1);
        give(gnoblar, player, new ItemStack(Items.BRUSH));
        helper.assertTrue(gnoblar.isTame() && gnoblar.isOwnedBy(player), "The last point of trust should befriend it");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void washingNeedsMudAndReturnsTheBottle(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player player = player(helper);
        ItemStack clean = waterBottle();
        helper.assertTrue(give(gnoblar, player, clean) == InteractionResult.PASS && clean.getCount() == 1,
                "A clean gnoblar should not take a bath");
        gnoblar.setMuddy(true);
        give(gnoblar, player, waterBottle());
        helper.assertTrue(!gnoblar.isMuddy(), "The mud should be gone");
        helper.assertTrue(player.getMainHandItem().is(Items.GLASS_BOTTLE), "The empty bottle should come back");
        helper.assertTrue(gnoblar.getTrust() == 1, "A wash should earn a point of trust");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 800)
    public static void mudMakesAGnoblarMuddy(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, -1, 12), Blocks.MUD);
        GnoblarEntity gnoblar = spawn(helper);
        gnoblar.goalSelector.removeAllGoals(goal -> true);
        helper.succeedWhen(() -> helper.assertTrue(gnoblar.isMuddy(), "Standing in mud should make a gnoblar muddy"));
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void ownersDyeTheSash(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player owner = player(helper);
        Player stranger = player(helper);
        tame(gnoblar, owner);
        ItemStack dye = new ItemStack(Items.RED_DYE, 2);
        give(gnoblar, stranger, dye);
        helper.assertTrue(gnoblar.getSashColor() == null && dye.getCount() == 2, "A stranger dyed somebody else's gnoblar");
        give(gnoblar, owner, dye);
        helper.assertTrue(gnoblar.getSashColor() == DyeColor.RED && dye.getCount() == 1, "The owner's dye should colour the sash");
        helper.assertTrue(give(gnoblar, owner, dye) == InteractionResult.PASS, "The same dye twice should change nothing");
        give(gnoblar, owner, new ItemStack(Items.BLUE_DYE));
        helper.assertTrue(gnoblar.getSashColor() == DyeColor.BLUE, "A different dye should recolour it");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void bannersMakeHatsAndShearsTakeThemOff(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        Player owner = player(helper);
        tame(gnoblar, owner);
        ItemStack white = new ItemStack(Items.WHITE_BANNER);
        give(gnoblar, owner, white);
        helper.assertTrue(gnoblar.getItemBySlot(EquipmentSlot.HEAD).is(Items.WHITE_BANNER) && white.isEmpty(),
                "The banner should go on its head");
        give(gnoblar, owner, new ItemStack(Items.RED_BANNER));
        helper.assertTrue(gnoblar.getItemBySlot(EquipmentSlot.HEAD).is(Items.RED_BANNER), "A second banner should replace the first");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.WHITE_BANNER)), "The first banner should go back to the owner");
        give(gnoblar, owner, new ItemStack(Items.SHEARS));
        helper.assertTrue(gnoblar.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Shears should take the hat off");
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.RED_BANNER, new BlockPos(12, 1, 12), 4.0D));
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void aBedIsHayWithACarpetOnTop(GameTestHelper helper) {
        BlockPos hay = new BlockPos(14, 0, 12);
        helper.setBlock(hay, Blocks.HAY_BLOCK);
        helper.assertTrue(!GnoblarSleepGoal.isBed(helper.getLevel(), helper.absolutePos(hay)), "Hay alone is not a bed");
        helper.setBlock(hay.above(), Blocks.RED_CARPET);
        helper.assertTrue(GnoblarSleepGoal.isBed(helper.getLevel(), helper.absolutePos(hay)), "Hay with a carpet is a bed");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 2400)
    public static void gnoblarsSleepOnABedAtNight(GameTestHelper helper) {
        // tests run side by side in one world, so put the clock back as soon as this one has what it needs
        long before = helper.getLevel().getDayTime();
        helper.getLevel().setDayTime(18000L);
        helper.setBlock(new BlockPos(14, 0, 12), Blocks.HAY_BLOCK);
        helper.setBlock(new BlockPos(14, 1, 12), Blocks.RED_CARPET);
        GnoblarEntity gnoblar = spawn(helper);
        BlockPos bed = helper.absolutePos(new BlockPos(14, 0, 12));
        helper.succeedWhen(() -> {
            helper.assertTrue(gnoblar.isNapping(), "The gnoblar should sleep on the bed at night");
            helper.assertTrue(gnoblar.getY() > bed.getY() + 1.0D && Math.abs(gnoblar.getX() - (bed.getX() + 0.5D)) < 0.6D,
                    "The gnoblar should lie on top of the bed");
            helper.getLevel().setDayTime(before);
        });
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void nightRunsFromDuskToJustBeforeDawn(GameTestHelper helper) {
        helper.assertTrue(!GnoblarSleepGoal.isNight(2000L) && !GnoblarSleepGoal.isNight(12000L), "Day is not night");
        helper.assertTrue(GnoblarSleepGoal.isNight(13000L) && GnoblarSleepGoal.isNight(18000L), "Midnight is night");
        helper.assertTrue(!GnoblarSleepGoal.isNight(23500L), "The morning is not night");
        helper.assertTrue(GnoblarSleepGoal.isNight(24000L * 5 + 18000L), "Later days have nights too");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE, timeoutTicks = 900)
    public static void aCakeStartsAParty(GameTestHelper helper) {
        BlockPos cake = new BlockPos(15, 0, 12);
        helper.setBlock(cake, Blocks.CAKE);
        GnoblarEntity eater = spawn(helper);
        GnoblarEntity bystander = helper.spawn(ModEntities.GNOBLAR.get(), 12, 1, 15);
        helper.succeedWhen(() -> {
            var state = helper.getBlockState(cake);
            helper.assertTrue(!state.is(Blocks.CAKE) || state.getValue(net.minecraft.world.level.block.CakeBlock.BITES) > 0,
                    "Nobody took a bite of the cake");
            helper.assertTrue(eater.isDancing() || bystander.isDancing(), "The cake should start a party");
            helper.assertTrue(eater.getTrust() > 0 || bystander.getTrust() > 0, "The party should win some trust");
        });
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void aCakeCannotBefriendWithoutAPlayer(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        gnoblar.setTrust(GnoblarEntity.TAME_TRUST - 1);
        gnoblar.celebrateCake(helper.absolutePos(new BlockPos(15, 0, 12)));
        helper.assertTrue(!gnoblar.isTame() && gnoblar.getTrust() == GnoblarEntity.TAME_TRUST - 1,
                "Only a player's own gesture should be able to tame a gnoblar");
        helper.succeed();
    }

    @GameTest(templateNamespace = Gnoblars.MODID, template = TEMPLATE)
    public static void kindnessSurvivesSaving(GameTestHelper helper) {
        GnoblarEntity gnoblar = spawn(helper);
        gnoblar.setMuddy(true);
        gnoblar.setSashColor(DyeColor.PURPLE);
        gnoblar.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GREEN_BANNER));
        CompoundTag tag = new CompoundTag();
        gnoblar.addAdditionalSaveData(tag);
        GnoblarEntity copy = ModEntities.GNOBLAR.get().create(helper.getLevel());
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.isMuddy() && copy.getSashColor() == DyeColor.PURPLE, "Mud or the dye was lost when saving");
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
