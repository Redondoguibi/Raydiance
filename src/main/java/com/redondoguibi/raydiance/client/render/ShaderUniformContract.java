package com.redondoguibi.raydiance.client.render;

import java.util.List;

/** Reject reserved uniform type mismatches before replacing a working pipeline. Types come from {@link FrameUniforms}. */
final class ShaderUniformContract {
    static void validate(List<GlProgram.Active> uniforms){
        for(var u:uniforms){
            int expected=FrameUniforms.expectedType(u.name());
            if(expected!=0&&(expected!=u.type()||u.size()!=1))throw new IllegalArgumentException("Invalid type for reserved uniform "+u.name());
        }
    }
    private ShaderUniformContract(){}
}
