package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.Raydiance;
import com.redondoguibi.raydiance.client.pipeline.PipelineManager;
import com.redondoguibi.raydiance.client.pipeline.ShaderPipeline;
import com.redondoguibi.raydiance.client.shaders.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import org.lwjgl.opengl.*;

/** Raydiance's built-in OpenGL effects. Shaderpacks use DirectPipeline. */
public final class GlPipeline implements ShaderPipeline {
    private GlProgram composite, blur;
    private GlTarget result, bloomA, bloomB;
    private boolean failed;
    private int vao;
    private String error = "";

    public GlPipeline() throws java.io.IOException {
        composite = new GlProgram(GlProgram.resource("composite.fsh"));
        try {
            composite.use();
            FrameUniforms.prepare(composite, PackRepository.NONE);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    @Override public Kind kind() { return Kind.OPENGL; }
    @Override public PackRepository.Pack pack() { return PackRepository.NONE; }
    private static float value(String key) { return VisualPostProcessor.value(key); }

    @Override public void render(float partialTicks){
        var mc=Minecraft.getInstance();var level=mc.level;
        var target=mc.getMainRenderTarget();
        var grade=VisualSettings.current();
        boolean sky=level.dimensionType().hasSkyLight();
        boolean active = RenderPlan.post(true, grade.isNeutral(), false, value("bloom"),
                sky ? value("atmosphere") : 0, sky ? value("contactShadows") : 0,
                PipelineManager.fxaa(), value("ssao"),
                sky && mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.NONE ? value("godRays") : 0,
                value("sharpen"), value("toneMapping"));
        if (!active || failed) { releaseTargets(); return; }
        try (GlState state = new GlState(Math.max(1, FrameUniforms.textureUnits(composite)))) {
            if (vao == 0) vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            GlState.fullscreen();
            FrameUniforms.update(partialTicks);
            int world = target.getColorTextureId();
            if (result == null || result.width != target.width || result.height != target.height) {
                releaseTargets();
                result = new GlTarget(target.width, target.height, false);
            }
            renderBloom(world, target.width, target.height);
            result.bind();
            composite.use();
            FrameUniforms.bind(composite, new FrameUniforms.Pass(world, target.width, target.height, target.width, target.height, -1, null));
            composite.draw();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, result.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, result.width, result.height, 0, 0, target.width, target.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }catch(Exception ex){
            failed=true;error=String.valueOf(ex.getMessage());
            Raydiance.LOGGER.error("Raydiance OpenGL pipeline disabled until reload",ex);
        }
    }
    private void renderBloom(int input,int width,int height) throws java.io.IOException{
        if(value("bloom")<=0){if(bloomA!=null){bloomA.close();bloomB.close();bloomA=bloomB=null;}FrameUniforms.bloom(0);return;}
        if(blur==null)blur=new GlProgram(GlProgram.resource("bloom.fsh"));
        if(bloomA==null||bloomA.width!=Math.max(1,width/2)||bloomA.height!=Math.max(1,height/2)){
            if(bloomA!=null){bloomA.close();bloomB.close();}
            bloomA=new GlTarget(Math.max(1,width/2),Math.max(1,height/2),false);bloomB=new GlTarget(bloomA.width,bloomA.height,false);
        }
        blur.use();bloomA.bind();blur.texture("Scene",0,input);blur.vec2("Direction",1f/width,0);blur.scalar("Threshold",value("bloomThreshold"));blur.integer("Extract",1);blur.draw();
        bloomB.bind();blur.texture("Scene",0,bloomA.color);blur.vec2("Direction",0,1f/bloomA.height);blur.integer("Extract",0);blur.draw();
        FrameUniforms.bloom(bloomB.color);
    }
    @Override public void releaseTargets(){
        if(result!=null)result.close();if(bloomA!=null)bloomA.close();if(bloomB!=null)bloomB.close();result=bloomA=bloomB=null;
    }
    @Override public String status() {
        return failed ? "Standalone OpenGL: " + ShaderErrors.summary(error, 3) : "Standalone OpenGL";
    }

    @Override public void close() {
        releaseTargets();
        if (composite != null) composite.close();
        if (blur != null) blur.close();
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        composite = blur = null;
        vao = 0;
    }
}
