vec3 applyGain(vec3 color){
#ifdef GAIN
return color*EXPOSURE;
#else
return color;
#endif
}
