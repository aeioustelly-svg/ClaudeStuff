package com.aceliada.registry;

import com.aceliada.Aceliada;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Aceliada.MODID);

    /** The disc track, played from a jukebox like any vanilla disc. */
    public static final RegistryObject<SoundEvent> LA_CRUCEA_DIN_MORMANT = register("music_disc.la_crucea_din_mormant");

    /** The same track, streamed over the arena while the fight lasts. */
    public static final RegistryObject<SoundEvent> BOSS_MUSIC = register("acela.boss_music");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Aceliada.MODID, name)));
    }
}
