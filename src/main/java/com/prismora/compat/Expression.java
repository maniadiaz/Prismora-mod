package com.prismora.compat;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Bounded C-preprocessor numeric expression evaluator, including short-circuit and bit operators. */
public final class Expression {
 private final List<String> tokens=new ArrayList<>();private final Map<String,String> macros;private final int depth;private int index;
 private static final Pattern TOKEN=Pattern.compile("\\s*(defined\\b|0[xX][0-9a-fA-F]+|(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[uUlLfF]?|[A-Za-z_]\\w*|&&|\\|\\||==|!=|<=|>=|<<|>>|[()?:!~+*/%<>&|^,\\-])");
 private static final List<List<String>> LEVELS=List.of(List.of("||"),List.of("&&"),List.of("|"),List.of("^"),List.of("&"),List.of("==","!="),List.of("<","<=",">",">="),List.of("<<",">>"),List.of("+","-"),List.of("*","/","%"));
 private Expression(String text,Map<String,String> macros,int depth)throws IOException{
  if(depth>32||text.length()>65536)throw new IOException("Macro expression recursion/size limit");this.macros=macros;this.depth=depth;
  int at=0,nesting=0;while(at<text.length()){if(text.substring(at).isBlank())break;var match=TOKEN.matcher(text);match.region(at,text.length());if(!match.lookingAt())throw new IOException("Unsupported preprocessor expression near "+text.substring(at,Math.min(text.length(),at+40)));String token=match.group(1);if(token.equals("(")&&++nesting>64)throw new IOException("Expression nesting limit");if(token.equals(")")&&--nesting<0)throw new IOException("Unmatched expression parenthesis");tokens.add(token);at=match.end();if(tokens.size()>8192)throw new IOException("Expression token limit");}if(nesting!=0)throw new IOException("Unclosed expression parenthesis");
 }
 public static boolean test(String text,Map<String,String> macros)throws IOException{return evaluate(text,macros,0)!=0;}
 private static double evaluate(String text,Map<String,String> macros,int depth)throws IOException{var p=new Expression(text,macros,depth);double value=p.conditional(true);if(p.index!=p.tokens.size())throw new IOException("Unexpected expression token "+p.peek());return value;}
 private String peek(){return index<tokens.size()?tokens.get(index):"";}
 private boolean take(String text){if(peek().equals(text)){index++;return true;}return false;}
 private void expect(String text)throws IOException{if(!take(text))throw new IOException("Expected '"+text+"' in expression");}
 private double conditional(boolean evaluate)throws IOException{double condition=binary(0,evaluate);if(take("?")){double yes=conditional(evaluate&&condition!=0);expect(":");double no=conditional(evaluate&&condition==0);return evaluate?(condition!=0?yes:no):0;}return condition;}
 private double binary(int level,boolean evaluate)throws IOException{
  if(level==LEVELS.size())return unary(evaluate);double left=binary(level+1,evaluate);
  while(LEVELS.get(level).contains(peek())){String op=tokens.get(index++);boolean rhs=evaluate&&!(op.equals("&&")&&left==0)&&!(op.equals("||")&&left!=0);double right=binary(level+1,rhs);if(evaluate)left=apply(op,left,right);}
  return evaluate?left:0;
 }
 private double unary(boolean evaluate)throws IOException{
  if(take("!"))return unary(evaluate)==0?1:0;if(take("~"))return ~(long)unary(evaluate);if(take("-"))return -unary(evaluate);if(take("+"))return unary(evaluate);
  if(take("(")){double value=conditional(evaluate);expect(")");return value;}
  String token=peek();if(token.isEmpty())throw new IOException("Incomplete expression");index++;
  if(token.equals("defined")){boolean paren=take("(");String name=peek();index++;if(paren)expect(")");return evaluate&&macros.containsKey(name)?1:0;}
  if(token.matches("[A-Za-z_]\\w*")){
   String value=macros.get(token);
   if(take("(")){
    var args=new ArrayList<String>();int start=index,nesting=0;
    while(!peek().isEmpty()){
     if(peek().equals(")")&&nesting==0){if(index>start)args.add(String.join(" ",tokens.subList(start,index)));index++;break;}
     if(peek().equals(",")&&nesting==0){args.add(String.join(" ",tokens.subList(start,index)));start=++index;continue;}
     if(peek().equals("("))nesting++;if(peek().equals(")"))nesting--;index++;
    }
    if(!evaluate)return 0;
    if(value==null||!value.startsWith("@fn:"))throw new IOException("Unknown preprocessor function "+token);
    int separator=value.indexOf(':',4);String signature=value.substring(4,separator),expanded=value.substring(separator+1);var params=signature.isBlank()?List.<String>of():List.of(signature.split(","));
    if(params.size()!=args.size())throw new IOException("Macro argument count for "+token);
    var replacements=new HashMap<String,String>();for(int i=0;i<params.size();i++)replacements.put(params.get(i).trim(),"("+args.get(i)+")");var identifiers=Pattern.compile("[A-Za-z_]\\w*").matcher(expanded);var substituted=new StringBuffer();while(identifiers.find())identifiers.appendReplacement(substituted,Matcher.quoteReplacement(replacements.getOrDefault(identifiers.group(),identifiers.group())));identifiers.appendTail(substituted);expanded=substituted.toString();
    return evaluate(expanded,macros,depth+1);
   }
   if(!evaluate)return 0;if(token.equals("true"))return 1;if(token.equals("false"))return 0;return value==null||value.startsWith("@fn:")?0:value.isBlank()?1:evaluate(value,macros,depth+1);
  }
  if(!evaluate)return 0;
  try{String clean=token.replaceFirst(token.startsWith("0x")||token.startsWith("0X")?"[uUlL]$":"[uUlLfF]$","");return clean.startsWith("0x")||clean.startsWith("0X")?Long.parseUnsignedLong(clean.substring(2),16):Double.parseDouble(clean);}catch(NumberFormatException e){throw new IOException("Invalid expression number "+token,e);}
 }
 private static double apply(String op,double a,double b)throws IOException{return switch(op){case "||"->a!=0||b!=0?1:0;case "&&"->a!=0&&b!=0?1:0;case "|"->(long)a|(long)b;case "^"->(long)a^(long)b;case "&"->(long)a&(long)b;case "=="->a==b?1:0;case "!="->a!=b?1:0;case "<"->a<b?1:0;case "<="->a<=b?1:0;case ">"->a>b?1:0;case ">="->a>=b?1:0;case "<<"->(long)a<<(long)b;case ">>"->(long)a>>(long)b;case "+"->a+b;case "-"->a-b;case "*"->a*b;case "/"->{if(b==0)throw new IOException("Division by zero in preprocessor");yield a/b;}case "%"->{if(b==0)throw new IOException("Modulo by zero in preprocessor");yield a%b;}default->throw new IOException("Unknown expression operator "+op);};}
}
