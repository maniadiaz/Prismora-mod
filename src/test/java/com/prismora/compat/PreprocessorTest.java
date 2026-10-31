package com.prismora.compat;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class PreprocessorTest {
 @Test void rejectsMalformedAndDeepExpressions(){assertThrows(IOException.class,()->Expression.test("F(2",Map.of("F","@fn:x:x")));assertThrows(IOException.class,()->Expression.test("(".repeat(1000)+"1"+")".repeat(1000),Map.of()));}
 @Test void functionArgumentsAreSubstitutedSimultaneously()throws Exception{assertTrue(Expression.test("SUB(b,1)==2",Map.of("b","3","SUB","@fn:a,b:((a)-(b))")));}
 @Test void onlyCurrentOperatingSystemIsDefined(){var env=ShaderPreprocessor.environment();assertEquals(1,env.keySet().stream().filter(k->k.startsWith("MC_OS_")).count());}
 @Test void originalVersionControlsBranches()throws Exception{var p=pack("#version 120\n#if __VERSION__ == 120\nLEGACY\n#else\nMODERN\n#endif",Map.of());var source=new ShaderPreprocessor(p,Map.of(),Map.of()).process("shaders/final.fsh").source();assertTrue(source.contains("LEGACY"));assertFalse(source.contains("MODERN"));}
 private LegacyPack pack(String shader,Map<String,String> extra)throws Exception{var f=LegacyPackTest.fixture();f.put("shaders/final.fsh",shader.getBytes(StandardCharsets.UTF_8));extra.forEach((k,v)->f.put(k,v.getBytes(StandardCharsets.UTF_8)));return LegacyPack.inspect("test",f);}
 @Test void evaluatesBranchesBitMathAndShortCircuit()throws Exception{assertTrue(Expression.test("defined(A) && ((A << 1) == 8) && (0 && 1/0 || 1)",Map.of("A","4")));assertFalse(Expression.test("UNDEFINED || (A < 2)",Map.of("A","4")));assertTrue(Expression.test("A > 2 ? 1 : 1/0",Map.of("A","4")));}
 @Test void hexAndFunctionMacros()throws Exception{assertTrue(Expression.test("0xFF == 255",Map.of()));assertTrue(Expression.test("MAX(2,3)==3",Map.of("MAX","@fn:a,b:((a)>(b)?(a):(b))")));}
 @Test void includesAndOptionsKeepOrigin()throws Exception{
  var p=pack("#version 120\n#define EXPOSURE 1.0 // [0.5 1.0 2.0]\n#include \"/lib/test.glsl\"\n",Map.of("shaders/lib/test.glsl","#if EXPOSURE > 1\nvec3 bright;\n#else\nvec3 dark;\n#endif\n"));
  var result=new ShaderPreprocessor(p,Map.of("EXPOSURE","2.0"),Map.of()).process("shaders/final.fsh");assertTrue(result.source().contains("bright"));assertFalse(result.source().contains("dark"));assertTrue(result.lines().stream().anyMatch(l->l.file().equals("shaders/lib/test.glsl")&&l.number()==2));assertTrue(result.source().contains("#line 2"));
 }
 @Test void commentedToggleCanBeEnabledAndDisabled()throws Exception{var p=pack("//#define FEATURE // toggle\n#ifdef FEATURE\nON\n#else\nOFF\n#endif\n",Map.of());assertTrue(new ShaderPreprocessor(p,Map.of("FEATURE","true"),Map.of()).process("shaders/final.fsh").source().contains("ON"));assertTrue(new ShaderPreprocessor(p,Map.of(),Map.of()).process("shaders/final.fsh").source().contains("OFF"));}
 @Test void rejectsEscapesMissingIncludesAndCycles()throws Exception{assertThrows(IOException.class,()->ShaderPreprocessor.resolve("shaders/final.fsh","../../outside"));var p=pack("#include \"/missing.glsl\"",Map.of());assertThrows(IOException.class,()->new ShaderPreprocessor(p,Map.of(),Map.of()).process("shaders/final.fsh"));var cycle=pack("#include \"final.fsh\"",Map.of());assertThrows(IOException.class,()->new ShaderPreprocessor(cycle,Map.of(),Map.of()).process("shaders/final.fsh"));}
 @Test void includeGuardStopsRecursiveIncludes()throws Exception{var p=pack("#include \"/lib/a.glsl\"",Map.of("shaders/lib/a.glsl","#ifndef A\n#define A\n#include \"/lib/b.glsl\"\n#endif","shaders/lib/b.glsl","#include \"a.glsl\"\n"));assertDoesNotThrow(()->new ShaderPreprocessor(p,Map.of(),Map.of()).process("shaders/final.fsh"));}
}
