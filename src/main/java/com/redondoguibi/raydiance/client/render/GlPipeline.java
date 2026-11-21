package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.Raydiance;
import com.redondoguibi.raydiance.client.pipeline.PipelineManager;
import com.redondoguibi.raydiance.client.pipeline.ShaderPipeline;
import com.redondoguibi.raydiance.client.profile.DynamicProfiles;
import com.redondoguibi.raydiance.client.shaders.*;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import org.lwjgl.opengl.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * OpenGL pipeline: the fused built-in effects, then a format-1 custom composite, a format-2 pass chain or a
 * format-3 program graph. Construct inside a saved GL state; construction compiles and validates everything.
 */
public final class GlPipeline implements ShaderPipeline {
    private final PackRepository.Pack pack;
    private final Predicate<String> defineOn;
    private GlProgram composite,blur;
    private PostPassChain chain;
    private CompositeGraph graph;
    private GlTarget result,bloomA,bloomB;
    private boolean resultFloating,failed;
    private int vao;
    private String error="";

    public GlPipeline(PackRepository.Pack pack,Map<String,String> macros,Map<String,String> defines) throws java.io.IOException {
        this.pack=pack;
        defineOn=name->"true".equals(defines.get(name));
        try{
            var target=Minecraft.getInstance().getMainRenderTarget();
            composite=compile("composite.fsh",pack.composite()==null?GlProgram.resource("composite.fsh"):finish(pack.composite(),macros,defines));
            if(!pack.passes().isEmpty()){
                if(PackRepository.targetBytes(pack.passes(),target.width,target.height)>PostPassChain.MAX_TARGET_BYTES)throw new IllegalArgumentException("Pack targets exceed 128 MiB");
                List<PackRepository.Pass> passes=new ArrayList<>();
                for(var p:pack.passes())passes.add(new PackRepository.Pass(p.file(),finish(p.source(),macros,defines),p.scale(),p.floatingPoint(),p.when()));
                chain=new PostPassChain(passes);
                chain.prepare(pack);
            }
            if(!pack.programs().isEmpty()){
                if(PackRepository.bufferBytes(pack.buffers(),target.width,target.height)>PostPassChain.MAX_TARGET_BYTES)throw new IllegalArgumentException("Pack buffers exceed 128 MiB");
                List<String> sources=new ArrayList<>();
                for(var p:pack.programs())sources.add(finish(p.source(),macros,defines));
                graph=new CompositeGraph(pack,sources,pack);
            }
            for(var option:pack.uniformOptions())
                if(!composite.has(option.uniform())&&(chain==null||!chain.hasUniform(option.uniform()))&&(graph==null||!graph.hasUniform(option.uniform())))
                    throw new IllegalArgumentException("Shader setting has no active uniform: "+option.uniform());
        }catch(RuntimeException ex){
            close();
            throw new IllegalArgumentException(ShaderErrors.translate(ex.getMessage(),pack.sourceNames()),ex);
        }
    }
    private static String finish(String source,Map<String,String> macros,Map<String,String> defines){
        return ShaderMacros.inject(DefineOptions.apply(source,defines),ShaderSourceLoader.Dialect.GLSL,macros,"");
    }
    private GlProgram compile(String label,String source){
        try{var p=new GlProgram(source);p.use();FrameUniforms.prepare(p,pack);return p;}
        catch(RuntimeException ex){throw new IllegalArgumentException(label+": "+ex.getMessage(),ex);}
    }
    @Override public Kind kind(){return Kind.OPENGL;}
    @Override public PackRepository.Pack pack(){return pack;}
    @Override public boolean wantsOpaqueDepth(){
        return composite!=null&&composite.samples("depthtex1","depthtex2")||chain!=null&&chain.samples("depthtex1","depthtex2")||graph!=null&&graph.wantsOpaqueDepth();
    }
    private static float value(String key){return VisualPostProcessor.value(key);}

