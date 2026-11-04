#version 150
uniform sampler2D Scene, Depth, BloomTex, ShadowTex;
uniform float ShadowReady;
uniform mat4 ShadowTransform;
uniform vec2 Texel;
uniform float Saturation,Contrast,Brightness,Exposure,Gamma,Temperature,Vignette,Strength;
uniform float Bloom,Atmosphere,Shadows,Daylight,Rain,Time;
uniform float Ssao,GodRays,Sharpen,ToneMapping;
uniform int Fxaa,Steps;
uniform mat4 Projection,InvProjection;
uniform vec3 Sun;
in vec2 texCoord;
out vec4 fragColor;
vec3 position(vec2 uv,float depth){vec4 p=InvProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1);return p.xyz/p.w;}
float luma(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}
vec3 aa(vec2 uv){
 vec3 c=texture(Scene,uv).rgb;if(Fxaa==0)return c;
 vec3 nw=texture(Scene,uv+Texel*vec2(-1,1)).rgb,ne=texture(Scene,uv+Texel).rgb;
 vec3 sw=texture(Scene,uv-Texel).rgb,se=texture(Scene,uv+Texel*vec2(1,-1)).rgb;
 float a=luma(nw),b=luma(ne),d=luma(sw),e=luma(se),m=luma(c);
 float lo=min(m,min(min(a,b),min(d,e))),hi=max(m,max(max(a,b),max(d,e)));
 if(hi-lo<max(0.0312,hi*0.125))return c;
 vec2 dir=vec2(-((a+b)-(d+e)),(a+d)-(b+e));
 float reduce=max((a+b+d+e)*0.03125,0.0078125);
 dir=clamp(dir/(min(abs(dir.x),abs(dir.y))+reduce),vec2(-8),vec2(8))*Texel;
 vec3 x=0.5*(texture(Scene,uv+dir*(-1.0/6.0)).rgb+texture(Scene,uv+dir*(1.0/6.0)).rgb);
 vec3 y=x*0.5+0.25*(texture(Scene,uv-dir*0.5).rgb+texture(Scene,uv+dir*0.5).rgb);
 return luma(y)<lo||luma(y)>hi?x:y;
}
float projectedShadow(vec3 p){
 vec4 clip=ShadowTransform*vec4(p,1);vec3 uv=clip.xyz/clip.w*0.5+0.5;
 if(any(lessThan(uv,vec3(0.0)))||any(greaterThan(uv,vec3(1.0))))return 1.0;
 float edge=smoothstep(0.0,0.12,min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y)));
 vec2 pixel=1.0/vec2(textureSize(ShadowTex,0));float lit=0.0;
 float bias=max(0.0006,0.002*(1.0-abs(normalize(cross(dFdx(p),dFdy(p))).y)));
 for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)lit+=uv.z-bias<=texture(ShadowTex,uv.xy+vec2(x,y)*pixel).r?1.0:0.0;
 return 1.0-(1.0-lit/9.0)*edge*Shadows*0.65*Daylight*(1.0-Rain*0.7);
}
float shadow(vec3 p){
 if(Shadows<=0.0||Daylight<0.02)return 1.0;
 if(ShadowReady>0.5)return projectedShadow(p);
 float hit=0.0;
 for(int i=1;i<=20;i++){if(i>Steps)break;vec3 ray=p+normalize(Sun)*(0.04+float(i)*0.12);
 vec4 clip=Projection*vec4(ray,1);if(clip.w<=0.0)break;vec2 uv=clip.xy/clip.w*0.5+0.5;
 if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))break;
 float z=texture(Depth,uv).r;if(z>=0.999999)continue;
 vec3 occluder=position(uv,z);float delta=ray.z-occluder.z;
 if(delta< -0.035 && delta> -0.25){hit=1.0-float(i)/float(Steps+1);break;}}
 return 1.0-hit*Shadows*0.65*Daylight*(1.0-Rain*0.7);
}
float ambientOcclusion(vec3 p){
 if(Ssao<=0.0)return 1.0;
 vec3 crossNormal=cross(dFdx(p),dFdy(p));
 vec3 n=crossNormal/max(length(crossNormal),0.00001);if(dot(n,-p)<0.0)n=-n;
 float radius=clamp(40.0/max(1.0,-p.z),2.0,24.0),occlusion=0.0;
 int samples=min(Steps,12);
 for(int i=0;i<12;i++){
  if(i>=samples)break;float angle=float(i)*2.399963;
  vec2 offset=vec2(cos(angle),sin(angle))*sqrt((float(i)+0.5)/float(samples))*Texel*radius;
  vec2 uv=texCoord+offset;if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))continue;
  float d=texture(Depth,uv).r;if(d>=0.999999)continue;
  vec3 delta=position(uv,d)-p;float distance=length(delta);
  if(distance>0.02&&distance<4.0)occlusion+=max(dot(n,delta/distance)-0.08,0.0)*(1.0-smoothstep(0.5,4.0,distance));
 }
 return 1.0-clamp(occlusion/float(max(samples,1))*Ssao*1.8,0.0,0.7);
}
vec3 sunRays(){
 if(GodRays<=0.0||Daylight<0.05||Rain>0.95)return vec3(0);
 vec4 clip=Projection*vec4(normalize(Sun),0);if(clip.w<=0.001)return vec3(0);
 vec2 sunUv=clip.xy/clip.w*0.5+0.5;
 float proximity=1.0-smoothstep(0.15,0.85,length(texCoord-sunUv));if(proximity<=0.0)return vec3(0);
 vec2 stepUv=(sunUv-texCoord)*0.8/float(max(Steps,1));vec2 uv=texCoord;float lit=0.0,weight=1.0;
 for(int i=0;i<20;i++){if(i>=Steps)break;uv+=stepUv;if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))break;
  lit+=step(0.999999,texture(Depth,uv).r)*weight;weight*=0.94;
 }
 return vec3(1.0,0.75,0.4)*lit/float(max(Steps,1))*proximity*GodRays*Daylight*(1.0-Rain)*0.3;
}
void main(){
 vec4 original=texture(Scene,texCoord);float depth=texture(Depth,texCoord).r;
 vec3 c=aa(texCoord),p=position(texCoord,depth);
 if(Sharpen>0.0){vec3 neighbors=(texture(Scene,texCoord+vec2(Texel.x,0)).rgb+texture(Scene,texCoord-vec2(Texel.x,0)).rgb+
 texture(Scene,texCoord+vec2(0,Texel.y)).rgb+texture(Scene,texCoord-vec2(0,Texel.y)).rgb)*0.25;
 c=max(c+(original.rgb-neighbors)*Sharpen*0.35,vec3(0));}
 if(depth<0.999999)c*=shadow(p)*ambientOcclusion(p);
 c+=sunRays();
 if(Atmosphere>0.0){
  vec3 ray=normalize(p);float sunGlow=pow(max(dot(ray,normalize(Sun)),0.0),32.0);
  if(depth>=0.999999)c+=Atmosphere*(vec3(1.0,0.55,0.22)*sunGlow*Daylight+vec3(0.035,0.065,0.11)*(1.0-Daylight))*(1.0-Rain);
  else {float haze=1.0-exp(-length(p)*0.0008*Atmosphere);c=mix(c,vec3(0.5,0.62,0.75)*(0.2+0.8*Daylight),haze*(1.0+Rain));}
 }
 if(Bloom>0.0)c+=texture(BloomTex,texCoord).rgb*Bloom;
 vec3 graded=c*(Brightness*exp2(Exposure));float lum=dot(graded,vec3(0.2126,0.7152,0.0722));
 graded=mix(vec3(lum),graded,Saturation);graded=(graded-0.5)*Contrast+0.5;
 graded.r+=Temperature*0.08;graded.b-=Temperature*0.08;
 graded=pow(max(graded,vec3(0)),vec3(1.0/max(Gamma,0.01)));
 graded*=1.0-Vignette*smoothstep(0.2,0.72,length((texCoord-0.5)*vec2(1,0.85)));
 vec3 outputColor=max(mix(c,graded,Strength),vec3(0));
 if(ToneMapping>0.0){vec3 mapped=outputColor*(1.0+outputColor/4.0)/(1.0+outputColor);outputColor=mix(outputColor,mapped,ToneMapping);}
 fragColor=vec4(clamp(outputColor,0.0,1.0),original.a);
}
