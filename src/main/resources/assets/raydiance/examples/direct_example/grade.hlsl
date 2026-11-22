Texture2D<float4> Previous : register(t0);
Texture2D<float> Depth : register(t1);
Texture2D<float4> Original : register(t2);
cbuffer Frame : register(b0) { float4 DirectFrame; float4 RaydianceData[63]; };

float4 main(float4 position : SV_Position) : SV_Target0 {
    float4 color = Previous.Load(int3(int2(position.xy), 0));
    return float4(lerp(color.rgb, color.rgb * float3(1.1, 1, 0.92), PackTint), color.a);
}
