package com.redondoguibi.raydiance.mixin;
import com.redondoguibi.raydiance.client.render.VisualPostProcessor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method="close",at=@At("HEAD"))
    private void raydiance$close(CallbackInfo ci){VisualPostProcessor.shutdown();}
}
