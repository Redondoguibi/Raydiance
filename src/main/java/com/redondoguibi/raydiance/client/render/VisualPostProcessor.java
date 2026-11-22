package com.redondoguibi.raydiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.client.pipeline.PipelineManager;
import com.redondoguibi.raydiance.client.profile.DynamicProfiles;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/**
 * Render-hook facade used by the mixins, terrain effects and menus. Rendering itself belongs to the active
 * {@link com.redondoguibi.raydiance.client.pipeline.ShaderPipeline}; this class owns frame setup and the
 * pre-translucent color/depth snapshot shared by GL water refraction and {@code depthtex1}.
 */
public final class VisualPostProcessor {
    private static GlTarget opaque;
    private static int opaqueFormat;
    private static boolean opaqueReady;

    public static float value(String key){return PipelineManager.standaloneActive() ? (float) DynamicProfiles.value(key) : 0;}
    public static void beginFrame(Matrix4f rotation, Matrix4f proj, float partialTicks) {
        FrameUniforms.beginFrame(rotation, proj);
        opaqueReady = false;
        PipelineManager.prepareFrame(partialTicks);
    }
    public static void idle(){
        if(opaque!=null||ShadowMapRenderer.ready())try(GlState state=new GlState()){releaseOpaque();ShadowMapRenderer.release();}
        PipelineManager.idle();
        FrameUniforms.history.reset();
    }
    public static boolean opaqueReady(){return opaqueReady&&opaque!=null;}
    public static int opaqueColor(){return opaque.color;}
    public static int opaqueDepth(){return opaque.depth;}
    public static Matrix4f projection(){return FrameUniforms.projection;}
    /** Before translucent terrain: snapshot only when GL water/reflections or a pack's depthtex1 needs it. */
    public static void captureOpaque(){
        if(PipelineManager.shadersRequested()){releaseOpaque();return;}
        boolean terrain=RenderPlan.opaque(Config.ENABLE_EFFECTS.get(),TerrainEffects.available(),value("water"),value("reflections"));
        if(!terrain){releaseOpaque();return;}
        var target=Minecraft.getInstance().getMainRenderTarget();
        try (GlState state = new GlState(1)) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getDepthTextureId());
            int format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
            if(opaque==null||opaque.width!=target.width||opaque.height!=target.height||opaqueFormat!=format){
                if(opaque!=null)opaque.close();opaque=new GlTarget(target.width,target.height,true,format);opaqueFormat=format;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,opaque.fbo);
            GL30.glBlitFramebuffer(0,0,target.width,target.height,0,0,target.width,target.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);opaqueReady=true;
        }catch(RuntimeException ex){opaqueReady=false;com.redondoguibi.raydiance.Raydiance.LOGGER.warn("Opaque snapshot failed",ex);}
    }
    public static void render(float partialTicks){
        if(Minecraft.getInstance().level==null)return;
        if(PipelineManager.shadersRequested()||!Config.ENABLE_EFFECTS.get()){
            if(opaque!=null||ShadowMapRenderer.ready())try(GlState state=new GlState()){releaseOpaque();ShadowMapRenderer.release();}
        }
        PipelineManager.render(partialTicks);
    }
    public static void reload(){reload(false);}
    public static void reload(boolean announce){
        Runnable task=()->{PipelineManager.reload(announce);try(GlState state=new GlState()){releaseOpaque();}PipelineManager.releaseTargets();FrameUniforms.history.reset();};
        if(RenderSystem.isOnRenderThread())task.run();else RenderSystem.recordRenderCall(task::run);
    }
    private static void releaseOpaque(){if(opaque!=null)opaque.close();opaque=null;opaqueReady=false;}
    public static void shutdown(){try(GlState state=new GlState()){
        releaseOpaque();ShadowMapRenderer.shutdown();PipelineManager.shutdown();FrameUniforms.shutdown();
    }}
    public static String diagnostic(){return PipelineManager.status();}
    private VisualPostProcessor(){}
}
