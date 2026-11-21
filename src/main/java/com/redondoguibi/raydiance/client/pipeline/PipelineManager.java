package com.redondoguibi.raydiance.client.pipeline;

import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.Raydiance;
import com.redondoguibi.raydiance.client.render.DirectPipeline;
import com.redondoguibi.raydiance.client.render.GlPipeline;
import com.redondoguibi.raydiance.client.render.GlState;
import com.redondoguibi.raydiance.client.shaders.*;
import com.redondoguibi.raydiance.compat.direct.DirectCompatibility;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

/**
 * Owns the active {@link ShaderPipeline} (compare Iris' PipelineManager).
 *
 * <p>Pack data (manifest, sources, options) loads without GL, so menus can edit a pack even when it fails to
 * compile. The pipeline is rebuilt whenever its key changes: pack, dimension, language, backend, define values,
 * quality or a reload. A failed build never replaces a working pipeline of the same pack; a failing different pack
 * leaves nothing running, and the error is reported once in chat.</p>
 */
public final class PipelineManager {
    private static ShaderPipeline pipeline;
    private static String pipelineKey,failedKey,packKey,rejectedKey;
    private static PackRepository.Pack pack=PackRepository.NONE;
    private static String packError="",buildError="";
    private static int generation;
    private static boolean announce,directWorldPost;
    private static BackendSelector.Decision decision=new BackendSelector.Decision(BackendSelector.Choice.NONE,"");

    public static boolean packSelected(){return !"none".equals(Config.OPENGL_PACK.get());}
    public static PackBackend backend(){return PackBackend.parse(Config.PACK_BACKEND.get());}
    /** Called every client tick: cheap reflection probes stay out of the frame loop. */
    public static void tick(){
        ensurePack();
        directWorldPost=DirectCompatibility.worldPostAvailable()&&!Minecraft.useShaderTransparency();
    }
    private static String dimension(){
        var level=Minecraft.getInstance().level;
        return level==null?PackRepository.LoadContext.DEFAULT.dimension():level.dimension().location().toString();
    }
    private static String language(){
        try{return Minecraft.getInstance().getLanguageManager().getSelected();}catch(RuntimeException ex){return "en_us";}
    }
    private static void ensurePack(){
        String id=Config.OPENGL_PACK.get(),dimension=dimension(),language=language();
        String key=id+"|"+dimension+"|"+language+"|"+generation;
        if(key.equals(packKey))return;
        packKey=key;
        if("none".equals(id)){pack=PackRepository.NONE;packError="";PackRepository.accept(pack);return;}
        try{
            var loaded=PackRepository.load(id,new PackRepository.LoadContext(dimension,language));
            PackOptionStore.live().prepare(loaded);
            pack=loaded;packError="";PackRepository.accept(loaded);
        }catch(Exception ex){
            pack=PackRepository.NONE;PackRepository.accept(pack);PackRepository.reject(ex);
            packError=ex.getMessage()==null?ex.toString():ex.getMessage();
            Raydiance.LOGGER.error("Cannot load shaderpack {}",id,ex);
            report(Component.translatable("raydiance.pipeline.loadFailed",id,ShaderErrors.summary(packError,2)));
        }
    }
    /** The loaded selected pack, or {@link PackRepository#NONE}. */
    public static PackRepository.Pack pack(){ensurePack();return pack;}
    public static boolean packReady(){ensurePack();return packSelected()&&!pack.none();}
    /** Effective engine value of the selected pack (cached pack; refreshed by tick/render/menus). */
    public static double value(String key){return PackOptionStore.live().engine(pack,key);}
    public static int quality(){return packSelected()&&!pack.none()?PackOptionStore.live().quality(pack):Config.QUALITY.get();}
    public static boolean fxaa(){return packSelected()&&!pack.none()?PackOptionStore.live().fxaa(pack):Config.FXAA.get();}

    static BackendSelector.Decision decide(){
        ensurePack();
        return BackendSelector.decide(new BackendSelector.Inputs(Config.ENABLE_EFFECTS.get(),packSelected(),!pack.none(),backend(),
                Config.ENABLE_DIRECT_INTEGRATION.get(),directWorldPost,pack.canRunDirect(),pack.canRunOpenGl(),
                Objects.equals(rejectedKey,pack.id()+"|"+generation)));
    }
    public static BackendSelector.Decision decision(){return decision;}
    /** True while the selected pack executes on Direct DX12 (geometry requests, fog and terrain then belong to Direct). */
    public static boolean directActive(){return pipeline!=null&&pipeline.kind()==ShaderPipeline.Kind.DIRECT&&decision.choice()==BackendSelector.Choice.DIRECT;}
    public static boolean wantsOpaqueDepth(){return pipeline!=null&&pipeline.wantsOpaqueDepth();}

