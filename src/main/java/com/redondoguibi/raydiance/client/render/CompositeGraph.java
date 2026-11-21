package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.client.shaders.PackRepository;
import com.redondoguibi.raydiance.client.shaders.ProgramDirectives;
import org.lwjgl.opengl.*;
import java.util.*;
import java.util.function.*;

/**
 * Format-3 runtime: Iris-style {@code composite*} and {@code final} programs over {@code colortex0-7}
 * (compare Iris' CompositeRenderer). Only allocate/render inside a saved GL state.
 *
 * <ul>
 *   <li>colortex0 starts as the frame input (world color, or the built-in effects' result) and is never written in place.</li>
 *   <li>Programs write {@code RENDERTARGETS} through MRT; written buffers flip afterwards.</li>
 *   <li>{@code final} renders straight into the world target when it cannot read that same texture.</li>
 * </ul>
 */
final class CompositeGraph implements AutoCloseable {
    private record Stage(PackRepository.Program program,GlProgram gl,int[] targets,int[] mipmaps,boolean readsInput,boolean readsWorld) {}
    private final List<Stage> stages=new ArrayList<>();
    private final Map<Integer,PackRepository.BufferSpec> specs;
    private final GlTarget[][] textures=new GlTarget[ProgramDirectives.BUFFERS][2];
    private final BufferFlipper flipper=new BufferFlipper(ProgramDirectives.BUFFERS);
    private final Map<String,Integer> framebuffers=new HashMap<>();
    private GlTarget direct;
    private int width,height;
    private final boolean opaque;

    CompositeGraph(PackRepository.Pack pack,List<String> sources,PackRepository.Pack owner) {
        specs=pack.buffers();
        try{
            for(int i=0;i<pack.programs().size();i++){
                var program=pack.programs().get(i);GlProgram gl;
                try{gl=new GlProgram(sources.get(i));gl.use();FrameUniforms.prepare(gl,owner);}
                catch(RuntimeException ex){throw new IllegalArgumentException(program.file()+": "+ex.getMessage(),ex);}
                stages.add(new Stage(program,gl,program.directives().renderTargets().stream().mapToInt(Integer::intValue).toArray(),
                        program.directives().mipmaps().stream().mapToInt(Integer::intValue).toArray(),
                        gl.samples("colortex0","gcolor","Scene"),gl.samples("OriginalScene")));
            }
        }catch(RuntimeException ex){close();throw ex;}
        opaque=stages.stream().anyMatch(s->s.gl.samples("depthtex1","depthtex2"));
    }
    boolean wantsOpaqueDepth(){return opaque;}
    boolean hasUniform(String name){return stages.stream().anyMatch(s->s.gl.has(name));}
    private static boolean enabled(Stage s,ToDoubleFunction<String> values,Predicate<String> defines){
        var p=s.program;
        return (p.when().isEmpty()||values.applyAsDouble(p.when())>0)&&(p.enabledBy().isEmpty()||defines.test(p.enabledBy()));
    }
    boolean active(ToDoubleFunction<String> values,Predicate<String> defines){for(var s:stages)if(enabled(s,values,defines))return true;return false;}

