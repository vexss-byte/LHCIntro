package com.laper.lhc.mixin;

import com.laper.lhc.client.ClientFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 1)
    private void lhc$lockLook(double yRot, double xRot, CallbackInfo ci) {
        if (ClientFx.locked() && (Object) this == Minecraft.getInstance().player) ci.cancel();
    }
}
