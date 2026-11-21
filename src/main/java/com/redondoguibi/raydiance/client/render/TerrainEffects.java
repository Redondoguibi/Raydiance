package com.redondoguibi.raydiance.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.redondoguibi.raydiance.*;
import com.redondoguibi.raydiance.compat.direct.DirectCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import java.util.*;

/** Optional vanilla terrain shader. Unknown/alternate renderers retain their own shaders. */
public final class TerrainEffects {
    private static ShaderInstance shader;
    private static boolean conflict;
    private static boolean nativeTerrain;
    public static void tick(){nativeTerrain=DirectCompatibility.probe().nativeTerrain();}
    private static List<float[]> plants=List.of();
    private static final ResourceLocation STILL=ResourceLocation.withDefaultNamespace("block/water_still"),FLOW=ResourceLocation.withDefaultNamespace("block/water_flow");
    public static void register(RegisterShadersEvent event){
        conflict=List.of("iris","oculus","embeddium","sodium","optifine").stream().anyMatch(id->ModList.get().isLoaded(id));
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("raydiance","terrain"),DefaultVertexFormat.BLOCK),s->shader=s);}
        catch(Exception ex){shader=null;Raydiance.LOGGER.error("Optional terrain effects unavailable",ex);}
        plants=List.of();
    }
    public static boolean available(){return !com.redondoguibi.raydiance.client.pipeline.PipelineManager.directActive()&&shader!=null&&!conflict&&!nativeTerrain;}
    public static String status(){return nativeTerrain?(com.redondoguibi.raydiance.compat.direct.DirectShaderBridge.nativeGeometryActive()?"Direct geometry effects: DX12":"Direct terrain: geometry effects not requested"):conflict?"Alternate terrain renderer: water/wind disabled":shader==null?"Terrain shader unavailable":"Vanilla terrain effects ready (OpenGL)";}
    private static float[] rect(net.minecraft.client.renderer.texture.TextureAtlasSprite s){return new float[]{s.getU0(),s.getV0(),s.getU1(),s.getV1()};}
    private static void rect(String name,float[] values){var u=shader.getUniform(name);if(u!=null)u.set(values);}
    public static ShaderInstance select(ShaderInstance original,RenderType layer){
        if(!Config.ENABLE_EFFECTS.get()||!available())return original;
        boolean water=layer==RenderType.translucent(),plant=layer==RenderType.cutout()||layer==RenderType.cutoutMipped();
        String expected=water?"rendertype_translucent":layer==RenderType.cutout()?"rendertype_cutout":"rendertype_cutout_mipped";
        if(!original.getName().equals(expected)||(!water&&!plant))return original;
        float wind=VisualPostProcessor.value("wind"),wet=VisualPostProcessor.value("water"),reflection=VisualPostProcessor.value("reflections");
        if(water?(wet==0&&reflection==0):wind==0)return original;
        var mc=Minecraft.getInstance();var atlas=mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        rect("WaterStill",rect(atlas.getSprite(STILL)));rect("WaterFlow",rect(atlas.getSprite(FLOW)));
        if(plants.isEmpty()){
            List<float[]> found=new ArrayList<>();
            for(String id:List.of("short_grass","tall_grass_bottom","tall_grass_top","fern","large_fern_bottom","large_fern_top","oak_leaves","birch_leaves","spruce_leaves","jungle_leaves","acacia_leaves","dark_oak_leaves","mangrove_leaves","cherry_leaves","vine","wheat_stage7"))
                found.add(rect(atlas.getSprite(ResourceLocation.withDefaultNamespace("block/"+id))));
            plants=List.copyOf(found);
        }
        for(int i=0;i<plants.size();i++)rect("Plant"+i,plants.get(i));
        float t=(mc.level.getGameTime()%240000)/20f;
        shader.safeGetUniform("Time").set(t);shader.safeGetUniform("Wind").set(water?0:wind);
        shader.safeGetUniform("Water").set(water?wet:0);shader.safeGetUniform("Reflections").set(water?reflection:0);
        shader.safeGetUniform("AlphaCutoff").set(water?0f:layer==RenderType.cutout()?0.1f:0.5f);
        shader.safeGetUniform("Camera").set((float)(mc.gameRenderer.getMainCamera().getPosition().x%4096),
                (float)(mc.gameRenderer.getMainCamera().getPosition().y%4096),(float)(mc.gameRenderer.getMainCamera().getPosition().z%4096));
        shader.safeGetUniform("InverseProj").set(new Matrix4f(VisualPostProcessor.projection()).invert());
        shader.safeGetUniform("ScreenSize").set((float)mc.getMainRenderTarget().width,(float)mc.getMainRenderTarget().height);
        shader.safeGetUniform("SceneReady").set(VisualPostProcessor.opaqueReady()?1f:0f);
        // ShaderInstance retains sampler IDs across frames. After reload/resize, never bind deleted snapshots
        // in the cutout layers that run before this frame's opaque capture.
        shader.setSampler("RayScene",VisualPostProcessor.opaqueReady()?VisualPostProcessor.opaqueColor():atlas.getId());
        shader.setSampler("RayDepth",VisualPostProcessor.opaqueReady()?VisualPostProcessor.opaqueDepth():atlas.getId());
        return shader;
    }
    private TerrainEffects(){}
}
