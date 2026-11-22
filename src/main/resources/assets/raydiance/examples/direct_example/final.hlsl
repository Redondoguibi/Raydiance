Texture2D<float4> Previous : register(t0);
Texture2D<float> Depth : register(t1);
Texture2D<float4> Original : register(t2);
cbuffer Frame : register(b0) { float4 DirectFrame; float4 RaydianceData[63]; };

float4 main(float4 position : SV_Position) : SV_Target0 {
    int2 pixel = int2(position.xy);
    float4 initial = Original.Load(int3(pixel, 0));
    float4 graded = Previous.Load(int3(pixel, 0));
    float foreground = Depth.Load(int3(pixel, 0)) < 1 ? 1 : 0.45;
    return lerp(initial, graded, foreground);
}
