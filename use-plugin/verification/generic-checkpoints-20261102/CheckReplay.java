import java.nio.file.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;
class CheckReplay {
 public static void main(String[] args)throws Exception{
  var report=new NativeRuntimeReplay().replay(Path.of(args[0]));
  new com.fasterxml.jackson.databind.ObjectMapper().writeValue(Path.of(args[1]).toFile(),report);
  System.out.println(report);if(!report.complete())System.exit(1);
 }
}
