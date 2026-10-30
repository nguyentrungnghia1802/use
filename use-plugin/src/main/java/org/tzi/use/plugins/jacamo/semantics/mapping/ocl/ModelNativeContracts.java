package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;
import java.nio.*;import java.nio.charset.*;import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;
/** Explicit versioned tool annotation inside CSL/iStar comments. Original DSL parsers
 * remain unchanged. Bodies use ONLY native USE OCL, never a local evaluator.
 * XML binds the resulting named contracts; all authored bytes stay hash-pinned. */
public final class ModelNativeContracts {
 private ModelNativeContracts(){}
 public static AuthoredOclSpecificationLoader.Specification read(MappingInputs inputs){
  var loader=new AuthoredOclSpecificationLoader();var fragments=new ArrayList<String>();
  if(inputs.authoredModels())for(String id:List.of(MappingInputs.AUTHORED_CSL,MappingInputs.AUTHORED_ISTAR)){
   var asset=inputs.require(id);for(String body:blocks(asset.bytes()))fragments.add("-- Model-native OCL from "+id+" sha256="+asset.sha256()+"\n"+body);
  }
  if(fragments.isEmpty())return loader.parseOptional(inputs.ocl().bytes(),inputs.ocl().id());
  // Independently validate the optional file too, so malformed clauses never disappear.
  var external=loader.parseOptional(inputs.ocl().bytes(),inputs.ocl().id());
  fragments.add("-- Optional external OCL sha256="+inputs.ocl().sha256()+"\n"+external.source());
  return loader.parse(String.join("\n",fragments),"MODEL_NATIVE_OCL/1.0+"+inputs.ocl().id());
 }
 public static List<String> blocks(byte[] bytes){
  String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException invalid){throw new IllegalArgumentException("MODEL_NATIVE_OCL_UTF8",invalid);}
  if(bytes.length>8*1024*1024)throw new IllegalArgumentException("MODEL_NATIVE_OCL_CAPACITY");
  var blocks=new ArrayList<String>();int i=0;
  while(i<text.length()){
   char c=text.charAt(i);
   if(c=='\''||c=='"'){char quote=c;i++;while(i<text.length()){char q=text.charAt(i++);if(q=='\\'&&i<text.length())i++;else if(q==quote){if(i<text.length()&&text.charAt(i)==quote)i++;else break;}}continue;}
   if(text.startsWith("//",i)){int end=text.indexOf('\n',i);i=end<0?text.length():end+1;continue;}
   if(text.startsWith("/*",i)){
    int end=text.indexOf("*/",i+2);if(end<0)throw new IllegalArgumentException("MODEL_NATIVE_OCL_UNTERMINATED_COMMENT");String comment=text.substring(i+2,end).stripLeading();
    if(comment.startsWith("@use-native-ocl")){
     int line=comment.indexOf('\n');if(line<0||!comment.substring(0,line).strip().equals("@use-native-ocl 1"))throw new IllegalArgumentException("MODEL_NATIVE_OCL_VERSION_REQUIRED");
     String body=comment.substring(line+1).strip();if(body.isBlank()||blocks.size()>=16)throw new IllegalArgumentException("MODEL_NATIVE_OCL_EMPTY_OR_CAPACITY");blocks.add(body);
    }
    i=end+2;continue;
   }
   i++;
  }
  return List.copyOf(blocks);
 }
}
