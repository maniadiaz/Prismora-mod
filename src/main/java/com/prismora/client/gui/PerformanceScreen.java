package com.prismora.client.gui;
import com.prismora.client.ShaderController;
import com.prismora.performance.PerformanceSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class PerformanceScreen extends Screen {
 private final Screen parent;private final ShaderController controller=ShaderController.INSTANCE;
 private PerformanceSettings draft;
 public PerformanceScreen(Screen parent){super(Component.translatable("prismora.performance"));this.parent=parent;draft=controller.performance();}
 private Component state(String key,boolean on){return Component.translatable(key).append(": ").append(Component.translatable(on?"options.on":"options.off"));}
 @Override protected void init(){
  int left=width/2-150;
  var presets=new PerformanceSettings.Profile[]{PerformanceSettings.Profile.ENTRY,PerformanceSettings.Profile.LOW,PerformanceSettings.Profile.MEDIUM,PerformanceSettings.Profile.HIGH};
  for(int i=0;i<4;i++){var preset=presets[i];addRenderableWidget(Button.builder(Component.translatable("prismora.profile."+preset.name().toLowerCase(java.util.Locale.ROOT)),b->{draft=PerformanceSettings.preset(preset);rebuildWidgets();}).bounds(left+i*76,38,72,20).build());}
  addRenderableWidget(new AbstractSliderButton(left,64,300,20,Component.empty(),(draft.scale()-0.5)/0.5){
   {updateMessage();}protected void updateMessage(){setMessage(Component.translatable("prismora.scale").append(": "+Math.round(draft.scale()*100)+"%"));}
   protected void applyValue(){draft=draft.custom(Math.round((0.5+value*0.5)*100)/100.0,draft.bloom(),draft.adaptive(),draft.targetFps(),draft.fallback());}
  });
  addRenderableWidget(Button.builder(state("prismora.bloom",draft.bloom()),b->{draft=draft.custom(draft.scale(),!draft.bloom(),draft.adaptive(),draft.targetFps(),draft.fallback());rebuildWidgets();}).bounds(left,90,146,20).build());
  addRenderableWidget(Button.builder(state("prismora.adaptive",draft.adaptive()),b->{draft=draft.custom(draft.scale(),draft.bloom(),!draft.adaptive(),draft.targetFps(),draft.fallback());rebuildWidgets();}).bounds(left+154,90,146,20).build());
  addRenderableWidget(Button.builder(Component.translatable("prismora.target").append(": "+draft.targetFps()+" FPS"),b->{draft=draft.custom(draft.scale(),draft.bloom(),draft.adaptive(),draft.targetFps()==30?45:draft.targetFps()==45?60:30,draft.fallback());rebuildWidgets();}).bounds(left,116,300,20).build());
  addRenderableWidget(Button.builder(state("prismora.fallback",draft.fallback()),b->{draft=draft.custom(draft.scale(),draft.bloom(),draft.adaptive(),draft.targetFps(),!draft.fallback());rebuildWidgets();}).bounds(left,142,300,20).build());
  var apply=Button.builder(Component.translatable("prismora.apply"),b->{controller.performance(draft);}).bounds(left,height-32,146,20).build();apply.active=!controller.busy();addRenderableWidget(apply);
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left+154,height-32,146,20).build());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial){super.extractRenderState(g,x,y,partial);g.centeredText(font,title,width/2,14,0xFFFFFFFF);g.centeredText(font,Component.translatable("prismora.profile."+draft.profile().name().toLowerCase(java.util.Locale.ROOT)),width/2,172,0xFFBBBBBB);
  var metrics=com.prismora.client.AdaptivePerformance.metrics();if(metrics!=null)g.centeredText(font,Component.literal(String.format(java.util.Locale.ROOT,"%.0f FPS | p95 %.1f ms | GPU %.1f ms",metrics.fps(),metrics.frameP95(),metrics.gpuP95())),width/2,184,0xFFBBBBBB);}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
