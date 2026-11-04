// Raydiance sample shader for Direct's optional HLSL extension API.
// A fullscreen pixel shader, not an Iris/OptiFine shaderpack.
Texture2D<float4> DirectInput : register(t0);
SamplerState DirectSampler : register(s0);

cbuffer RaydianceFrame : register(b0) {
    float4 DirectFrameData;
    float4 DirectUniforms[15];
};

float4 main(float4 position : SV_Position) : SV_Target0 {
    uint width, height;
    DirectInput.GetDimensions(width, height);
    float2 uv = position.xy / float2(max(width, 1u), max(height, 1u));
    float4 color = DirectInput.Sample(DirectSampler, uv);
    color.r = saturate(color.r * 1.035);
    color.g = saturate(color.g * 1.010);
    color.b = saturate(color.b * 0.975);
    return color;
}
