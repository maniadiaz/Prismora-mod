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
#endif
