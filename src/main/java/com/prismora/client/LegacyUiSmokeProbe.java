package com.prismora.client;
import com.prismora.Prismora;
import com.prismora.client.gui.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
public final class LegacyUiSmokeProbe {
 private static int ticks,state,wait,search;
 public static void tick(){
  var mc=Minecraft.getInstance();if(++ticks>2000)throw new IllegalStateException("Legacy UI probe timed out");
  if(state==0&&mc.isGameLoadFinished()&&mc.gui.screen()!=null&&mc.gui.overlay()==null&&ticks>80){
   var c=ShaderController.INSTANCE;c.refresh();var e=c.entries().stream().filter(p->p.id().equals(System.getProperty("prismora.legacyPack"))).findFirst().orElseThrow();c.select(e);state=1;
   if(Boolean.getBoolean("prismora.compactUiSmoke")){mc.options.guiScale().set(2);mc.getWindow().setWindowed(1920,1080);}
  }
  if(state==1&&!ShaderController.INSTANCE.busy()&&mc.gui.overlay()==null){
   var pack=ShaderController.INSTANCE.legacyInspected();if(pack==null||pack.screens().size()<2||pack.options().size()<2)throw new IllegalStateException("Real pack metadata missing");
   mc.gui.setScreen(new LegacyOptionsScreen(new TitleScreen(),pack,ShaderController.INSTANCE.legacyValues(pack.id())));state=2;wait=0;
  }
  if(state==2&&++wait>30){if(Boolean.getBoolean("prismora.compactUiSmoke"))checkBounds(mc.gui.screen());Screenshot.grab(mc.gameDirectory,"prismora-legacy-root.png",mc.gameRenderer.mainRenderTarget(),1,m->{});state=3;}
  if(state==3&&mc.gui.screen() instanceof LegacyOptionsScreen screen){
   var button=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().contains("Performance Settings")).map(c->(Button)c).findFirst();
   if(button.isPresent()){button.get().onPress(null);state=4;wait=0;}
   else{if(++search>10)throw new IllegalStateException("Pack Performance Settings submenu missing");screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(">")).map(c->(Button)c).findFirst().orElseThrow().onPress(null);}
  }
  if(state==4&&++wait>40&&mc.gui.screen() instanceof LegacyOptionsScreen screen){
   if(screen.children().size()<8)throw new IllegalStateException("Pack submenu widgets missing");
   Screenshot.grab(mc.gameDirectory,"prismora-legacy-performance.png",mc.gameRenderer.mainRenderTarget(),1,m->{});
   Prismora.LOGGER.info("PRISMORA_LEGACY_UI_PASS: real shaderpack categories options profiles and submenu rendered");state=5;wait=0;
  }
  if(state==5&&++wait>20){if(Boolean.getBoolean("prismora.compactUiSmoke")){checkBounds(mc.gui.screen());mc.gui.setScreen(new ShaderErrorScreen(mc.gui.screen(),ShaderController.INSTANCE.error()));state=6;wait=0;}else{mc.stop();state=10;}}
  if(state==6&&++wait>25){checkBounds(mc.gui.screen());Screenshot.grab(mc.gameDirectory,"prismora-compact-errors.png",mc.gameRenderer.mainRenderTarget(),1,m->{});mc.getWindow().setWindowed(854,480);state=7;wait=0;}
  if(state==7&&++wait>40){var pack=ShaderController.INSTANCE.legacyInspected();mc.gui.setScreen(new LegacyOptionsScreen(new TitleScreen(),pack,ShaderController.INSTANCE.legacyValues(pack.id())));state=8;wait=0;}
  if(state==8&&++wait>25){checkBounds(mc.gui.screen());Screenshot.grab(mc.gameDirectory,"prismora-compact-small.png",mc.gameRenderer.mainRenderTarget(),1,m->{});Prismora.LOGGER.info("PRISMORA_COMPACT_UI_PASS: 1920x1080 and 854x480, centered controls within 420 GUI units, no overlaps");state=9;wait=0;}
  if(state==9&&++wait>15){mc.stop();state=10;}
 }
 private static void checkBounds(net.minecraft.client.gui.screens.Screen screen){
  var widgets=screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractWidget).map(c->(net.minecraft.client.gui.components.AbstractWidget)c).toList();
  int left=Integer.MAX_VALUE,right=0;
  for(var w:widgets){if(w.getX()<0||w.getY()<0||w.getRight()>screen.width||w.getBottom()>screen.height)throw new IllegalStateException("Control outside viewport");left=Math.min(left,w.getX());right=Math.max(right,w.getRight());}
  if(right-left>420||Math.abs((left+right)-screen.width)>4)throw new IllegalStateException("Menu is not compact and centered");
  for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);if(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())throw new IllegalStateException("Overlapping controls");}
 }
}
