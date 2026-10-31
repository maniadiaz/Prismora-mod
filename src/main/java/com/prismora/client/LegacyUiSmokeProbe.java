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
  }
  if(state==1&&!ShaderController.INSTANCE.busy()&&mc.gui.overlay()==null){
   var pack=ShaderController.INSTANCE.legacyInspected();if(pack==null||pack.screens().size()<2||pack.options().size()<2)throw new IllegalStateException("Real pack metadata missing");
   mc.gui.setScreen(new LegacyOptionsScreen(new TitleScreen(),pack,ShaderController.INSTANCE.legacyValues(pack.id())));state=2;wait=0;
  }
  if(state==2&&++wait>30){Screenshot.grab(mc.gameDirectory,"prismora-legacy-root.png",mc.gameRenderer.mainRenderTarget(),1,m->{});state=3;}
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
  if(state==5&&++wait>20){mc.stop();state=6;}
 }
}
