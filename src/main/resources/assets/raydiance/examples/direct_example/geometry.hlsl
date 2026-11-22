VertexOutput packVertex(VertexInput input, VertexOutput vanilla) {
    return vanilla;
}
float4 packOpaque(VertexOutput input, float4 vanilla) { return vanilla; }
float4 packCutout(VertexOutput input, float4 vanilla) {
    clip(vanilla.a - alphaCutoff);
    return float4(vanilla.rgb, 1);
}
float4 packTranslucent(VertexOutput input, float4 vanilla) {
    clip(vanilla.a - 0.001);
    if (input.material != 5 || PackRefraction == 0) return vanilla;
    uint width, height;
    SceneColor.GetDimensions(width, height);
    int2 pixel = int2(input.position.xy);
    int2 offset = int2(sin(pixel.y * .1 + sunTime.w) * 4.0 * PackRefraction, 0);
    int2 refracted = clamp(pixel + offset, int2(0, 0), int2(width - 1, height - 1));
    if (SceneDepth.Load(int3(refracted, 0)) < input.position.z) refracted = pixel;
    float3 background = SceneColor.Load(int3(refracted, 0)).rgb;
    return float4(lerp(vanilla.rgb, background, PackRefraction), vanilla.a);
}

void packShadow(VertexOutput input, float alpha) { clip(alpha - alphaCutoff); }
