package com.redondoguibi.raydiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redondoguibi.raydiance.*;
import com.redondoguibi.raydiance.client.profile.DynamicProfiles;
import com.redondoguibi.raydiance.client.shaders.PackRepository;
import com.redondoguibi.raydiance.client.shaders.PackOptionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import java.util.Objects;

/** Fused built-in effects plus optional bounded format-2 post passes. */
public final class VisualPostProcessor {
    private static GlProgram composite,blur;
    private static PostPassChain chain;
    private static GlTarget result,bloomA,bloomB,opaque;
    private static int vao,opaqueFormat;
    private static String attempted,error="";
    private static boolean failed,opaqueReady,resultFloating;
    private static final Matrix4f projection=new Matrix4f(),view=new Matrix4f(),inverseProjection=new Matrix4f(),inverseView=new Matrix4f();
    private static final FrameHistory history=new FrameHistory();

    public static float value(String key){return Config.ENABLE_EFFECTS.get()?(float)DynamicProfiles.value(key):0;}
    public static void beginFrame(Matrix4f rotation,Matrix4f proj){
        view.set(rotation);projection.set(proj);inverseProjection.set(proj).invert();inverseView.set(rotation).invert();opaqueReady=false;
        var mc=Minecraft.getInstance();var position=mc.gameRenderer.getMainCamera().getPosition();
        history.update(mc.level,System.nanoTime(),proj,rotation,new Vector3d(position.x,position.y,position.z),mc.level.getRainLevel(0));
    }
    public static void idle(){
        if(result!=null||opaque!=null||ShadowMapRenderer.ready()||(chain!=null&&!chain.empty())){
            try(GlState state=new GlState()){releaseTargets();ShadowMapRenderer.release();}
        }
        history.reset();
    }
    public static boolean opaqueReady(){return opaqueReady&&opaque!=null;}
    public static int opaqueColor(){return opaque.color;}
    public static int opaqueDepth(){return opaque.depth;}
    public static Matrix4f projection(){return projection;}
    public static void captureOpaque(){
        if(!RenderPlan.opaque(Config.ENABLE_EFFECTS.get(),TerrainEffects.available(),value("water"),value("reflections"))){
            if(opaque!=null){opaque.close();opaque=null;}return;
        }
        var target=Minecraft.getInstance().getMainRenderTarget();
        try(GlState state=new GlState()){
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getDepthTextureId());
            int format=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
            if(opaque==null||opaque.width!=target.width||opaque.height!=target.height||opaqueFormat!=format){
                if(opaque!=null)opaque.close();opaque=new GlTarget(target.width,target.height,true,format);opaqueFormat=format;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,opaque.fbo);
            GL30.glBlitFramebuffer(0,0,target.width,target.height,0,0,target.width,target.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);opaqueReady=true;
        }catch(RuntimeException ex){opaqueReady=false;error=ex.getMessage();}
    }
    private static void updatePack(int width,int height){
        String requested=Config.OPENGL_PACK.get();
        if(Objects.equals(attempted,requested))return;
        attempted=requested;
        GlProgram candidate=null;PostPassChain candidateChain=null;
        try{
            var pack=PackRepository.requested();
            if(PackRepository.targetBytes(pack.passes(),width,height)>PostPassChain.MAX_TARGET_BYTES)throw new IllegalArgumentException("Pack targets exceed 128 MiB");
            candidate=new GlProgram(pack.composite()==null?GlProgram.resource("composite.fsh"):pack.composite());
            candidateChain=new PostPassChain(pack.passes());
            var settingUniforms=pack.shaderOptions().stream().map(o->o.uniform()).toList();
            candidate.validateScalars(settingUniforms);candidateChain.validateScalars(settingUniforms);
            for(String uniform:settingUniforms)if(!candidate.has(uniform)&&!candidateChain.hasUniform(uniform))
                throw new IllegalArgumentException("Shader setting has no active uniform: "+uniform);
            if(!pack.shaderOptions().isEmpty())PackOptionStore.live().prepare(pack);
            if(composite!=null)composite.close();if(chain!=null)chain.close();
            composite=candidate;chain=candidateChain;candidate=null;candidateChain=null;
            PackRepository.accept(pack);failed=false;error="";
        }catch(Exception ex){
            if(candidate!=null)candidate.close();if(candidateChain!=null)candidateChain.close();
            PackRepository.reject(ex);Raydiance.LOGGER.error("Rejected Raydiance pack; keeping previous pipeline",ex);
        }
    }
    public static void render(float partialTicks){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(!Config.ENABLE_EFFECTS.get()){releaseTargets();ShadowMapRenderer.release();return;}
        var target=mc.getMainRenderTarget();
        try(GlState state=new GlState()){
            updatePack(target.width,target.height);
            if(composite==null)composite=new GlProgram(GlProgram.resource("composite.fsh"));
            var grade=VisualSettings.current();
            boolean custom=PackRepository.active().composite()!=null||(chain!=null&&chain.active(DynamicProfiles::value));
            boolean post=RenderPlan.post(true,grade.isNeutral(),custom,value("bloom"),value("atmosphere"),com.redondoguibi.raydiance.compat.direct.DirectShaderBridge.nativeGeometryActive()?0:value("contactShadows"),Config.FXAA.get(),
                    value("ssao"),value("godRays"),value("sharpen"),value("toneMapping"));
            if(!post||failed){releasePostTargets();return;}
            if(vao==0)vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);GlState.fullscreen();
            boolean floating=chain!=null&&chain.active(DynamicProfiles::value);
            if(result==null||result.width!=target.width||result.height!=target.height||resultFloating!=floating){
                releasePostTargets();result=new GlTarget(target.width,target.height,false,0,floating);resultFloating=floating;
            }
            renderBloom(target.getColorTextureId(),target.width,target.height);
            result.bind();composite.use();
            bindUniforms(composite,target.getColorTextureId(),target.width,target.height,-1,target.width,target.height,partialTicks,grade);
            composite.draw();
            GlTarget output=result;
            if(chain!=null&&chain.active(DynamicProfiles::value)){
                var rendered=chain.render(result.color,target.width,target.height,
                        (program,input,iw,ih,index,w,h)->bindUniforms(program,input,iw,ih,index,w,h,partialTicks,grade),DynamicProfiles::value);
                if(rendered!=null)output=rendered;
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,output.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);
            GL30.glBlitFramebuffer(0,0,output.width,output.height,0,0,target.width,target.height,GL11.GL_COLOR_BUFFER_BIT,
                    output.width==target.width&&output.height==target.height?GL11.GL_NEAREST:GL11.GL_LINEAR);
        }catch(Exception ex){failed=true;error=ex.getMessage();Raydiance.LOGGER.error("Raydiance composite disabled until reload",ex);}
    }
    private static void renderBloom(int input,int width,int height) throws java.io.IOException{
        if(value("bloom")<=0){if(bloomA!=null){bloomA.close();bloomB.close();bloomA=bloomB=null;}return;}
        if(blur==null)blur=new GlProgram(GlProgram.resource("bloom.fsh"));
        if(bloomA==null){bloomA=new GlTarget(Math.max(1,width/2),Math.max(1,height/2),false);bloomB=new GlTarget(bloomA.width,bloomA.height,false);}
        blur.use();bloomA.bind();blur.texture("Scene",0,input);blur.vec2("Direction",1f/width,0);blur.scalar("Threshold",value("bloomThreshold"));blur.integer("Extract",1);blur.draw();
        bloomB.bind();blur.texture("Scene",0,bloomA.color);blur.vec2("Direction",0,1f/bloomA.height);blur.integer("Extract",0);blur.draw();
    }
    private static void bindUniforms(GlProgram program,int input,int inputWidth,int inputHeight,int passIndex,int width,int height,float partialTicks,VisualSettings.Grade grade){
        var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
        program.texture("Scene",0,input);program.texture("Depth",1,target.getDepthTextureId());
        program.texture("BloomTex",2,bloomB==null?target.getColorTextureId():bloomB.color);
        program.texture("ShadowTex",3,ShadowMapRenderer.ready()?ShadowMapRenderer.texture():target.getDepthTextureId());
        program.texture("OriginalScene",4,target.getColorTextureId());
        program.scalar("ShadowReady",ShadowMapRenderer.ready()?1:0);program.matrix("ShadowTransform",ShadowMapRenderer.transform());
        program.vec2("Texel",1f/inputWidth,1f/inputHeight);program.vec2("InputSize",inputWidth,inputHeight);program.vec2("OutputSize",width,height);program.vec2("ViewSize",target.width,target.height);
        program.integer("PassIndex",passIndex);program.integer("FrameCounter",history.counter);program.scalar("FrameTime",history.delta);program.scalar("FrameTimeCounter",history.seconds);program.integer("HistoryValid",history.valid?1:0);
        program.integer("WorldTime",(int)Math.floorMod(mc.level.getDayTime(),24000));program.integer("WorldDay",(int)Math.floorMod(mc.level.getDayTime()/24000,2147483647));
        program.scalar("Wetness",history.wetness);program.scalar("Thunder",mc.level.getThunderLevel(partialTicks));
        var camera=mc.gameRenderer.getMainCamera();var fluid=camera.getFluidInCamera();
        program.integer("EyeInWater",fluid==FogType.WATER?1:fluid==FogType.LAVA?2:fluid==FogType.POWDER_SNOW?3:0);
        if(program.has("SkyColor")){var sky=mc.level.getSkyColor(camera.getPosition(),partialTicks);program.vec3("SkyColor",(float)sky.x,(float)sky.y,(float)sky.z);}
        if(program.has("EyeBrightness")){var block=camera.getBlockPosition();program.vec2("EyeBrightness",mc.level.getBrightness(LightLayer.BLOCK,block)/15f,mc.level.getBrightness(LightLayer.SKY,block)/15f);}
        var position=history.camera;program.vec3("CameraPosition",(float)position.x,(float)position.y,(float)position.z);
        int bx=(int)Math.floor(position.x),by=(int)Math.floor(position.y),bz=(int)Math.floor(position.z);
        program.ivec3("CameraBlock",bx,by,bz);program.vec3("CameraFraction",(float)(position.x-bx),(float)(position.y-by),(float)(position.z-bz));
        program.vec3("CameraDelta",(float)history.cameraDelta.x,(float)history.cameraDelta.y,(float)history.cameraDelta.z);
        program.matrix("View",view);program.matrix("InvView",inverseView);program.matrix("PreviousView",history.previousView);program.matrix("PreviousProjection",history.previousProjection);
        program.matrix("Projection",projection);program.matrix("InvProjection",inverseProjection);
        program.scalar("Saturation",grade.saturation());program.scalar("Contrast",grade.contrast());program.scalar("Brightness",grade.brightness());program.scalar("Exposure",grade.exposure());
        program.scalar("Gamma",grade.gamma());program.scalar("Temperature",grade.temperature());program.scalar("Vignette",grade.vignette());program.scalar("Strength",grade.strength());
        program.scalar("Bloom",value("bloom"));program.scalar("Atmosphere",mc.level.dimensionType().hasSkyLight()?value("atmosphere"):0);
        program.scalar("Shadows",mc.level.dimensionType().hasSkyLight()&&!com.redondoguibi.raydiance.compat.direct.DirectShaderBridge.nativeGeometryActive()?value("contactShadows"):0);
        program.scalar("Ssao",value("ssao"));program.scalar("GodRays",mc.level.dimensionType().hasSkyLight()&&fluid==FogType.NONE?value("godRays"):0);
        program.scalar("Sharpen",value("sharpen"));program.scalar("ToneMapping",value("toneMapping"));
        program.integer("Fxaa",Config.FXAA.get()?1:0);program.integer("Steps",Config.QUALITY.get()==0?6:Config.QUALITY.get()==1?12:20);
        float angle=mc.level.getSunAngle(partialTicks);Vector3f sun=new Vector3f(-(float)Math.sin(angle),(float)Math.cos(angle),0);view.transformDirection(sun);
        program.vec3("Sun",sun.x,sun.y,sun.z);program.scalar("Daylight",Math.max(0,(float)Math.cos(angle)));program.scalar("Rain",mc.level.getRainLevel(partialTicks));
        program.scalar("Time",(mc.level.getGameTime()%240000+partialTicks)/20f);
        var pack=PackRepository.active();
        if(!pack.shaderOptions().isEmpty())for(var option:pack.shaderOptions())
            program.scalar(option.uniform(),(float)PackOptionStore.live().value(pack,option));
    }
    public static void reload(){if(RenderSystem.isOnRenderThread())reset();else RenderSystem.recordRenderCall(VisualPostProcessor::reset);}
    private static void reset(){attempted=null;failed=false;error="";releaseTargets();history.reset();}
    private static void releasePostTargets(){
        if(result!=null)result.close();if(bloomA!=null)bloomA.close();if(bloomB!=null)bloomB.close();result=bloomA=bloomB=null;
        if(chain!=null)chain.releaseTargets();
    }
    private static void releaseTargets(){releasePostTargets();if(opaque!=null)opaque.close();opaque=null;opaqueReady=false;}
    public static void shutdown(){try(GlState state=new GlState()){
        releaseTargets();ShadowMapRenderer.shutdown();if(composite!=null)composite.close();if(blur!=null)blur.close();if(chain!=null)chain.close();
        if(vao!=0)GL30.glDeleteVertexArrays(vao);composite=blur=null;chain=null;vao=0;attempted=null;history.reset();
    }}
    public static String diagnostic(){return failed?"OpenGL failure: "+error:"OpenGL: "+(result==null?"idle":"ready")+"; pack="+PackRepository.active().title()+" "+PackRepository.error();}
    private VisualPostProcessor(){}
}
