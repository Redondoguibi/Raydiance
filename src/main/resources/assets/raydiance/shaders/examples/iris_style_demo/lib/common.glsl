#ifndef COMMON_GLSL
#define COMMON_GLSL
// Options are discovered from these definitions (Iris style) and edited in F8 > Shaderpacks.
#define BLOOM // Glow around bright areas (three half-resolution programs).
#define BLOOM_STRENGTH 0.35 // [0.0 0.15 0.25 0.35 0.5 0.75] Bloom intensity.
#define BLOOM_THRESHOLD 0.75 // [0.5 0.65 0.75 0.85 0.95] Brightness where bloom starts.
#define TONEMAP 1 // [0 1 2] 0 = off, 1 = Reinhard, 2 = ACES approximation.
#define VIGNETTE // Darkens the screen corners.
//#define HAZE // Distance haze tinted by the fog color.

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 tonemap(vec3 c) {
#if TONEMAP == 1
    return c / (1.0 + luma(c));
#elif TONEMAP == 2
    c *= 0.6;
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
#else
    return c;
#endif
}

vec4 blur9(sampler2D tex, vec2 uv, vec2 direction) {
    vec2 texel = direction / vec2(textureSize(tex, 0));
    vec4 sum = texture(tex, uv) * 0.2270270270;
    sum += (texture(tex, uv + texel * 1.3846153846) + texture(tex, uv - texel * 1.3846153846)) * 0.3162162162;
    sum += (texture(tex, uv + texel * 3.2307692308) + texture(tex, uv - texel * 3.2307692308)) * 0.0702702703;
    return sum;
}
#endif
