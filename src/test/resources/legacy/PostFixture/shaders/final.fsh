#version 120
#define GAIN // toggle
#define EXPOSURE 2.0 // [1.0 2.0]
uniform sampler2D colortex0;
uniform float viewWidth;
varying vec2 texcoord;
#include "/lib/color.glsl"
void main(){gl_FragColor=vec4(applyGain(texture2D(colortex0,texcoord).rgb),1.0)+vec4(0.0/viewWidth);}
