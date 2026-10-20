package com.laper.lhc.client;

import com.laper.lhc.LhcPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class LhcClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LhcPayload.TYPE, (payload, ctx) -> ClientFx.receive(payload));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("laperhardcore", "fx"), (graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            ClientFx.build(w, h);
            for (int[] r : ClientFx.RECTS) graphics.fill(r[0], r[1], r[2], r[3], r[4]);
            for (Object[] t : ClientFx.TEXTS) Txt.draw(graphics, (String) t[0], (int) t[1], (int) t[2], (int) t[3]);
        });
    }
}
