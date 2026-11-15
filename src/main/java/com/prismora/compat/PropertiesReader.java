package com.prismora.compat;
import java.io.*;
import java.util.*;

public final class PropertiesReader {
 private PropertiesReader() {}
 public static Map<String,String> parse(String text)throws IOException {
  if(text.length()>1024*1024)throw new IOException("Properties file too large");
  var result=new LinkedHashMap<String,String>();
  var properties=new Properties(){
   @Override public synchronized Object put(Object key,Object value){return result.put((String)key,(String)value);}
  };
  try{properties.load(new StringReader(text));}catch(IllegalArgumentException e){throw new IOException("Invalid properties escape",e);}
  if(result.size()>16384)throw new IOException("Too many properties");
  return Collections.unmodifiableMap(result);
 }
}
