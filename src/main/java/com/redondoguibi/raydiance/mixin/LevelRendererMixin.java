package com.redondoguibi.raydiance.mixin;
import com.redondoguibi.raydiance.client.render.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection> visibleSections;
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void raydiance$begin(DeltaTracker dt,boolean outline,Camera camera,GameRenderer game,LightTexture light,Matrix4f view,Matrix4f projection,CallbackInfo ci){VisualPostProcessor.beginFrame(view,projection,dt.getGameTimeDeltaPartialTick(false));ShadowMapRenderer.begin();}
    @Inject(method="renderLevel",at=@At("TAIL"))
    private void raydiance$finish(DeltaTracker dt,boolean outline,Camera camera,GameRenderer game,LightTexture light,Matrix4f view,Matrix4f projection,CallbackInfo ci){VisualPostProcessor.render(dt.getGameTimeDeltaPartialTick(false));}
    @Inject(method="renderSectionLayer",at=@At("HEAD"),require=0)
    private void raydiance$opaque(RenderType layer,double x,double y,double z,Matrix4f view,Matrix4f projection,CallbackInfo ci){if(layer==RenderType.solid())ShadowMapRenderer.render(visibleSections,x,y,z,view);if(layer==RenderType.translucent())VisualPostProcessor.captureOpaque();}
    @ModifyExpressionValue(method="renderSectionLayer",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;getShader()Lnet/minecraft/client/renderer/ShaderInstance;"),require=0)
    private ShaderInstance raydiance$terrain(ShaderInstance original,RenderType layer,double x,double y,double z,Matrix4f view,Matrix4f projection){return TerrainEffects.select(original,layer);}
}
