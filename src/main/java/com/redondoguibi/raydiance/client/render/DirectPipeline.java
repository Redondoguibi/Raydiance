package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.client.pipeline.ShaderPipeline;
import com.redondoguibi.raydiance.client.shaders.*;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/**
 * Direct DX12 world post-processing (Direct API 1.5 {@code renderWorld}). The uniform block layout is the
 * documented Raydiance contract: 0-23 engine controls in {@link #KEYS} order, 32-63 pack uniforms in manifest
 * order, 64-127 matrices, 128-146 context.
 */
public final class DirectPipeline implements ShaderPipeline {
    public static final String[] KEYS={"saturation","contrast","brightness","exposure","gamma","temperature","vignette","strength","bloom","bloomThreshold","atmosphere","contactShadows","ssao","godRays","sharpen","toneMapping","fogDistance","water","reflections","wind","materialSpecular","materialWetness","materialEmissive","waterAbsorption"};
    private final PackRepository.Pack pack;
    private final String[] sources;
    private final float[] uniforms=new float[252],matrixScratch=new float[16];
    private final Matrix4f inverse=new Matrix4f();
    private String status="Shaderpack prepared; awaiting Direct DX12 world output";
    private boolean rejected;

    public DirectPipeline(PackRepository.Pack pack,Map<String,String> macros,Map<String,String> defines) throws java.io.IOException {
        this.pack=pack;
        if(pack.uniformOptions().size()>32)throw new IllegalArgumentException("At most 32 native pack controls");
        if(!pack.canRunDirect())throw new IllegalArgumentException("Pack requires direct.hlsl for Direct DX12");
        String hlsl=pack.hlsl()!=null?DefineOptions.apply(pack.hlsl(),defines):GlProgram.resource("native-composite.hlsl");
        sources=new String[]{ShaderMacros.inject(hlsl,ShaderSourceLoader.Dialect.HLSL,macros,PackRepository.HLSL)};
    }
    @Override public Kind kind(){return Kind.DIRECT;}
    @Override public PackRepository.Pack pack(){return pack;}
    @Override public boolean rejected(){return rejected;}
    @Override public String status(){return status;}
    private double value(String key){return PackOptionStore.live().engine(pack,key);}
    @Override public void render(float partial){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Matrix4f projection=FrameUniforms.projection,view=FrameUniforms.view;
        try{
            var serviceType=Class.forName("com.redondoguibi.direct.api.DirectRenderService");
            Object service=serviceType.getMethod("getInstance").invoke(null);
            Arrays.fill(uniforms,0);for(int i=0;i<KEYS.length;i++)uniforms[i]=(float)value(KEYS[i]);
            var options=pack.uniformOptions();
            for(int i=0;i<options.size();i++)uniforms[32+i]=(float)PackOptionStore.live().value(pack,options.get(i));
            put(projection,64);put(inverse.set(projection).invert(),80);put(inverse.set(view).invert(),96);put(inverse.identity(),112);
            float angle=mc.level.getSunAngle(partial);var sun=new Vector3f(-(float)Math.sin(angle),(float)Math.cos(angle),0);view.transformDirection(sun);
            uniforms[128]=sun.x;uniforms[129]=sun.y;uniforms[130]=sun.z;
            if(DirectShaderBridge.nativeGeometryActive())uniforms[11]=0;
            boolean skylight=mc.level.dimensionType().hasSkyLight();
            uniforms[131]=skylight?Math.max(0,(float)Math.cos(angle)):0;
            uniforms[132]=mc.level.getRainLevel(partial);uniforms[133]=mc.level.getThunderLevel(partial);
            uniforms[134]=(mc.level.getGameTime()%240000+partial)/20f;uniforms[135]=(int)Math.floorMod(mc.level.getDayTime(),24000);
            if(!skylight){uniforms[10]=0;uniforms[13]=0;}
            var camera=mc.gameRenderer.getMainCamera();
            if(camera.getFluidInCamera()!=FogType.NONE)uniforms[13]=0;
            var pos=camera.getPosition();var block=camera.getBlockPosition();
            uniforms[136]=(float)pos.x;uniforms[137]=(float)pos.y;uniforms[138]=(float)pos.z;
            uniforms[139]=mc.level.getBrightness(LightLayer.BLOCK,block)/15f;uniforms[140]=mc.level.getBrightness(LightLayer.SKY,block)/15f;
            uniforms[141]=camera.getFluidInCamera()==FogType.WATER?1:camera.getFluidInCamera()==FogType.LAVA?2:0;
            uniforms[142]=(float)PackOptionStore.live().quality(pack);uniforms[143]=PackOptionStore.live().fxaa(pack)?1:0;
            var sky=mc.level.getSkyColor(pos,partial);uniforms[144]=(float)sky.x;uniforms[145]=(float)sky.y;uniforms[146]=(float)sky.z;
            boolean rendered=(Boolean)serviceType.getMethod("renderWorld",String[].class,float[].class).invoke(service,sources,uniforms);
            String failure=(String)serviceType.getMethod("worldFailure").invoke(service);
            // Direct remembers rejected sources and returns false for them until they change.
            if(!rendered&&!failure.isEmpty())rejected=true;
            status=rendered?"Direct DX12: rendering":failure.isEmpty()?"Direct DX12: waiting for native world output":"Direct DX12 rejected the HLSL: "+failure;
        }catch(Exception|LinkageError ex){
            Throwable root=ex;while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();
            status="Direct DX12 unavailable: "+root.getClass().getSimpleName()+": "+root.getMessage();
        }
    }
    private void put(Matrix4f matrix,int start){matrix.get(matrixScratch);System.arraycopy(matrixScratch,0,uniforms,start,16);}
    @Override public void close(){}
}
