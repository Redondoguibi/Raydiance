package com.redondoguibi.raydiance.client.render;

import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import java.util.*;

/** Reject reserved uniform type mismatches before replacing a working pipeline. */
final class ShaderUniformContract {
    private static final Map<String,Integer> TYPES=new HashMap<>();
    static {
        add(GL20.GL_SAMPLER_2D,"Scene Depth BloomTex ShadowTex OriginalScene");
        add(GL20.GL_FLOAT,"ShadowReady Saturation Contrast Brightness Exposure Gamma Temperature Vignette Strength Bloom Atmosphere Shadows Daylight Rain Time Ssao GodRays Sharpen ToneMapping FrameTime FrameTimeCounter Wetness Thunder");
        add(GL20.GL_FLOAT_VEC2,"Texel InputSize OutputSize ViewSize EyeBrightness");
        add(GL20.GL_FLOAT_VEC3,"Sun SkyColor CameraPosition CameraFraction CameraDelta");
        add(GL20.GL_INT_VEC3,"CameraBlock");
        add(GL20.GL_INT,"Fxaa Steps PassIndex FrameCounter HistoryValid WorldTime WorldDay EyeInWater");
        add(GL20.GL_FLOAT_MAT4,"Projection InvProjection View InvView PreviousView PreviousProjection ShadowTransform");
    }
    private static void add(int type,String names){for(String name:names.split(" "))TYPES.put(name,type);}
    static void validate(int program){
        try(MemoryStack stack=MemoryStack.stackPush()){
            var size=stack.mallocInt(1);var type=stack.mallocInt(1);
            for(int i=0;i<GL20.glGetProgrami(program,GL20.GL_ACTIVE_UNIFORMS);i++){
                String name=GL20.glGetActiveUniform(program,i,256,size,type).replace("[0]","");
                Integer expected=TYPES.get(name);
                if(expected!=null&&(expected!=type.get(0)||size.get(0)!=1))throw new IllegalArgumentException("Invalid type for reserved uniform "+name);
            }
        }
    }
    private ShaderUniformContract(){}
}
