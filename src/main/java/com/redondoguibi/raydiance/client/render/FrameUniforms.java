package com.redondoguibi.raydiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redondoguibi.raydiance.client.pipeline.PipelineManager;
import com.redondoguibi.raydiance.client.shaders.PackOptionStore;
import com.redondoguibi.raydiance.client.shaders.PackRepository;
import com.redondoguibi.raydiance.client.shaders.ProgramDirectives;
import com.redondoguibi.raydiance.client.shaders.ShaderOption;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Frame-level uniform registry (compare Iris' CommonUniforms/CameraUniforms/CelestialUniforms).
 *
 * <p>Every reserved name has one GL type. Programs resolve their active uniforms once into a plan; each draw then
 * uploads only what the program actually declares. Raydiance names (Scene, Projection, Wetness, ...) keep their
 * documented meaning; the lower-case Iris-style aliases (colortex0, gbufferProjection, sunPosition, ...) ease
 * porting post-processing code but do not imply gbuffer data: colortex buffers hold what composite programs wrote.</p>
 */
public final class FrameUniforms {
    /** Per-draw inputs. {@code colortex} is null outside format-3 programs. */
    public record Pass(int scene,int inputWidth,int inputHeight,int outputWidth,int outputHeight,int index,int[] colortex) {}
    @FunctionalInterface interface Binder { void bind(GlProgram p,int location,Pass pass); }
    @FunctionalInterface interface Sampler { int texture(Pass pass); }
    private record Entry(int type,Binder binder,Sampler sampler) {}
    private record Plan(int[] locations,Binder[] binders,Sampler[] samplers,int[] units,int[] optionLocations,ShaderOption[] options,PackRepository.Pack owner) {}
    private static final Map<String,Entry> ENTRIES=new HashMap<>();
    /** Iris noiseTextureResolution default. */
    public static final int NOISE_SIZE=256;

    // Frame state ----------------------------------------------------------------------------------------------
    static final Matrix4f view=new Matrix4f(),projection=new Matrix4f(),inverseView=new Matrix4f(),inverseProjection=new Matrix4f();
    private static final Matrix4f shadowViewInverse=new Matrix4f(),shadowProjectionInverse=new Matrix4f();
    static final FrameHistory history=new FrameHistory();
    private static final Map<String,Float> engine=new HashMap<>();
    private static final Vector3f sun=new Vector3f(),up=new Vector3f(),moon=new Vector3f(),shadowLight=new Vector3f();
    private static float partial,daylight,rain,thunder,time,sunAngle,far,screenBrightness,nightVision,blindness,darkness;
    private static int worldTime,worldDay,moonPhase,eyeInWater,steps,fxaa;
    private static boolean skyLight,openAir,nativeShadows,skyDirty,eyeDirty;
    private static final float[] sky=new float[3],eye=new float[2],fog=new float[4];
    private static int bloomTexture,noise,black;
    private static PackRepository.Pack pack=PackRepository.NONE;

    public static void beginFrame(Matrix4f rotation,Matrix4f proj){
        view.set(rotation);projection.set(proj);inverseProjection.set(proj).invert();inverseView.set(rotation).invert();
        var mc=Minecraft.getInstance();var position=mc.gameRenderer.getMainCamera().getPosition();
        var block=mc.gameRenderer.getMainCamera().getBlockPosition();
        history.update(mc.level,System.nanoTime(),proj,rotation,new Vector3d(position.x,position.y,position.z),mc.level.getRainLevel(0),
                mc.level.getBrightness(LightLayer.BLOCK,block)/15f,mc.level.getBrightness(LightLayer.SKY,block)/15f);
    }
    /** Computes shared per-frame values once; called before any pass of the frame is drawn. */
    public static void update(float partialTicks){
        var mc=Minecraft.getInstance();var level=mc.level;partial=partialTicks;
        pack=PackRepository.active();
        for(String key:PackRepository.RANGES.keySet())engine.put(key,VisualPostProcessor.value(key));
        var camera=mc.gameRenderer.getMainCamera();var fluid=camera.getFluidInCamera();
        skyLight=level.dimensionType().hasSkyLight();openAir=fluid==FogType.NONE;nativeShadows=DirectShaderBridge.nativeGeometryActive();
        eyeInWater=fluid==FogType.WATER?1:fluid==FogType.LAVA?2:fluid==FogType.POWDER_SNOW?3:0;
        float angle=level.getSunAngle(partial);
        sun.set(-(float)Math.sin(angle),(float)Math.cos(angle),0);view.transformDirection(sun);
        up.set(0,1,0);view.transformDirection(up);moon.set(sun).negate();
        float skyAngle=level.getTimeOfDay(partial);sunAngle=skyAngle<.75f?skyAngle+.25f:skyAngle-.75f;
        shadowLight.set(sunAngle<=.5f?sun:moon);
        daylight=Math.max(0,(float)Math.cos(angle));rain=level.getRainLevel(partial);thunder=level.getThunderLevel(partial);
        time=(level.getGameTime()%240000+partial)/20f;
        worldTime=(int)Math.floorMod(level.getDayTime(),24000);worldDay=(int)Math.floorMod(level.getDayTime()/24000,2147483647);
        moonPhase=level.getMoonPhase();far=mc.gameRenderer.getRenderDistance();
        screenBrightness=mc.options.gamma().get().floatValue();
        var player=mc.player;
        nightVision=player!=null&&player.hasEffect(MobEffects.NIGHT_VISION)?GameRenderer.getNightVisionScale(player,partial):0;
        var blind=player==null?null:player.getEffect(MobEffects.BLINDNESS);
        blindness=blind==null?0:blind.isInfiniteDuration()?1:Math.min(1,blind.getDuration()/20f);
        var dark=player==null?null:player.getEffect(MobEffects.DARKNESS);
        darkness=dark==null?0:dark.getBlendFactor(player,partial);
        float[] f=RenderSystem.getShaderFogColor();System.arraycopy(f,0,fog,0,Math.min(4,f.length));
        steps=switch(PipelineManager.quality()){case 0->6;case 2->20;default->12;};fxaa=PipelineManager.fxaa()?1:0;
        skyDirty=eyeDirty=true;bloomTexture=0;
        if(ShadowMapRenderer.ready()){shadowViewInverse.set(ShadowMapRenderer.lightView()).invert();shadowProjectionInverse.set(ShadowMapRenderer.lightProjection()).invert();}
    }
    static void bloom(int texture){bloomTexture=texture;}
    private static float v(String key){return engine.getOrDefault(key,0f);}
    private static float[] sky(){
        if(skyDirty){var mc=Minecraft.getInstance();var c=mc.level.getSkyColor(mc.gameRenderer.getMainCamera().getPosition(),partial);sky[0]=(float)c.x;sky[1]=(float)c.y;sky[2]=(float)c.z;skyDirty=false;}
        return sky;
    }
    private static float[] eye(){
        if(eyeDirty){var mc=Minecraft.getInstance();var b=mc.gameRenderer.getMainCamera().getBlockPosition();eye[0]=mc.level.getBrightness(LightLayer.BLOCK,b)/15f;eye[1]=mc.level.getBrightness(LightLayer.SKY,b)/15f;eyeDirty=false;}
        return eye;
    }
    private static int worldColor(){return Minecraft.getInstance().getMainRenderTarget().getColorTextureId();}
    private static int worldDepth(){return Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();}

    // Registry ---------------------------------------------------------------------------------------------------
    private static void put(int type,String names,Binder binder){for(String n:names.split(" "))ENTRIES.put(n,new Entry(type,binder,null));}
    private static void sampler(String names,Sampler sampler){for(String n:names.split(" "))ENTRIES.put(n,new Entry(GL20.GL_SAMPLER_2D,null,sampler));}
    private static void f(String names,java.util.function.Supplier<Float> s){put(GL20.GL_FLOAT,names,(p,l,x)->p.set1f(l,s.get()));}
    private static void i(String names,java.util.function.IntSupplier s){put(GL20.GL_INT,names,(p,l,x)->p.set1i(l,s.getAsInt()));}
    private static void m(String names,java.util.function.Supplier<Matrix4f> s){put(GL20.GL_FLOAT_MAT4,names,(p,l,x)->p.setMatrix(l,s.get()));}
    private static void v3(String names,java.util.function.Supplier<Vector3f> s,float scale){put(GL20.GL_FLOAT_VEC3,names,(p,l,x)->{var q=s.get();p.set3f(l,q.x*scale,q.y*scale,q.z*scale);});}
    static {
        // Raydiance contract.
        sampler("Scene",p->p.scene());
        sampler("Depth depthtex0",p->worldDepth());
        sampler("depthtex1 depthtex2",p->VisualPostProcessor.opaqueReady()?VisualPostProcessor.opaqueDepth():worldDepth());
        sampler("BloomTex",p->bloomTexture==0?p.scene():bloomTexture);
        sampler("ShadowTex shadowtex0 shadowtex1",p->ShadowMapRenderer.ready()?ShadowMapRenderer.texture():worldDepth());
        sampler("OriginalScene",p->worldColor());
        sampler("noisetex",p->noise());
        for(var alias:ProgramDirectives.ALIASES.entrySet())sampler(alias.getKey(),colortex(alias.getValue()));
        for(int b=0;b<ProgramDirectives.BUFFERS;b++)sampler("colortex"+b,colortex(b));
        f("ShadowReady",()->ShadowMapRenderer.ready()?1f:0f);
        m("ShadowTransform",ShadowMapRenderer::transform);
        put(GL20.GL_FLOAT_VEC2,"Texel",(p,l,x)->p.set2f(l,1f/x.inputWidth(),1f/x.inputHeight()));
        put(GL20.GL_FLOAT_VEC2,"InputSize",(p,l,x)->p.set2f(l,x.inputWidth(),x.inputHeight()));
        put(GL20.GL_FLOAT_VEC2,"OutputSize",(p,l,x)->p.set2f(l,x.outputWidth(),x.outputHeight()));
        put(GL20.GL_FLOAT_VEC2,"ViewSize",(p,l,x)->{var t=Minecraft.getInstance().getMainRenderTarget();p.set2f(l,t.width,t.height);});
        put(GL20.GL_INT,"PassIndex",(p,l,x)->p.set1i(l,x.index()));
        i("FrameCounter frameCounter",()->history.counter);
        f("FrameTime frameTime",()->history.delta);f("FrameTimeCounter frameTimeCounter",()->history.seconds);
        i("HistoryValid",()->history.valid?1:0);
        i("WorldTime worldTime",()->worldTime);i("WorldDay worldDay",()->worldDay);
        f("Wetness wetness",()->history.wetness);f("Thunder thunderStrength",()->thunder);
        i("EyeInWater isEyeInWater",()->eyeInWater);
        put(GL20.GL_FLOAT_VEC3,"SkyColor skyColor",(p,l,x)->{var s=sky();p.set3f(l,s[0],s[1],s[2]);});
        put(GL20.GL_FLOAT_VEC2,"EyeBrightness",(p,l,x)->{var e=eye();p.set2f(l,e[0],e[1]);});
        put(GL20.GL_FLOAT_VEC3,"CameraPosition cameraPosition",(p,l,x)->{var c=history.camera;p.set3f(l,(float)c.x,(float)c.y,(float)c.z);});
        put(GL20.GL_INT_VEC3,"CameraBlock cameraPositionInt",(p,l,x)->{var c=history.camera;p.set3i(l,(int)Math.floor(c.x),(int)Math.floor(c.y),(int)Math.floor(c.z));});
        put(GL20.GL_FLOAT_VEC3,"CameraFraction cameraPositionFract",(p,l,x)->{var c=history.camera;p.set3f(l,(float)(c.x-Math.floor(c.x)),(float)(c.y-Math.floor(c.y)),(float)(c.z-Math.floor(c.z)));});
        put(GL20.GL_FLOAT_VEC3,"CameraDelta",(p,l,x)->{var d=history.cameraDelta;p.set3f(l,(float)d.x,(float)d.y,(float)d.z);});
        m("View gbufferModelView",()->view);m("InvView gbufferModelViewInverse",()->inverseView);
        m("PreviousView gbufferPreviousModelView",()->history.previousView);m("PreviousProjection gbufferPreviousProjection",()->history.previousProjection);
        m("Projection gbufferProjection",()->projection);m("InvProjection gbufferProjectionInverse",()->inverseProjection);
        f("Saturation",()->v("saturation"));f("Contrast",()->v("contrast"));f("Brightness",()->v("brightness"));f("Exposure",()->v("exposure"));
        f("Gamma",()->v("gamma"));f("Temperature",()->v("temperature"));f("Vignette",()->v("vignette"));f("Strength",()->v("strength"));
        f("Bloom",()->v("bloom"));f("Atmosphere",()->skyLight?v("atmosphere"):0);
        f("Shadows",()->skyLight&&!nativeShadows?v("contactShadows"):0);
        f("Ssao",()->v("ssao"));f("GodRays",()->skyLight&&openAir?v("godRays"):0);
        f("Sharpen",()->v("sharpen"));f("ToneMapping",()->v("toneMapping"));
        i("Fxaa",()->fxaa);i("Steps",()->steps);
        v3("Sun",()->sun,1);f("Daylight",()->daylight);f("Rain rainStrength",()->rain);f("Time",()->time);
        // Iris-style aliases.
        put(GL20.GL_FLOAT,"viewWidth",(p,l,x)->p.set1f(l,Minecraft.getInstance().getMainRenderTarget().width));
        put(GL20.GL_FLOAT,"viewHeight",(p,l,x)->p.set1f(l,Minecraft.getInstance().getMainRenderTarget().height));
        put(GL20.GL_FLOAT,"aspectRatio",(p,l,x)->{var t=Minecraft.getInstance().getMainRenderTarget();p.set1f(l,t.width/(float)Math.max(1,t.height));});
        f("near",()->.05f);f("far",()->far);
        f("sunAngle",()->sunAngle);f("shadowAngle",()->sunAngle<=.5f?sunAngle:sunAngle-.5f);
        v3("sunPosition",()->sun,100);v3("moonPosition",()->moon,100);v3("shadowLightPosition",()->shadowLight,100);v3("upPosition",()->up,100);
        put(GL20.GL_FLOAT_VEC3,"previousCameraPosition",(p,l,x)->{var c=history.camera;var d=history.cameraDelta;p.set3f(l,(float)(c.x-d.x),(float)(c.y-d.y),(float)(c.z-d.z));});
        put(GL20.GL_FLOAT_VEC3,"fogColor",(p,l,x)->p.set3f(l,fog[0],fog[1],fog[2]));
        put(GL20.GL_INT_VEC2,"eyeBrightness",(p,l,x)->{var e=eye();p.set2i(l,Math.round(e[0]*240),Math.round(e[1]*240));});
        put(GL20.GL_INT_VEC2,"eyeBrightnessSmooth",(p,l,x)->p.set2i(l,Math.round(history.blockLight*240),Math.round(history.skyLight*240)));
        f("eyeAltitude",()->(float)history.camera.y);
        i("moonPhase",()->moonPhase);i("noiseTextureResolution",()->NOISE_SIZE);
        f("screenBrightness",()->screenBrightness);f("nightVision",()->nightVision);f("blindness",()->blindness);f("darknessFactor",()->darkness);
        m("shadowModelView",ShadowMapRenderer::lightView);m("shadowProjection",ShadowMapRenderer::lightProjection);
        m("shadowModelViewInverse",()->shadowViewInverse);m("shadowProjectionInverse",()->shadowProjectionInverse);
    }
    private static Sampler colortex(int index){return p->p.colortex()!=null?(p.colortex()[index]!=0?p.colortex()[index]:black()):index==0?p.scene():black();}
    /** @return the GL type reserved for {@code name}, or 0 when the name is free. */
    static int expectedType(String name){var e=ENTRIES.get(name);return e==null?0:e.type;}
    public static Set<String> reservedNames(){return Collections.unmodifiableSet(ENTRIES.keySet());}

    // Binding ------------------------------------------------------------------------------------------------------
    /** Resolves (and validates) the program's plan. The program must be current. */
    static void prepare(GlProgram program,PackRepository.Pack owner){
        List<Integer> locations=new ArrayList<>();List<Binder> binders=new ArrayList<>();
        List<Sampler> samplers=new ArrayList<>();List<Integer> units=new ArrayList<>();
        List<Integer> optionLocations=new ArrayList<>();List<ShaderOption> options=new ArrayList<>();
        Map<String,ShaderOption> byUniform=new HashMap<>();
        for(var o:owner.uniformOptions())byUniform.put(o.uniform(),o);
        for(var u:program.activeUniforms()){
            var entry=ENTRIES.get(u.name());
            if(entry!=null&&entry.sampler!=null){
                if(samplers.size()>=GlState.UNITS)throw new IllegalArgumentException("A program may sample at most "+GlState.UNITS+" textures");
                units.add(samplers.size());samplers.add(entry.sampler);program.set1i(u.location(),samplers.size()-1);
            }else if(entry!=null){locations.add(u.location());binders.add(entry.binder);}
            else if(byUniform.containsKey(u.name())){
                if(u.type()!=GL20.GL_FLOAT||u.size()!=1)throw new IllegalArgumentException("Shader setting must use a float uniform: "+u.name());
                optionLocations.add(u.location());options.add(byUniform.get(u.name()));
            }
        }
        program.plan=new Plan(locations.stream().mapToInt(Integer::intValue).toArray(),binders.toArray(Binder[]::new),
                samplers.toArray(Sampler[]::new),units.stream().mapToInt(Integer::intValue).toArray(),
                optionLocations.stream().mapToInt(Integer::intValue).toArray(),options.toArray(ShaderOption[]::new),owner);
    }
    /** Uploads the program's active uniforms and binds its textures. The program must be current. */
    static void bind(GlProgram program,Pass pass){
        if(!(program.plan instanceof Plan))prepare(program,pack);
        Plan plan=(Plan)program.plan;
        for(int i=0;i<plan.locations.length;i++)plan.binders[i].bind(program,plan.locations[i],pass);
        for(int i=0;i<plan.samplers.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+plan.units[i]);GL11.glBindTexture(GL11.GL_TEXTURE_2D,plan.samplers[i].texture(pass));}
        if(plan.options.length==0)return;
        var store=PackOptionStore.live();
        for(int i=0;i<plan.options.length;i++)program.set1f(plan.optionLocations[i],(float)store.value(plan.owner,plan.options[i]));
    }

    // Generated textures -------------------------------------------------------------------------------------------
    private static int noise(){
        if(noise==0){
            var random=new Random(0x5241594CL);ByteBuffer data=org.lwjgl.BufferUtils.createByteBuffer(NOISE_SIZE*NOISE_SIZE*4);
            for(int i=0;i<NOISE_SIZE*NOISE_SIZE*4;i++)data.put((byte)random.nextInt(256));data.flip();
            noise=texture(NOISE_SIZE,data,GL11.GL_REPEAT);
        }
        return noise;
    }
    private static int black(){
        if(black==0){ByteBuffer data=org.lwjgl.BufferUtils.createByteBuffer(4);data.put(new byte[]{0,0,0,0}).flip();black=texture(1,data,GL12.GL_CLAMP_TO_EDGE);}
        return black;
    }
    private static int texture(int size,ByteBuffer data,int wrap){
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int t=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,t);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,4);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,wrap);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,wrap);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);return t;
    }
    static void shutdown(){if(noise!=0)GL11.glDeleteTextures(noise);if(black!=0)GL11.glDeleteTextures(black);noise=black=0;history.reset();}
    private FrameUniforms(){}
}
