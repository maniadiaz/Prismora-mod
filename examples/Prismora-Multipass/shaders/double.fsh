#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main(){ fragColor=vec4(texture(SceneColorSampler,texCoord).rgb*2.0,1.0); }
