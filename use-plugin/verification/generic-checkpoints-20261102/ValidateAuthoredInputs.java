import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
class ValidateAuthoredInputs {
 public static void main(String[] args)throws Exception{
  var result=new TreeMap<String,Object>();result.put("inputs",List.of(args).subList(0,5));
  try{
   var jcm=Path.of(args[0]).toRealPath();
   var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
   var inputs=MappingInputs.read(jcm,Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Path.of(args[4]),false);
   var program=new GenericMappingEngine().map(source,inputs);
   result.put("status","STATIC_MAPPING_VALIDATED");result.put("programHash",program.hash());
  }catch(Throwable failure){result.put("status","REJECTED");result.put("diagnostic",failure.toString());}
  new com.fasterxml.jackson.databind.ObjectMapper().writeValue(Path.of(args[5]).toFile(),result);System.out.println(result);
 }
}
