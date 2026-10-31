package com.prismora.render;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Short-lived bounded capture, including ShaderC messages emitted by worker threads. */
final class CompileDiagnostics extends AbstractAppender implements AutoCloseable {
 private final StringBuilder text=new StringBuilder();
 private final org.apache.logging.log4j.core.Logger root;
 CompileDiagnostics() {
  super("PrismoraCompileDiagnostics",null,null,true,Property.EMPTY_ARRAY);
  root=(org.apache.logging.log4j.core.Logger)LogManager.getRootLogger();start();root.addAppender(this);
 }
 @Override public synchronized void append(LogEvent event) {
  if(event.getLevel().isLessSpecificThan(Level.INFO) || text.length()>=8192)return;
  String message=event.getMessage().getFormattedMessage();
  if(event.getThrown()!=null)message+="\n"+event.getThrown().getMessage();
  text.append(message,0,Math.min(message.length(),8192-text.length())).append('\n');
 }
 synchronized String details() { return text.toString(); }
 @Override public void close() { root.removeAppender(this);stop(); }
}
