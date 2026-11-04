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
    public PostPassChain(List<PackRepository.Pass> passes) {
        this.passes=List.copyOf(passes);targets=new GlTarget[passes.size()];
        try {
            for(var pass:passes) {
                try { programs.add(new GlProgram(pass.source())); }
                catch(RuntimeException ex) {throw new IllegalArgumentException(pass.file()+": "+ex.getMessage(),ex);}
            }
        } catch(RuntimeException ex) {close();throw ex;}
    }
    public boolean active(ToDoubleFunction<String> values){return passes.stream().anyMatch(p->p.when().isEmpty()||values.applyAsDouble(p.when())>0);}
    public boolean empty(){return passes.isEmpty();}
    public void validateScalars(Collection<String> names){programs.forEach(p->p.validateScalars(names));}
    public boolean hasUniform(String name){return programs.stream().anyMatch(p->p.has(name));}
    public GlTarget render(int input,int w,int h,UniformBinder binder,ToDoubleFunction<String> values) {
        if(PackRepository.targetBytes(passes,w,h)>MAX_TARGET_BYTES)throw new IllegalArgumentException("Pack targets exceed 128 MiB at this resolution");
        if(width!=w||height!=h){releaseTargets();width=w;height=h;}
        int inputWidth=w,inputHeight=h;GlTarget output=null;
        for(int i=0;i<passes.size();i++) {
            var pass=passes.get(i);
            if(!pass.when().isEmpty()&&values.applyAsDouble(pass.when())<=0) {
                if(targets[i]!=null){targets[i].close();targets[i]=null;}
                continue;
            }
            int tw=Math.max(1,(int)Math.ceil(w*pass.scale())),th=Math.max(1,(int)Math.ceil(h*pass.scale()));
            if(targets[i]==null)targets[i]=new GlTarget(tw,th,false,0,pass.floatingPoint());
            var target=targets[i];target.bind();var program=programs.get(i);program.use();
            binder.bind(program,input,inputWidth,inputHeight,i,tw,th);program.draw();
            input=target.color;inputWidth=tw;inputHeight=th;output=target;
        }
        return output;
    }
    public void releaseTargets(){for(int i=0;i<targets.length;i++){if(targets[i]!=null)targets[i].close();targets[i]=null;}width=height=0;}
    public void close(){releaseTargets();programs.forEach(GlProgram::close);programs.clear();}
}
