package com.redondoguibi.raydiance.client.render;

import com.mojang.blaze3d.vertex.*;
import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.Raydiance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.*;
import org.lwjgl.opengl.*;
import java.util.ArrayList;
import java.util.List;

/** Opt-in near-camera terrain shadows. Owns a bounded depth map, never re-enters LevelRenderer. */
public final class ShadowMapRenderer {
    private static GlProgram shader;
    private static GlTarget map;
    private static final Matrix4f lightView=new Matrix4f(),lightProjection=new Matrix4f(),transform=new Matrix4f();
    private static boolean ready,failed;
    private static final float EXTENT=48;
    private static final List<RenderSection> nearbySections = new ArrayList<>();
    public static void register(RegisterShadersEvent event){
        failed=false;ready=false;
        try{if(shader!=null)shader.close();shader=new GlProgram(GlProgram.resource("core/shadow.vsh"),GlProgram.resource("core/shadow.fsh"),true);}
        catch(Exception ex){shader=null;Raydiance.LOGGER.error("Projected shadows unavailable; using screen-space shadows",ex);}
    }
    public static void begin(){ready=false;}
    public static boolean ready(){return ready&&map!=null;}
    public static int texture(){return map.depth;}
    public static Matrix4f transform(){return transform;}
    /** Camera-relative world space to light view (Iris shadowModelView). */
    public static Matrix4f lightView(){return lightView;}
    public static Matrix4f lightProjection(){return lightProjection;}
    public static void render(List<RenderSection> sections,double x,double y,double z,Matrix4f cameraView){
        if(!Config.ENABLE_EFFECTS.get()||VisualPostProcessor.value("contactShadows")==0||!TerrainEffects.available()||shader==null||failed){release();return;}
        var mc=Minecraft.getInstance();
        if(!mc.level.dimensionType().hasSkyLight())return;
        float angle=mc.level.getSunAngle(0),sunX=-(float)java.lang.Math.sin(angle),sunY=(float)java.lang.Math.cos(angle);
        if(sunY<0.05f)return;
        int quality=com.redondoguibi.raydiance.client.pipeline.PipelineManager.quality();
        int resolution=quality==0?512:quality==1?1024:2048;
        GlState state = new GlState(1);
        try{
            if(map==null||map.width!=resolution){release();map=new GlTarget(resolution,resolution,true);}
            map.bind();GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LESS);GL11.glDepthMask(true);GL11.glColorMask(false,false,false,false);GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            lightView.identity().lookAt(sunX*128,sunY*128,0,0,0,0,0,0,1);
            lightProjection.identity().ortho(-EXTENT,EXTENT,-EXTENT,EXTENT,.1f,256);
            lightProjection.mul(lightView,transform).mul(new Matrix4f(cameraView).invert());
            int atlas=mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).getId();
            // Eligibility is the same for all three layers. Keep the original section order in each layer.
            for (RenderSection section : sections) {
                var origin = section.getOrigin();
                double dx = origin.getX()+8-x, dy = origin.getY()+8-y, dz = origin.getZ()+8-z;
                if (dx*dx+dy*dy+dz*dz <= 80*80) nearbySections.add(section);
            }
            shader.use();
            shader.matrix("ModelViewMat", lightView);
            shader.matrix("ProjMat", lightProjection);
            shader.texture("Sampler0", 0, atlas);
            int draws=0;
            for(RenderType layer:List.of(RenderType.solid(),RenderType.cutoutMipped(),RenderType.cutout())){
                shader.scalar("AlphaCutoff",layer==RenderType.solid()?0f:layer==RenderType.cutout()?0.1f:0.5f);
                for (RenderSection section : nearbySections) {
                    if (section.getCompiled().isEmpty(layer)) continue;
                    var origin = section.getOrigin();
                    // Bound work in very dense scenes. Incomplete maps are discarded, not displayed.
                    if(++draws>768){ready=false;return;}
                    shader.vec3("ChunkOffset",(float)(origin.getX()-x),(float)(origin.getY()-y),(float)(origin.getZ()-z));
                    var buffer=section.getBuffer(layer);buffer.bind();buffer.draw();
                }
            }
            ready=true;
        }catch(RuntimeException ex){failed=true;ready=false;Raydiance.LOGGER.error("Projected shadows disabled until resource reload",ex);}
        finally {
            nearbySections.clear();
            state.close();
        }
    }
    public static void shutdown(){release();if(shader!=null)shader.close();shader=null;}
    public static void release(){ready=false;if(map!=null)map.close();map=null;}
    private ShadowMapRenderer(){}
}
