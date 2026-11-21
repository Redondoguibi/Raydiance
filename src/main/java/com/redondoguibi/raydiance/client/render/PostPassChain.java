package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.client.shaders.PackRepository;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** A bounded chain with a distinct read/write target per active pass. */
public final class PostPassChain implements AutoCloseable {
    public static final long MAX_TARGET_BYTES = 128L * 1024 * 1024;
    @FunctionalInterface public interface UniformBinder {
        void bind(GlProgram program,int input,int inputWidth,int inputHeight,int index,int width,int height);
    }
    private final List<PackRepository.Pass> passes;
    private final List<GlProgram> programs=new ArrayList<>();
    private final GlTarget[] targets;
    private int width,height;
    private GlTarget directOutput;
    private int checkedWorldColor;
    private boolean rgba8WorldColor;
    public PostPassChain(List<PackRepository.Pass> passes) {
        this.passes=List.copyOf(passes);targets=new GlTarget[passes.size()];
        try {
            for(var pass:passes) {
                try { programs.add(new GlProgram(pass.source())); }
                catch(RuntimeException ex) {throw new IllegalArgumentException(pass.file()+": "+ex.getMessage(),ex);}
            }
        } catch(RuntimeException ex) {close();throw ex;}
    }
    public boolean active(ToDoubleFunction<String> values){for(var p:passes)if(p.when().isEmpty()||values.applyAsDouble(p.when())>0)return true;return false;}
    public boolean empty(){return passes.isEmpty();}
    public void validateScalars(Collection<String> names){programs.forEach(p->p.validateScalars(names));}
    public boolean hasUniform(String name){return programs.stream().anyMatch(p->p.has(name));}
    public boolean samples(String... names){return programs.stream().anyMatch(p->p.samples(names));}
    /** Resolves every program's uniform plan against the owning pack (validates types up front). */
    void prepare(PackRepository.Pack owner){for(var p:programs){p.use();FrameUniforms.prepare(p,owner);}}
    public GlTarget render(int input,int w,int h,UniformBinder binder,ToDoubleFunction<String> values) {
        return render(input,w,h,binder,values,0);
    }
    public GlTarget render(int input,int w,int h,UniformBinder binder,ToDoubleFunction<String> values,int worldColor) {
        if(PackRepository.targetBytes(passes,w,h)>MAX_TARGET_BYTES)throw new IllegalArgumentException("Pack targets exceed 128 MiB at this resolution");
        if(width!=w||height!=h){releaseTargets();width=w;height=h;}
        if(worldColor!=0&&checkedWorldColor!=worldColor){
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,worldColor);
            rgba8WorldColor=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_INTERNAL_FORMAT)==org.lwjgl.opengl.GL11.GL_RGBA8;
            checkedWorldColor=worldColor;
        }
        int inputWidth=w,inputHeight=h;GlTarget output=null;
        int lastActive=-1;
        for(int i=0;i<passes.size();i++)if(passes.get(i).when().isEmpty()||values.applyAsDouble(passes.get(i).when())>0)lastActive=i;
        for(int i=0;i<passes.size();i++) {
            var pass=passes.get(i);
            if(!pass.when().isEmpty()&&values.applyAsDouble(pass.when())<=0) {
                if(targets[i]!=null){targets[i].close();targets[i]=null;}
                continue;
            }
            int tw=Math.max(1,(int)Math.ceil(w*pass.scale())),th=Math.max(1,(int)Math.ceil(h*pass.scale()));
            var program=programs.get(i);
            // OriginalScene is the world texture and must remain readable until the last pass.
            // Float intermediates still need their declared format before final conversion.
            boolean direct=i==lastActive&&worldColor!=0&&rgba8WorldColor&&input!=worldColor&&tw==w&&th==h
                    &&!pass.floatingPoint()&&!program.has("OriginalScene");
            GlTarget target;
            if(direct){
                if(directOutput==null||directOutput.color!=worldColor){if(directOutput!=null)directOutput.close();directOutput=GlTarget.borrowColor(worldColor,w,h);}
                if(targets[i]!=null){targets[i].close();targets[i]=null;}
                target=directOutput;
            }else{if(targets[i]==null)targets[i]=new GlTarget(tw,th,false,0,pass.floatingPoint());target=targets[i];}
            target.bind();program.use();
            binder.bind(program,input,inputWidth,inputHeight,i,tw,th);program.draw();
            input=target.color;inputWidth=tw;inputHeight=th;output=target;
        }
        return output;
    }
    public void releaseTargets(){for(int i=0;i<targets.length;i++){if(targets[i]!=null)targets[i].close();targets[i]=null;}if(directOutput!=null)directOutput.close();directOutput=null;checkedWorldColor=0;rgba8WorldColor=false;width=height=0;}
    public void close(){releaseTargets();programs.forEach(GlProgram::close);programs.clear();}
}
