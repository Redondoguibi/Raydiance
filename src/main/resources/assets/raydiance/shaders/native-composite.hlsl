// Native DX12 world contract, API 1.5. Logical UVs use the GL bottom-left convention.
Texture2D<float4> DirectInput:register(t0); Texture2D<float> DirectDepth:register(t1); SamplerState DirectSampler:register(s0);
cbuffer DirectConstants:register(b0){float4 DirectFrameData;float4 DirectUniforms[63];};
float val(int i){return DirectUniforms[i/4][i%4];}
float4x4 mat(int i){return transpose(float4x4(DirectUniforms[i/4],DirectUniforms[i/4+1],DirectUniforms[i/4+2],DirectUniforms[i/4+3]));}
static float2 texCoord=float2(0,0); static float4 fragColor=float4(0,0,0,0);
#define Texel (1.0/DirectFrameData.yz)
#define ViewSize DirectFrameData.yz
#define Projection transpose(float4x4(DirectUniforms[16],DirectUniforms[17],DirectUniforms[18],DirectUniforms[19]))
#define InvProjection transpose(float4x4(DirectUniforms[20],DirectUniforms[21],DirectUniforms[22],DirectUniforms[23]))
#define InvView transpose(float4x4(DirectUniforms[24],DirectUniforms[25],DirectUniforms[26],DirectUniforms[27]))
#define ShadowTransform transpose(float4x4(DirectUniforms[28],DirectUniforms[29],DirectUniforms[30],DirectUniforms[31]))
#define Sun float3(DirectUniforms[32].x,DirectUniforms[32].y,DirectUniforms[32].z)
#define Daylight DirectUniforms[32].w
#define Rain DirectUniforms[33].x
#define Wetness DirectUniforms[33].x
#define Thunder DirectUniforms[33].y
#define Time DirectUniforms[33].z
#define WorldTime ((int)DirectUniforms[33].w)
#define CameraPosition float3(DirectUniforms[34].x,DirectUniforms[34].y,DirectUniforms[34].z)
#define EyeBrightness float2(DirectUniforms[34].w,DirectUniforms[35].x)
#define EyeInWater ((int)DirectUniforms[35].y)
#define Steps ((int)DirectUniforms[35].z==0?6:((int)DirectUniforms[35].z==1?12:20))
#define Fxaa ((int)DirectUniforms[35].w)
#define SkyColor float3(DirectUniforms[36].x,DirectUniforms[36].y,DirectUniforms[36].z)
#define ShadowReady 0.0
#define mix lerp
#define fract frac
#define dFdx ddx
#define dFdy ddy
bool2 lessThan(float2 a,float2 b){return a<b;} bool3 lessThan(float3 a,float3 b){return a<b;}
bool2 greaterThan(float2 a,float2 b){return a>b;} bool3 greaterThan(float3 a,float3 b){return a>b;}
float4 sampleScene(float2 uv){return DirectInput.SampleLevel(DirectSampler,float2(uv.x,1-uv.y),0);}
float4 sampleDepth(float2 uv){
    int2 size=(int2)DirectFrameData.yz;
    int2 pixel=clamp((int2)floor(float2(uv.x,1-uv.y)*size),int2(0,0),size-1);
    return DirectDepth.Load(int3(pixel,0)).xxxx;
}
float4 sampleShadowTex(float2 uv){return 1.0.xxxx;}
#define Saturation DirectUniforms[0].x
#define Contrast DirectUniforms[0].y
#define Brightness DirectUniforms[0].z
#define Exposure DirectUniforms[0].w
#define Gamma DirectUniforms[1].x
#define Temperature DirectUniforms[1].y
#define Vignette DirectUniforms[1].z
#define Strength DirectUniforms[1].w
#define Bloom DirectUniforms[2].x
#define BloomThreshold DirectUniforms[2].y
#define Atmosphere DirectUniforms[2].z
#define Shadows DirectUniforms[2].w
#define Ssao DirectUniforms[3].x
#define GodRays DirectUniforms[3].y
#define Sharpen DirectUniforms[3].z
#define ToneMapping DirectUniforms[3].w
#define FogDistance DirectUniforms[4].x
float4 sampleBloomTex(float2 uv){float3 color=0;float weight=0;[unroll]for(int y=-1;y<=1;y++){[unroll]for(int x=-1;x<=1;x++){float w=(x==0?2:1)*(y==0?2:1);float3 s=sampleScene(uv+float2(x,y)*Texel*2).rgb;color+=max(s-BloomThreshold,0)*w;weight+=w;}}return float4(color/weight,1);}
#define texture(T,uv) sample##T(uv)
float3 position(float2 uv,float depth){float4 p=mul(InvProjection,float4(uv*2.0-1.0,depth*2.0-1.0,1));return p.xyz/p.w;}
float luma(float3 c){return dot(c,float3(0.299,0.587,0.114));}
float3 aa(float2 uv){
 float3 c=texture(Scene,uv).rgb;if(Fxaa==0)return c;
 float3 nw=texture(Scene,uv+Texel*float2(-1,1)).rgb,ne=texture(Scene,uv+Texel).rgb;
 float3 sw=texture(Scene,uv-Texel).rgb,se=texture(Scene,uv+Texel*float2(1,-1)).rgb;
 float a=luma(nw),b=luma(ne),d=luma(sw),e=luma(se),m=luma(c);
 float lo=min(m,min(min(a,b),min(d,e))),hi=max(m,max(max(a,b),max(d,e)));
 if(hi-lo<max(0.0312,hi*0.125))return c;
 float2 dir=float2(-((a+b)-(d+e)),(a+d)-(b+e));
 float reduce=max((a+b+d+e)*0.03125,0.0078125);
 dir=clamp(dir/(min(abs(dir.x),abs(dir.y))+reduce),float2(-8,-8),float2(8,8))*Texel;
 float3 x=0.5*(texture(Scene,uv+dir*(-1.0/6.0)).rgb+texture(Scene,uv+dir*(1.0/6.0)).rgb);
 float3 y=x*0.5+0.25*(texture(Scene,uv-dir*0.5).rgb+texture(Scene,uv+dir*0.5).rgb);
 return luma(y)<lo||luma(y)>hi?x:y;
}
float projectedShadow(float3 p){
 float4 clip=mul(ShadowTransform,float4(p,1));float3 uv=clip.xyz/clip.w*0.5+0.5;
 if(any(lessThan(uv,float3(0.0,0.0,0.0)))||any(greaterThan(uv,float3(1.0,1.0,1.0))))return 1.0;
 float edge=smoothstep(0.0,0.12,min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y)));
 float2 pixel=1.0/float2(1024,1024);float lit=0.0;
 float bias=max(0.0006,0.002*(1.0-abs(normalize(cross(dFdx(p),dFdy(p))).y)));
 for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)lit+=uv.z-bias<=texture(ShadowTex,uv.xy+float2(x,y)*pixel).r?1.0:0.0;
 return 1.0-(1.0-lit/9.0)*edge*Shadows*0.65*Daylight*(1.0-Rain*0.7);
}
float shadow(float3 p){
 if(Shadows<=0.0||Daylight<0.02)return 1.0;
 if(ShadowReady>0.5)return projectedShadow(p);
 float hit=0.0;
 for(int i=1;i<=20;i++){if(i>Steps)break;float3 ray=p+normalize(Sun)*(0.04+float(i)*0.12);
 float4 clip=mul(Projection,float4(ray,1));if(clip.w<=0.0)break;float2 uv=clip.xy/clip.w*0.5+0.5;
 if(any(lessThan(uv,float2(0,0)))||any(greaterThan(uv,float2(1,1))))break;
 float z=texture(Depth,uv).r;if(z>=0.999999)continue;
 float3 occluder=position(uv,z);float delta=ray.z-occluder.z;
 if(delta< -0.035 && delta> -0.25){hit=1.0-float(i)/float(Steps+1);break;}}
 return 1.0-hit*Shadows*0.65*Daylight*(1.0-Rain*0.7);
}
float ambientOcclusion(float3 p){
 if(Ssao<=0.0)return 1.0;
 float3 crossNormal=cross(dFdx(p),dFdy(p));
 float3 n=crossNormal/max(length(crossNormal),0.00001);if(dot(n,-p)<0.0)n=-n;
 float radius=clamp(40.0/max(1.0,-p.z),2.0,24.0),occlusion=0.0;
 int samples=min(Steps,12);
 for(int i=0;i<12;i++){
  if(i>=samples)break;float angle=float(i)*2.399963;
  float2 offset=float2(cos(angle),sin(angle))*sqrt((float(i)+0.5)/float(samples))*Texel*radius;
  float2 uv=texCoord+offset;if(any(lessThan(uv,float2(0,0)))||any(greaterThan(uv,float2(1,1))))continue;
  float d=texture(Depth,uv).r;if(d>=0.999999)continue;
  float3 delta=position(uv,d)-p;float distance=length(delta);
  if(distance>0.02&&distance<4.0)occlusion+=max(dot(n,delta/distance)-0.08,0.0)*(1.0-smoothstep(0.5,4.0,distance));
 }
 return 1.0-clamp(occlusion/float(max(samples,1))*Ssao*1.8,0.0,0.7);
}
float3 sunRays(){
 if(GodRays<=0.0||Daylight<0.05||Rain>0.95)return float3(0,0,0);
 float4 clip=mul(Projection,float4(normalize(Sun),0));if(clip.w<=0.001)return float3(0,0,0);
 float2 sunUv=clip.xy/clip.w*0.5+0.5;
 float proximity=1.0-smoothstep(0.15,0.85,length(texCoord-sunUv));if(proximity<=0.0)return float3(0,0,0);
 float2 stepUv=(sunUv-texCoord)*0.8/float(max(Steps,1));float2 uv=texCoord;float lit=0.0,weight=1.0;
 for(int i=0;i<20;i++){if(i>=Steps)break;uv+=stepUv;if(any(lessThan(uv,float2(0,0)))||any(greaterThan(uv,float2(1,1))))break;
  lit+=step(0.999999,texture(Depth,uv).r)*weight;weight*=0.94;
 }
 return float3(1.0,0.75,0.4)*lit/float(max(Steps,1))*proximity*GodRays*Daylight*(1.0-Rain)*0.3;
}
void shade(){
 float4 original=texture(Scene,texCoord);
 bool geometryEffects=Shadows>0.0||Ssao>0.0||Atmosphere>0.0;
 float depth=1.0;float3 p=float3(0.0,0.0,0.0);
 if(geometryEffects){depth=texture(Depth,texCoord).r;p=position(texCoord,depth);}
 float3 c=aa(texCoord);
 if(Sharpen>0.0){float3 neighbors=(texture(Scene,texCoord+float2(Texel.x,0)).rgb+texture(Scene,texCoord-float2(Texel.x,0)).rgb+
 texture(Scene,texCoord+float2(0,Texel.y)).rgb+texture(Scene,texCoord-float2(0,Texel.y)).rgb)*0.25;
 c=max(c+(original.rgb-neighbors)*Sharpen*0.35,float3(0,0,0));}
 if(depth<0.999999&&(Shadows>0.0||Ssao>0.0))c*=shadow(p)*ambientOcclusion(p);
 c+=sunRays();
 if(Atmosphere>0.0){
  float3 ray=normalize(p);float sunGlow=pow(max(dot(ray,normalize(Sun)),0.0),32.0);
  if(depth>=0.999999)c+=Atmosphere*(float3(1.0,0.55,0.22)*sunGlow*Daylight+float3(0.035,0.065,0.11)*(1.0-Daylight))*(1.0-Rain);
  else {float haze=1.0-exp(-length(p)*0.0008*Atmosphere);c=mix(c,float3(0.5,0.62,0.75)*(0.2+0.8*Daylight),haze*(1.0+Rain));}
 }
 if(Bloom>0.0)c+=texture(BloomTex,texCoord).rgb*Bloom;
 float3 graded=c*(Brightness*exp2(Exposure));float lum=dot(graded,float3(0.2126,0.7152,0.0722));
 graded=mix(float3(lum,lum,lum),graded,Saturation);graded=(graded-0.5)*Contrast+0.5;
 graded.r+=Temperature*0.08;graded.b-=Temperature*0.08;
 graded=pow(max(graded,float3(0,0,0)),float3(1.0/max(Gamma,0.01),1.0/max(Gamma,0.01),1.0/max(Gamma,0.01)));
 graded*=1.0-Vignette*smoothstep(0.2,0.72,length((texCoord-0.5)*float2(1,0.85)));
 float3 outputColor=max(mix(c,graded,Strength),float3(0,0,0));
 if(ToneMapping>0.0){float3 mapped=outputColor*(1.0+outputColor/4.0)/(1.0+outputColor);outputColor=mix(outputColor,mapped,ToneMapping);}
 fragColor=float4(clamp(outputColor,0.0,1.0),original.a);
}

float4 main(float4 position:SV_Position):SV_Target0{texCoord=float2(position.x/DirectFrameData.y,1-position.y/DirectFrameData.z);shade();return fragColor;}
