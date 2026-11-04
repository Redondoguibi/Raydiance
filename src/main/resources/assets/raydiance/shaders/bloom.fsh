#version 150
uniform sampler2D Scene;
uniform vec2 Direction;
uniform float Threshold;
uniform int Extract;
in vec2 texCoord;
out vec4 fragColor;
vec3 sampleBright(vec2 uv){vec3 c=texture(Scene,uv).rgb;if(Extract!=0)c*=max(max(c.r,max(c.g,c.b))-Threshold,0.0)/max(1.0-Threshold,0.001);return c;}
void main(){vec3 c=sampleBright(texCoord)*0.227027;for(int i=1;i<=4;i++){float w=i==1?0.1945946:i==2?0.1216216:i==3?0.054054:0.016216;c+=(sampleBright(texCoord+Direction*float(i)*2.0)+sampleBright(texCoord-Direction*float(i)*2.0))*w;}fragColor=vec4(c,1);}
