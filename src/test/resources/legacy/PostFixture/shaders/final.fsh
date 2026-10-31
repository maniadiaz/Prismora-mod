#version 120
#define GAIN // toggle
#define EXPOSURE 2.0 // [1.0 2.0]
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform float viewWidth;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
varying vec2 texcoord;
#include "/lib/color.glsl"
void main(){vec4 probe=vec4(0.1,0.2,0.3,1.0);vec4 roundTrip=gbufferProjection*(gbufferProjectionInverse*probe);vec4 viewRoundTrip=gbufferModelView*(gbufferModelViewInverse*probe);if(abs(gbufferProjection[2][3]+1.0)>0.00001||abs(gbufferProjection[3][3])>0.00001||length(roundTrip-probe)>0.001||length(viewRoundTrip-probe)>0.001){gl_FragColor=vec4(1.0,0.0,1.0,1.0);return;}float d=texture2D(depthtex0,texcoord).r;if(isnan(d)||d<0.0||d>1.0||abs(d+texture2D(depthtex0Sampler,texcoord).r-1.0)>0.000001){gl_FragColor=vec4(1.0,0.0,1.0,1.0);return;}gl_FragColor=vec4(applyGain(texture2D(colortex0,texcoord).rgb),1.0)+vec4(0.0/viewWidth);}
