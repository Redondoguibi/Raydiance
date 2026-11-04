#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position;in vec4 Color;in vec2 UV0;in ivec2 UV2;in vec3 Normal;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat,ProjMat;
uniform vec3 ChunkOffset,Camera;
uniform int FogShape;
uniform float Time,Wind,Water;
uniform vec4 WaterStill,WaterFlow;
uniform vec4 Plant0;
uniform vec4 Plant1;
uniform vec4 Plant2;
uniform vec4 Plant3;
uniform vec4 Plant4;
uniform vec4 Plant5;
uniform vec4 Plant6;
uniform vec4 Plant7;
uniform vec4 Plant8;
uniform vec4 Plant9;
uniform vec4 Plant10;
uniform vec4 Plant11;
uniform vec4 Plant12;
uniform vec4 Plant13;
uniform vec4 Plant14;
uniform vec4 Plant15;

out float vertexDistance;out vec4 vertexColor;out vec2 texCoord0;out vec3 viewPosition;out float waterMask;
bool inside(vec4 r){return all(greaterThan(UV0,r.xy+vec2(0.000001)))&&all(lessThan(UV0,r.zw-vec2(0.000001)));}
void main(){vec3 pos=Position+ChunkOffset;vec3 world=pos+Camera;
 bool plant=inside(Plant0)||inside(Plant1)||inside(Plant2)||inside(Plant3)||inside(Plant4)||inside(Plant5)||inside(Plant6)||inside(Plant7)||inside(Plant8)||inside(Plant9)||inside(Plant10)||inside(Plant11)||inside(Plant12)||inside(Plant13)||inside(Plant14)||inside(Plant15);
 if(plant&&Wind>0.0){float phase=Time*1.7+world.x*0.45+world.z*0.37;pos.xz+=vec2(sin(phase),cos(phase*0.83))*0.035*Wind*(0.3+0.7*fract(Position.y));}
 waterMask=(inside(WaterStill)||inside(WaterFlow))?1.0:0.0;
 if(waterMask>0.5&&Normal.y>0.5)pos.y+=sin(world.x*0.8+Time*1.2)*sin(world.z*0.7+Time)*Water*0.025;
 vec4 vp=ModelViewMat*vec4(pos,1);viewPosition=vp.xyz;gl_Position=ProjMat*vp;
 vertexDistance=fog_distance(pos,FogShape);vertexColor=Color*minecraft_sample_lightmap(Sampler2,UV2);texCoord0=UV0;}
