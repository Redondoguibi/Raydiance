#version 150
// Horizontal blur. Reading and writing colortex1 is safe: the buffer ping-pongs.
#include "/lib/common.glsl"
/* RENDERTARGETS: 1 */
uniform sampler2D colortex1;
in vec2 texCoord;
out vec4 fragColor;
void main() { fragColor = blur9(colortex1, texCoord, vec2(1.0, 0.0)); }
