package com.prismora.performance;

/** User choices, independent of GPU branding. Scaling affects postprocessing, not chunk rendering. */
public record PerformanceSettings(Profile profile, double scale, boolean bloom, boolean adaptive, int targetFps, boolean fallback) {
    public enum Profile { ENTRY, LOW, MEDIUM, HIGH, CUSTOM }
    public PerformanceSettings {
        if(profile==null || !Double.isFinite(scale) || scale<0.5 || scale>1.0 || targetFps<30 || targetFps>120)
            throw new IllegalArgumentException("Invalid performance settings");
    }
    public static PerformanceSettings preset(Profile profile) {
        return switch(profile) {
            case ENTRY -> new PerformanceSettings(profile,0.5,false,true,30,true);
            case LOW -> new PerformanceSettings(profile,0.5,true,false,30,true);
            case MEDIUM -> new PerformanceSettings(profile,0.75,true,false,60,true);
            case HIGH -> new PerformanceSettings(profile,1.0,true,false,60,true);
            case CUSTOM -> new PerformanceSettings(profile,0.75,true,false,30,true);
        };
    }
    public PerformanceSettings custom(double newScale,boolean newBloom,boolean newAdaptive,int fps,boolean newFallback) {
        return new PerformanceSettings(Profile.CUSTOM,newScale,newBloom,newAdaptive,fps,newFallback);
    }
    public PerformanceSettings reduced() {
        if(scale>0.5)return custom(Math.max(0.5,Math.round((scale-0.1)*100.0)/100.0),bloom,adaptive,targetFps,fallback);
        return custom(scale,false,adaptive,targetFps,fallback);
    }
    public boolean minimum() { return scale<=0.5 && !bloom; }
    public long estimatedPixels(int width,int height) { return (long)Math.max(1,(int)Math.ceil(width*scale))*Math.max(1,(int)Math.ceil(height*scale)); }
}
