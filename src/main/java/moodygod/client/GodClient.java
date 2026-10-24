package moodygod.client;

import moodygod.FxPayload;
import moodygod.GodMod;
import moodygod.GodPayload;
import moodygod.TimerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class GodClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(GodPayload.TYPE, (payload, ctx) -> GodHud.enqueue(payload.text(), payload.mood()));
        ClientPlayNetworking.registerGlobalReceiver(TimerPayload.TYPE, (payload, ctx) -> GodHud.setTimer(payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, ctx) -> GodHud.fx(payload.op(), payload.a(), payload.n()));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(GodMod.ID, "god_hud"), GodHud::render);
    }
}
