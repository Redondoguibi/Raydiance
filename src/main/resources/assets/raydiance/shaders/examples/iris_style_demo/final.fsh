#version 150
// Final composition. The uniform names follow Iris conventions; see the Raydiance README for meanings.
#include "/lib/common.glsl"
uniform sampler2D colortex0, colortex1, depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform vec3 fogColor;
uniform float far;
in vec2 texCoord;
out vec4 fragColor;

vec3 grade(vec3 c);

void main() {
    vec4 scene = texture(colortex0, texCoord);
    vec3 c = scene.rgb;
#ifdef BLOOM
    c += texture(colortex1, texCoord).rgb * BLOOM_STRENGTH;
#endif
#ifdef HAZE
    float depth = texture(depthtex0, texCoord).r;
    vec4 view = gbufferProjectionInverse * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
    float dist = length(view.xyz / view.w);
    if (depth < 1.0) c = mix(c, fogColor, clamp(dist / max(far, 16.0), 0.0, 1.0) * 0.35);
#endif
    c = grade(tonemap(c));
#ifdef VIGNETTE
    vec2 d = texCoord - 0.5;
    c *= 1.0 - dot(d, d) * 0.6;
#endif
    fragColor = vec4(c, scene.a);
}

vec3 grade(vec3 c) { return c; }
