#version 150
// Nether variant (Iris world-1 folder): warmer grade, no distance haze.
#include "/lib/common.glsl"
uniform sampler2D colortex0, colortex1;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 scene = texture(colortex0, texCoord);
    vec3 c = scene.rgb;
#ifdef BLOOM
    c += texture(colortex1, texCoord).rgb * BLOOM_STRENGTH * 1.3;
#endif
    c = tonemap(c) * vec3(1.06, 0.98, 0.92);
#ifdef VIGNETTE
    vec2 d = texCoord - 0.5;
    c *= 1.0 - dot(d, d) * 0.8;
#endif
    fragColor = vec4(c, scene.a);
}
