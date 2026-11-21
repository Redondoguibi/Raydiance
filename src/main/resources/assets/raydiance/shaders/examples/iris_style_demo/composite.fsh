#version 150
// Bright pass: world color (colortex0) -> half-resolution colortex1.
#include "/lib/common.glsl"
/* RENDERTARGETS: 1 */
uniform sampler2D colortex0;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 c = texture(colortex0, texCoord).rgb;
    float knee = max(luma(c) - BLOOM_THRESHOLD, 0.0) / max(1.0 - BLOOM_THRESHOLD, 0.001);
    fragColor = vec4(c * knee, 1.0);
}
