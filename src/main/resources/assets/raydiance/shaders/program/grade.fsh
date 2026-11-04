#version 150

uniform sampler2D DiffuseSampler;
uniform float Saturation;
uniform float Contrast;
uniform float Brightness;
uniform float Exposure;
uniform float Gamma;
uniform float Temperature;
uniform float Vignette;
uniform float Strength;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    vec3 color = original.rgb * (Brightness * exp2(Exposure));
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luminance), color, Saturation);
    color = (color - 0.5) * Contrast + 0.5;
    color.r += Temperature * 0.08;
    color.b -= Temperature * 0.08;
    color = pow(max(color, vec3(0.0)), vec3(1.0 / max(Gamma, 0.01)));
    float radial = length((texCoord - 0.5) * vec2(1.0, 0.85));
    color *= 1.0 - Vignette * smoothstep(0.20, 0.72, radial);
    fragColor = vec4(mix(original.rgb, clamp(color, 0.0, 1.0), Strength), original.a);
}
