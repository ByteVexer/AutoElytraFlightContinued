package me.ByteVexer.autoelytraflight;

import com.mojang.blaze3d.platform.InputConstants;
import me.ByteVexer.autoelytraflight.gui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Auto Elytra Flight Continued: hands-free elytra flight that gains altitude without fireworks.
 * One key: toggles auto flight while gliding, opens the settings otherwise.
 *
 * <p>A Fabric continuation of Cheaterpaul's AutoElytraFlight (NeoForge, GPL-3.0, last updated for
 * 1.21.4), which was itself a fork of simonlourson's original Fabric mod. Purely client-side: the only thing it
 * ever changes is the player's pitch, exactly as moving the mouse would.
 */
public final class AutoElytraFlightClient implements ClientModInitializer {

    public static final String MOD_ID = "autoelytraflightcontinued";

    @Override
    public void onInitializeClient() {
        FlightConfig.load();
        // Created after the load: the flight cycle keeps the config object it is given.
        FlightTracker tracker = new FlightTracker();

        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + MOD_ID + ".toggle",
                InputConstants.Type.KEYSYM, InputConstants.KEY_R,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

        // START, not END: the pitch has to be set before this tick's glide physics run.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            tracker.tick(mc);
            while (toggle.consumeClick()) {
                // As in the original mod: the same key flies in the air and configures on the ground.
                if (tracker.isGliding()) {
                    tracker.toggle();
                } else if (mc.player != null && mc.gui.screen() == null) {
                    mc.gui.setScreen(new SettingsScreen());
                }
            }
        });

        FlightHud.register(tracker);
    }
}
