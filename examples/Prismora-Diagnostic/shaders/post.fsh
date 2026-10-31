#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
#include "prismora:uniforms.glsl"
void main(){
 bool valid=prismoraResolution().x>0.0 && prismoraResolution().y>0.0 && prismoraTime()>=0.0 && prismoraHasWorld()>0.5;
 fragColor=valid?vec4(0.2,0.8,0.4,1.0):vec4(1.0,0.0,0.0,1.0);
}
