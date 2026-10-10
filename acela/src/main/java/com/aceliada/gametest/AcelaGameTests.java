package com.aceliada.gametest;

import com.aceliada.Aceliada;
import com.aceliada.entity.AcelaEntity;
import com.aceliada.entity.BoneSpikeEntity;
import com.aceliada.entity.goal.AcelaSpecialAttackGoal;
import com.aceliada.item.ZombieDrugItem;
import com.aceliada.registry.ModEffects;
import com.aceliada.registry.ModEntities;
import com.aceliada.registry.ModItems;
import com.aceliada.registry.ModSounds;
import com.aceliada.summon.AcelaSummoning;
import com.aceliada.summon.ArenaBuilder;
import com.aceliada.summon.SummonEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Run with: ./gradlew runGameTestServer */
@GameTestHolder(Aceliada.MODID)
@PrefixGameTestTemplate(false)
public class AcelaGameTests {
    private static final String TEMPLATE = "empty";

    /** Arenas are built this far from the test structures so they never overlap. */
    private static final int ARENA_OFFSET = 2000;

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static AcelaEntity fightingAcela(GameTestHelper helper, int x, int z) {
        AcelaEntity acela = helper.spawn(ModEntities.ACELA.get(), x, 2, z);
        acela.setTalkCooldown(1_000_000);
        return acela;
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE)
    public static void clockReadsThreeFiftyFive(GameTestHelper helper) {
        helper.assertTrue(SummonEvents.hour(0) == 6 && SummonEvents.minute(0) == 0, "Day time 0 is not 6:00");
        helper.assertTrue(SummonEvents.hour(18000) == 0, "Day time 18000 is not midnight");
        helper.assertTrue(SummonEvents.isSummonTime(21917), "21917 should read 3:55");
        helper.assertTrue(SummonEvents.isSummonTime(21933), "21933 should still read 3:55");
        helper.assertFalse(SummonEvents.isSummonTime(21916), "21916 reads 3:54");
        helper.assertFalse(SummonEvents.isSummonTime(21934), "21934 reads 3:56");
        helper.assertTrue(SummonEvents.isSummonTime(5 * 24000L + 21920), "3:55 on a later day is missed");
        helper.assertFalse(SummonEvents.isSummonTime(9917), "15:55 must not count");
        helper.succeed();
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE)
    public static void discIsAPlayableRecord(GameTestHelper helper) {
        helper.assertTrue(ModItems.LA_CRUCEA_DIN_MORMANT_DISC.get() instanceof RecordItem, "The disc is not a record");
        RecordItem disc = (RecordItem) ModItems.LA_CRUCEA_DIN_MORMANT_DISC.get();
        helper.assertTrue(disc.getLengthInTicks() == ModItems.DISC_LENGTH_TICKS, "Wrong disc length");
        helper.assertTrue(disc.getSound() == ModSounds.LA_CRUCEA_DIN_MORMANT.get(), "Wrong disc sound");

        BlockPos pos = new BlockPos(12, 2, 12);
        helper.setBlock(pos, Blocks.JUKEBOX);
        JukeboxBlockEntity jukebox = (JukeboxBlockEntity) helper.getBlockEntity(pos);
        jukebox.setItem(0, new ItemStack(disc));
        helper.assertTrue(SummonEvents.isPlayingTheDisc(jukebox), "A jukebox fed the disc is not recognised as playing it");
        jukebox.setItem(0, new ItemStack(Items.MUSIC_DISC_13));
        helper.assertFalse(SummonEvents.isPlayingTheDisc(jukebox), "Another disc must not summon Acela");
        helper.succeed();
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE, timeoutTicks = 400)
    public static void summoningBuildsDealulBohiiAndStartsTheIntro(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(ARENA_OFFSET, 80, ARENA_OFFSET));
        AcelaEntity acela = AcelaSummoning.summonAt(level, centre, List.of());
        helper.assertTrue(acela != null && acela.isIntro(), "Acela did not start in the intro");
        helper.assertTrue(acela.hasGlowingTag(), "Acela should glow while the players are blind");

