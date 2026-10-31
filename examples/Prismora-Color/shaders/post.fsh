#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main() {
 vec3 color=texture(SceneColorSampler,texCoord).rgb;
 color=pow(max(color,vec3(0.0)),vec3(0.92))*vec3(1.06,1.0,0.94);
 fragColor=vec4(clamp(color,0.0,1.0),1.0);
}
