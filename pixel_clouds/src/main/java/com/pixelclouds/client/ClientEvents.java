package com.pixelclouds.client;

import com.pixelclouds.PixelClouds;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = PixelClouds.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        /** Replaces the overworld's effects, which is where vanilla draws its clouds. */
        @SubscribeEvent
        public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new PixelCloudEffects());
        }
    }

    @Mod.EventBusSubscriber(modid = PixelClouds.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                CloudRenderer.INSTANCE.tick();
            }
        }

        @SubscribeEvent
        public static void levelUnload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                CloudRenderer.INSTANCE.reset();
            }
        }
    }
}
