#ifndef PRISMORA_UNIFORMS
#define PRISMORA_UNIFORMS
uniform sampler2D PrismoraDataSampler;
float prismoraValue(int slot) {
 uvec4 b=uvec4(round(texelFetch(PrismoraDataSampler,ivec2(slot,0),0)*255.0));
 return uintBitsToFloat(b.r | (b.g<<8u) | (b.b<<16u) | (b.a<<24u));
}
vec2 prismoraResolution(){ return vec2(prismoraValue(0),prismoraValue(1)); }
float prismoraTime(){ return prismoraValue(2); }
float prismoraDayTicks(){ return prismoraValue(3); }
float prismoraSunAngle(){ return prismoraValue(4); }
vec3 prismoraCamera(){ return vec3(prismoraValue(5),prismoraValue(6),prismoraValue(7)); }
vec3 prismoraSunDirection(){ return vec3(prismoraValue(8),prismoraValue(9),prismoraValue(10)); }
float prismoraHasWorld(){ return prismoraValue(11); }
mat4 prismoraMatrix(int slot){
 return mat4(vec4(prismoraValue(slot),prismoraValue(slot+1),prismoraValue(slot+2),prismoraValue(slot+3)),
 vec4(prismoraValue(slot+4),prismoraValue(slot+5),prismoraValue(slot+6),prismoraValue(slot+7)),
 vec4(prismoraValue(slot+8),prismoraValue(slot+9),prismoraValue(slot+10),prismoraValue(slot+11)),
 vec4(prismoraValue(slot+12),prismoraValue(slot+13),prismoraValue(slot+14),prismoraValue(slot+15)));
}
#endif
