package com.prismora.compat;
import java.io.*;
import java.util.*;

public final class PropertiesReader {
 private PropertiesReader() {}
 public static Map<String,String> parse(String text)throws IOException {
  if(text.length()>1024*1024)throw new IOException("Properties file too large");
  var properties=new Properties();
  try{properties.load(new StringReader(text));}catch(IllegalArgumentException e){throw new IOException("Invalid properties escape",e);}
  if(properties.size()>16384)throw new IOException("Too many properties");
  var result=new LinkedHashMap<String,String>();for(String key:properties.stringPropertyNames())result.put(key,properties.getProperty(key));return Collections.unmodifiableMap(result);
 }
}