    private GlTarget texture(int buffer,int side){
        if(textures[buffer][side]==null){
            var spec=specs.get(buffer);
            int w=Math.max(1,(int)Math.ceil(width*spec.scale())),h=Math.max(1,(int)Math.ceil(height*spec.scale()));
            var t=new GlTarget(w,h,spec.format().gl,0,spec.mipmap());
            textures[buffer][side]=t;clear(t,spec.clearColor());
        }
        return textures[buffer][side];
    }
    private static void clear(GlTarget target,float[] color){
        target.bind();GL11.glClearColor(color[0],color[1],color[2],color[3]);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }
    private int readTexture(int buffer,int input){
        if(!specs.containsKey(buffer))return 0;
        int side=flipper.read(buffer);
        return side==BufferFlipper.INPUT?input:texture(buffer,side).color;
    }
    private int framebuffer(int[] colors){
        return framebuffers.computeIfAbsent(Arrays.toString(colors),key->{
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            int[] attachments=new int[colors.length];
            for(int i=0;i<colors.length;i++){attachments[i]=GL30.GL_COLOR_ATTACHMENT0+i;GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,attachments[i],GL11.GL_TEXTURE_2D,colors[i],0);}
            GL20.glDrawBuffers(attachments);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE){GL30.glDeleteFramebuffers(fbo);throw new IllegalStateException("Incomplete Raydiance MRT framebuffer");}
            return fbo;
        });
    }

    /**
     * @param inputTarget the frame input when it is an owned target (built-in effects), or null for the world color
     * @return the target to copy into the world, or null when the world already holds the result
     */
    GlTarget render(GlTarget inputTarget,int worldColor,int w,int h,ToDoubleFunction<String> values,Predicate<String> defines,BiConsumer<GlProgram,FrameUniforms.Pass> bind){
        if(PackRepository.bufferBytes(specs,w,h)>PostPassChain.MAX_TARGET_BYTES)throw new IllegalArgumentException("Pack buffers exceed 128 MiB at this resolution");
        if(width!=w||height!=h){releaseTargets();width=w;height=h;}
        int input=inputTarget==null?worldColor:inputTarget.color;
        flipper.startFromInput(0);
        for(var spec:specs.values())if(spec.index()!=0&&spec.clear()){flipper.reset(spec.index());clear(texture(spec.index(),0),spec.clearColor());}
        int last=-1;
        for(int i=0;i<stages.size();i++)if(enabled(stages.get(i),values,defines))last=i;
        boolean wroteDirect=false;
        int[] colortex=new int[ProgramDirectives.BUFFERS];
        for(int i=0;i<stages.size();i++){
            var stage=stages.get(i);
            if(!enabled(stage,values,defines))continue;
            for(int m:stage.mipmaps){
                int side=flipper.read(m);
                if(side!=BufferFlipper.INPUT){GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture(m,side).color);GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);}
            }
            for(int b=0;b<colortex.length;b++)colortex[b]=readTexture(b,input);
            // final may draw into the world target unless it would sample that very texture.
            boolean toWorld=i==last&&stage.program.name().equals("final")&&stage.targets.length==1&&stage.targets[0]==0
                    &&!stage.readsWorld&&!(stage.readsInput&&inputTarget==null&&flipper.read(0)==BufferFlipper.INPUT);
            int tw,th;
            if(toWorld){
                if(direct==null||direct.color!=worldColor){if(direct!=null)direct.close();direct=GlTarget.borrowColor(worldColor,w,h);}
                direct.bind();tw=w;th=h;
            }else{
                int[] colors=new int[stage.targets.length];
                for(int t=0;t<colors.length;t++)colors[t]=texture(stage.targets[t],flipper.write(stage.targets[t])).color;
                var first=texture(stage.targets[0],flipper.write(stage.targets[0]));tw=first.width;th=first.height;
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer(colors));GL11.glViewport(0,0,tw,th);
            }
            stage.gl.use();
            bind.accept(stage.gl,new FrameUniforms.Pass(colortex[0],w,h,tw,th,i,colortex.clone()));
            stage.gl.draw();
            if(toWorld)wroteDirect=true;
            else for(int t:stage.targets)flipper.flip(t);
        }
        if(wroteDirect)return null;
        int side=flipper.read(0);
        return side==BufferFlipper.INPUT?inputTarget:texture(0,side);
    }
    void releaseTargets(){
        for(var pair:textures)for(int s=0;s<2;s++){if(pair[s]!=null)pair[s].close();pair[s]=null;}
        framebuffers.values().forEach(GL30::glDeleteFramebuffers);framebuffers.clear();
        if(direct!=null)direct.close();direct=null;width=height=0;flipper.resetAll();
    }
    public void close(){releaseTargets();stages.forEach(s->s.gl.close());stages.clear();}
}