    @Override public void render(float partialTicks){
        var mc=Minecraft.getInstance();var level=mc.level;
        var target=mc.getMainRenderTarget();
        var grade=VisualSettings.current();
        boolean sky=level.dimensionType().hasSkyLight();
        boolean builtin=pack.builtinEffects()&&RenderPlan.post(true,grade.isNeutral(),pack.composite()!=null,value("bloom"),
                sky?value("atmosphere"):0,sky&&!DirectShaderBridge.nativeGeometryActive()?value("contactShadows"):0,
                PipelineManager.fxaa(),value("ssao"),sky&&mc.gameRenderer.getMainCamera().getFluidInCamera()==FogType.NONE?value("godRays"):0,value("sharpen"),value("toneMapping"));
        boolean activeChain=chain!=null&&chain.active(DynamicProfiles::value);
        boolean activeGraph=graph!=null&&graph.active(DynamicProfiles::value,defineOn);
        if((!builtin&&!activeChain&&!activeGraph)||failed){releaseTargets();return;}
        try(GlState state=new GlState()){
            if(vao==0)vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);GlState.fullscreen();
            FrameUniforms.update(partialTicks);
            int world=target.getColorTextureId();
            GlTarget output=null;int chainInput=world;
            if(builtin){
                boolean floating=activeChain||activeGraph;
                if(result==null||result.width!=target.width||result.height!=target.height||resultFloating!=floating){
                    releaseTargets();result=new GlTarget(target.width,target.height,false,0,floating);resultFloating=floating;
                }
                renderBloom(world,target.width,target.height);
                result.bind();composite.use();
                FrameUniforms.bind(composite,new FrameUniforms.Pass(world,target.width,target.height,target.width,target.height,-1,null));
                composite.draw();
                output=result;chainInput=result.color;
            }else if(result!=null||bloomA!=null)releaseBuiltin();
            if(activeChain){
                var rendered=chain.render(chainInput,target.width,target.height,
                        (program,input,iw,ih,index,w,h)->FrameUniforms.bind(program,new FrameUniforms.Pass(input,iw,ih,w,h,index,null)),DynamicProfiles::value,world);
                if(rendered!=null)output=rendered;
            }
            if(activeGraph){
                var input=output==null||output.color==world?null:output;
                output=graph.render(input,world,target.width,target.height,DynamicProfiles::value,defineOn,FrameUniforms::bind);
            }
            if(output==null||output.color==world)return;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,output.fbo);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);
            GL30.glBlitFramebuffer(0,0,output.width,output.height,0,0,target.width,target.height,GL11.GL_COLOR_BUFFER_BIT,
                    output.width==target.width&&output.height==target.height?GL11.GL_NEAREST:GL11.GL_LINEAR);
        }catch(Exception ex){
            failed=true;error=ShaderErrors.translate(String.valueOf(ex.getMessage()),pack.sourceNames());
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
    private void releaseBuiltin(){
        if(result!=null)result.close();if(bloomA!=null)bloomA.close();if(bloomB!=null)bloomB.close();result=bloomA=bloomB=null;
    }
    @Override public void releaseTargets(){
        releaseBuiltin();
        if(chain!=null)chain.releaseTargets();
        if(graph!=null)graph.releaseTargets();
    }
    @Override public String status(){
        if(failed)return "OpenGL failure: "+ShaderErrors.summary(error,3);
        String shape=pack.none()?"built-in effects":switch(pack.format()){
            case 3->pack.programs().size()+" programs, "+pack.buffers().size()+" buffers";
            case 2->pack.passes().size()+" passes";
            default->pack.composite()==null?"built-in composite":"custom composite";
        };
        return "OpenGL: "+(result==null&&!pack.hasGlsl()?"idle":"ready")+" ("+shape+")";
    }
    @Override public void close(){
        releaseTargets();
        if(composite!=null)composite.close();if(blur!=null)blur.close();if(chain!=null)chain.close();if(graph!=null)graph.close();
        if(vao!=0)GL30.glDeleteVertexArrays(vao);composite=blur=null;chain=null;graph=null;vao=0;
    }
}
