#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main(){
 vec2 d=vec2(0,1.0/float(textureSize(SceneColorSampler,0).y));
 vec3 c=texture(SceneColorSampler,texCoord).rgb*0.227027;
 c+=(texture(SceneColorSampler,texCoord+d*1.384615).rgb+texture(SceneColorSampler,texCoord-d*1.384615).rgb)*0.316216;
 c+=(texture(SceneColorSampler,texCoord+d*3.230769).rgb+texture(SceneColorSampler,texCoord-d*3.230769).rgb)*0.070270;
 fragColor=vec4(c,1);
}
