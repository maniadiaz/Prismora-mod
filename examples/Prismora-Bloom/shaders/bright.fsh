#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main(){ vec3 c=texture(SceneColorSampler,texCoord).rgb; float b=max(c.r,max(c.g,c.b)); fragColor=vec4(c*max(b-0.65,0.0)/max(b,0.001),1); }
