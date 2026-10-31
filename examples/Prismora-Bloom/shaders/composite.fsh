#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
uniform sampler2D BloomSampler;
layout(std140) uniform BloomOptions { float Strength; };
void main(){
 vec3 color=texture(SceneColorSampler,texCoord).rgb+texture(BloomSampler,texCoord).rgb*Strength;
 fragColor=vec4(clamp(color,0,1),1);
}
