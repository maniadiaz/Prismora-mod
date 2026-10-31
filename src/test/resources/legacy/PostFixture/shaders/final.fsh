#version 120
#define GAIN // toggle
#define EXPOSURE 2.0 // [1.0 2.0]
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform float viewWidth;
varying vec2 texcoord;
#include "/lib/color.glsl"
void main(){float d=texture2D(depthtex0,texcoord).r;if(isnan(d)||d<0.0||d>1.0||abs(d+texture2D(depthtex0Sampler,texcoord).r-1.0)>0.000001){gl_FragColor=vec4(1.0,0.0,1.0,1.0);return;}gl_FragColor=vec4(applyGain(texture2D(colortex0,texcoord).rgb),1.0)+vec4(0.0/viewWidth);}
