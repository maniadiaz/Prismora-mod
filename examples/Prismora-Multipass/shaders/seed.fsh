#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneColorSampler;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
#include "prismora:uniforms.glsl"
void main(){ fragColor=vec4(prismoraHasWorld()>0.5?vec3(0.1,0.4,0.2):vec3(1,0,0),1); }
