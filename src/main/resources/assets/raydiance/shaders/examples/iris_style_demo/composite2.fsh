#version 150
// Vertical blur.
#include "/lib/common.glsl"
/* RENDERTARGETS: 1 */
uniform sampler2D colortex1;
in vec2 texCoord;
out vec4 fragColor;
void main() { fragColor = blur9(colortex1, texCoord, vec2(0.0, 1.0)); }
