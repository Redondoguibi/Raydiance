#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0,RayScene,RayDepth;
uniform vec4 ColorModulator,FogColor;
uniform float FogStart,FogEnd,AlphaCutoff,Water,Reflections,Time,SceneReady;
uniform vec2 ScreenSize;
uniform mat4 ProjMat,InverseProj;
in float vertexDistance;in vec4 vertexColor;in vec2 texCoord0;in vec3 viewPosition;in float waterMask;
out vec4 fragColor;
vec3 unproject(vec2 uv,float z){vec4 p=InverseProj*vec4(uv*2.0-1.0,z*2.0-1.0,1);return p.xyz/p.w;}
void main(){vec4 color=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;if(color.a<AlphaCutoff)discard;
 if(waterMask>0.5&&SceneReady>0.5&&(Water>0.0||Reflections>0.0)){
 vec2 screen=gl_FragCoord.xy/ScreenSize;vec3 n=normalize(cross(dFdx(viewPosition),dFdy(viewPosition)));if(dot(n,-viewPosition)<0.0)n=-n;
 vec2 ripple=vec2(sin(Time*1.3+screen.y*80.0),cos(Time+screen.x*90.0))*Water*0.0015;
 vec2 refractUv=clamp(screen+ripple,vec2(0),vec2(1));
 float sceneZ=texture(RayDepth,refractUv).r;
 if(sceneZ>gl_FragCoord.z){vec3 behind=texture(RayScene,refractUv).rgb;color.rgb=mix(color.rgb,behind*vec3(0.76,0.9,0.94),Water*0.18);}
 if(Reflections>0.0){vec3 direction=reflect(normalize(viewPosition),n);vec3 reflected=vec3(0.35,0.48,0.62);float confidence=0.0;
 for(int i=1;i<=16;i++){vec3 point=viewPosition+direction*(0.2+float(i)*0.7);vec4 clip=ProjMat*vec4(point,1);if(clip.w<=0.0)break;
 vec2 uv=clip.xy/clip.w*0.5+0.5;if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))break;
 float d=texture(RayDepth,uv).r;if(d>=0.999999)continue;vec3 hit=unproject(uv,d);float delta=point.z-hit.z;
 if(delta< -0.04&&delta> -0.8){reflected=texture(RayScene,uv+ripple).rgb;confidence=smoothstep(0.0,0.1,min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y)));break;}}
 float fresnel=0.04+0.96*pow(1.0-max(dot(normalize(-viewPosition),n),0.0),5.0);
 color.rgb=mix(color.rgb,reflected,Reflections*fresnel*mix(0.2,0.7,confidence));}
 }
 fragColor=linear_fog(color,vertexDistance,FogStart,FogEnd,FogColor);}