        int top = ArenaBuilder.hillHeight(0, 0) + 1;
        for (int dy = 0; dy < 4; dy++) {
            helper.assertTrue(level.getBlockState(centre.offset(0, top + dy, 0)).is(Blocks.DARK_OAK_FENCE),
                    "The cross on the hill is missing");
        }
        BlockState heart = level.getBlockState(centre.offset(0, top + 2, 0));
        helper.assertTrue(heart.getValue(FenceBlock.EAST) && heart.getValue(FenceBlock.WEST),
                "The arms of the cross are not joined");
        int skulls = 0;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-7, 1, -7), centre.offset(7, 7, 7))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.SKELETON_SKULL) || s.is(Blocks.WITHER_SKELETON_SKULL)
                    || s.is(Blocks.SKELETON_WALL_SKULL) || s.is(Blocks.WITHER_SKELETON_WALL_SKULL)) {
                skulls++;
            }
        }
        helper.assertTrue(skulls >= 30, "The hill has only " + skulls + " skulls");
        helper.assertTrue(level.getBlockState(centre.offset(16, 3, 3)).is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                || level.getBlockState(centre.offset(16, 3, 3)).is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
                "No wall around the arena");
        helper.assertTrue(level.getBlockState(centre.offset(17, 3, 0)).is(Blocks.IRON_BARS), "No barred window");
        helper.assertTrue(level.getBlockState(centre.offset(5, 0, 12)).is(Blocks.BEDROCK)
                || !level.getBlockState(centre.offset(5, 0, 12)).isAir(), "No floor in the arena");

        // Intro: hits do nothing.
        Player player = helper.makeMockPlayer();
        acela.hurt(level.damageSources().playerAttack(player), 20.0F);
        helper.assertTrue(acela.getHealth() == acela.getMaxHealth(), "Acela was hurt during the intro");

        // Any answer starts the fight.
        acela.beginIntro(centre, List.of(player));
        helper.assertTrue(acela.onDialogueChoice(player, 2), "The answer was refused");
        helper.assertFalse(acela.isIntro(), "The fight did not start after the answer");
        helper.assertFalse(acela.hasGlowingTag(), "Acela still glows during the fight");
        helper.assertFalse(acela.onDialogueChoice(player, 1), "A second answer should be ignored");

        dumpArena(level, centre);
        acela.discardQuietly();
        helper.succeed();
    }

    /** Writes the arena's blocks for tools/render_arena.py when the gameTestServer run asks for it. */
    private static void dumpArena(ServerLevel level, BlockPos centre) {
        String target = System.getProperty("aceliada.arenaDump");
        if (target == null) {
            return;
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-19, -1, -19), centre.offset(19, 14, 19))) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) {
                continue;
            }
            json.append(first ? "" : ",").append("[").append(p.getX() - centre.getX()).append(',')
                    .append(p.getY() - centre.getY()).append(',').append(p.getZ() - centre.getZ()).append(",\"")
                    .append(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()).append("\",{");
            boolean firstProp = true;
            for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
                json.append(firstProp ? "" : ",").append('"').append(e.getKey().getName()).append("\":\"")
                        .append(e.getValue()).append('"');
                firstProp = false;
            }
            json.append("}]");
            first = false;
        }
        json.append("]");
        try {
            Path path = Path.of(target);
            Files.createDirectories(path.getParent());
            Files.writeString(path, json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void acelaDodgesSomeHits(GameTestHelper helper) {
        floor(helper);
        AcelaEntity acela = fightingAcela(helper, 12, 12);
        acela.setSpecialCooldown(1_000_000);
        Player player = helper.makeMockPlayer();
        int[] counts = new int[2]; // dodged, hit
        for (int i = 0; i < 60; i++) {
            helper.runAfterDelay(2 + i, () -> {
                acela.invulnerableTime = 0;
                acela.setHealth(acela.getMaxHealth());
                if (acela.hurt(acela.damageSources().playerAttack(player), 1.0F)) {
                    counts[1]++;
                } else {
                    counts[0]++;
                }
            });
        }
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(counts[0] > 0, "Acela never dodged in 60 hits");
            helper.assertTrue(counts[1] > 0, "Acela dodged every one of 60 hits");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE)
    public static void deathDropsDrogulZombie(GameTestHelper helper) {
        floor(helper);
        AcelaEntity acela = fightingAcela(helper, 12, 12);
        acela.hurt(acela.damageSources().genericKill(), Float.MAX_VALUE);
        helper.succeedWhen(() -> helper.assertItemEntityPresent(ModItems.DROGUL_ZOMBIE.get(), new BlockPos(12, 2, 12), 4.0D));
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE)
    public static void syringeGivesTheEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack stack = new ItemStack(ModItems.DROGUL_ZOMBIE.get(), 2);
        ItemStack left = stack.getItem().finishUsingItem(stack, helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(ModEffects.DROGUL_ZOMBIE.get()), "No Drogul Zombie effect");
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST), "No strength");
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "No speed");
        helper.assertTrue(player.getEffect(ModEffects.DROGUL_ZOMBIE.get()).getDuration() == ZombieDrugItem.DURATION,
                "Wrong duration");
        helper.assertTrue(left.getCount() == 1, "The syringe was not used up");
        helper.succeed();
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE, timeoutTicks = 300)
    public static void undeadIgnoreTheDrugged(GameTestHelper helper) {
        floor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 2, 12);
        villager.addEffect(new MobEffectInstance(ModEffects.DROGUL_ZOMBIE.get(), 20 * 60));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 12, 2, 16);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(zombie.getTarget() != villager, "The zombie went for a drugged villager");
            villager.removeAllEffects();
        });
        helper.runAfterDelay(101, () -> helper.succeedWhen(() ->
                helper.assertTrue(zombie.getTarget() == villager, "The zombie ignores a sober villager")));
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE)
    public static void boneSpikeHurts(GameTestHelper helper) {
        floor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 2, 12);
        BoneSpikeEntity spike = ModEntities.BONE_SPIKE.get().create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(12, 2, 12));
        spike.setup(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 3, null);
        helper.getLevel().addFreshEntity(spike);
        helper.succeedWhen(() -> helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "The bone did not hurt"));
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void boneCageTrapsTheTarget(GameTestHelper helper) {
        floor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 2, 18);
        AcelaEntity acela = fightingAcela(helper, 12, 4);
        acela.setTarget(villager);
        acela.setSpecialCooldown(0);
        acela.getSpecialAttackGoal().force(AcelaSpecialAttackGoal.Attack.BONE_CAGE);
        helper.succeedWhen(() -> helper.assertTrue(villager.getLastDamageSource() != null
                && villager.getLastDamageSource().is(DamageTypes.INDIRECT_MAGIC), "The bone cage did not hit"));
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void smokeBlastHurtsAndBlinds(GameTestHelper helper) {
        floor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 2, 18);
        AcelaEntity acela = fightingAcela(helper, 12, 6);
        acela.setTarget(villager);
        acela.setSpecialCooldown(0);
        acela.getSpecialAttackGoal().force(AcelaSpecialAttackGoal.Attack.SMOKE_BLAST);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.hasEffect(MobEffects.BLINDNESS), "The smoke did not blind");
            helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "The smoke did not hurt");
        });
    }

    @GameTest(templateNamespace = Aceliada.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void gravitySlamLiftsTheTarget(GameTestHelper helper) {
        floor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 2, 16);
        AcelaEntity acela = fightingAcela(helper, 12, 8);
        acela.setTarget(villager);
        acela.setSpecialCooldown(0);
        acela.getSpecialAttackGoal().force(AcelaSpecialAttackGoal.Attack.GRAVITY_SLAM);
        double startY = villager.getY();
        helper.succeedWhen(() -> helper.assertTrue(villager.getY() > startY + 2.0, "The target was not lifted"));
    }
}