    /** Render thread, after the level and before hand/GUI. */
    public static void render(float partialTicks){
        decision=decide();
        var choice=decision.choice();
        if(choice==BackendSelector.Choice.NONE){close();return;}
        boolean direct=choice==BackendSelector.Choice.DIRECT;
        var target=choice==BackendSelector.Choice.BUILTIN?PackRepository.NONE:pack;
        Map<String,String> defines=target.none()?Map.of():PackOptionStore.live().defineValues(target);
        int quality=quality();
        String key=choice+"|"+packKey+"|"+defines+"|"+quality;
        if(!key.equals(pipelineKey)&&!key.equals(failedKey))build(key,target,direct,defines,quality);
        if(pipeline==null)return;
        pipeline.render(partialTicks);
        if(pipeline.rejected()&&!Objects.equals(rejectedKey,pack.id()+"|"+generation)){
            rejectedKey=pack.id()+"|"+generation;
            report(Component.translatable("raydiance.pipeline.directRejected",pack.title(),ShaderErrors.summary(pipeline.status(),2)));
        }
    }
    private static void build(String key,PackRepository.Pack target,boolean direct,Map<String,String> defines,int quality){
        var macros=ShaderMacros.standard(direct?"dx12":"opengl",quality,dimension(),target.format());
        long start=System.nanoTime();
        ShaderPipeline candidate=null;
        try(GlState state=new GlState()){
            candidate=direct?new DirectPipeline(target,macros,defines):new GlPipeline(target,macros,defines);
        }catch(Exception ex){
            failedKey=key;buildError=ex.getMessage()==null?ex.toString():ex.getMessage();
            Raydiance.LOGGER.error("Rejected Raydiance pipeline for {}; {}",target.id(),pipeline!=null&&pipeline.pack().id().equals(target.id())?"keeping the previous one":"nothing will run",ex);
            if(pipeline!=null&&!pipeline.pack().id().equals(target.id()))close();
            report(Component.translatable("raydiance.pipeline.failed",target.none()?"Raydiance":target.title(),ShaderErrors.summary(buildError,4)));
            announce=false;
            return;
        }
        close();
        pipeline=candidate;pipelineKey=key;failedKey=null;buildError="";
        if(announce){
            announce=false;
            var mc=Minecraft.getInstance();
            mc.gui.setOverlayMessage(Component.translatable("raydiance.pipeline.reloaded",target.none()?"Raydiance":target.title(),
                    direct?"Direct DX12":"OpenGL",(System.nanoTime()-start)/1_000_000),false);
        }
    }
    private static void report(Component message){
        var mc=Minecraft.getInstance();
        if(mc.player!=null)mc.gui.getChat().addMessage(Component.literal("[Raydiance] ").withStyle(ChatFormatting.GOLD).append(message.copy().withStyle(ChatFormatting.RED)));
    }
    private static void close(){
        if(pipeline==null)return;
        try(GlState state=new GlState()){pipeline.close();}
        pipeline=null;pipelineKey=null;
    }
    /** No world: release GPU resources, like Iris destroying its pipeline on world unload. */
    public static void idle(){if(pipeline!=null){close();}}
    public static void releaseTargets(){if(pipeline!=null)try(GlState state=new GlState()){pipeline.releaseTargets();}}
    /** Re-reads pack files and recompiles; {@code announce} shows the result (reload key, command, menu). */
    public static void reload(boolean announce){
        generation++;failedKey=null;rejectedKey=null;buildError="";PipelineManager.announce=announce;
    }
    public static void shutdown(){close();}

    public static String status(){
        StringBuilder s=new StringBuilder();
        var choice=decision.choice();
        s.append(packSelected()?"Pack: "+(pack.none()?Config.OPENGL_PACK.get():pack.title()+" (format "+pack.format()+")"):"Pack: none (built-in effects)");
        s.append("\nBackend: ").append(backend().id()).append(" -> ").append(switch(choice){
            case DIRECT->"Direct DX12";case OPENGL->"OpenGL";case BUILTIN->"OpenGL built-in";case NONE->"paused";});
        if(!decision.reason().isEmpty())s.append("\n").append(decision.reason());
        if(pipeline!=null)s.append("\n").append(pipeline.status());
        if(!packError.isEmpty())s.append("\nLoad error: ").append(ShaderErrors.summary(packError,3));
        if(!buildError.isEmpty())s.append("\nCompile error: ").append(ShaderErrors.summary(buildError,6));
        long defines=pack.shaderOptions().stream().filter(ShaderOption::define).count();
        if(defines>0)s.append("\n").append(defines).append(" #define options");
        return s.toString();
    }
    private PipelineManager(){}
}
