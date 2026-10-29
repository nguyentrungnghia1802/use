package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;
import static org.junit.jupiter.api.Assertions.*;
class FlexibleSourceImportTest {
 @TempDir Path temp;
 final Path bundle=Path.of("src/test/resources/auction-reference"), external=Path.of("../../jacamo/examples/auction");
 MappingInputs read(Path jcm, boolean strict){return MappingInputs.read(jcm,bundle.resolve("authored-v1/auction-mapping.xml"),bundle.resolve("authored-v1/auction_constraints_final_fixed.ocl"),bundle.resolve("authored-v1/auction_csl.csl"),bundle.resolve("authored-v1/auction_istar_final.istar"),strict);}
 Path copy()throws Exception {
  for(String file:List.of("auction.jcm","src/agt/auction_capabilities.asl","src/org/auction-os.xml","src/env/auction_env/AuctionArtifact.java","build.gradle")) {
   var target=temp.resolve(file);Files.createDirectories(target.getParent());Files.copy(external.resolve(file),target);
  }
  return Files.move(temp.resolve("auction.jcm"),temp.resolve("renamed.jcm"));
 }
 @Test void selectedExternalProjectUsesActualBytesAndStrictRemainsExplicit()throws Exception {
  Path jcm=external.resolve("auction.jcm");byte[] before=Files.readAllBytes(jcm);
  assertThrows(IllegalArgumentException.class,()->read(jcm,true));
  var inputs=read(jcm,false);assertArrayEquals(before,inputs.require("src-jcm").bytes());
  assertEquals("VALIDATED_SOURCE_V1",inputs.specification().root().attr("sourcePolicy"));
  assertFalse(SourceSelections.execute(inputs).isEmpty());assertArrayEquals(before,Files.readAllBytes(jcm));
 }
 @Test void relocatedRenamedAndEditedCompatibleSourcesFreezeAndReplayWithoutDisk()throws Exception {
  Path jcm=copy();Files.writeString(jcm,Files.readString(jcm)+"\n// user revision\n");
  Path asl=temp.resolve("src/agt/auction_capabilities.asl");Files.writeString(asl,Files.readString(asl)+"\n// user revision\n");
  var inputs=read(jcm,false);String digest=inputs.require("src-asl").sha256();
  assertEquals(TraceComplement.Asset.of("actual","ASL",Files.readAllBytes(asl)).sha256(),digest);
  Files.delete(jcm);Files.delete(asl);
  var replay=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"offline");assertEquals(inputs.projectDigest(),replay.projectDigest());
  var tampered=new LinkedHashMap<>(inputs.sources());var original=inputs.require("src-jcm");tampered.put(original.id(),TraceComplement.Asset.of(original.id(),original.format(),"mas other {}".getBytes()));
  assertThrows(IllegalArgumentException.class,()->MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),tampered,"tamper"));
 }
 @Test void incompatibleSelectorsStillFailBeforeRuntimeAndUnknownPolicyRejects()throws Exception {
  Path jcm=copy();Files.writeString(jcm,Files.readString(jcm).replace("auctionGroup","incompatible_group"));
  assertThrows(IllegalArgumentException.class,()->read(jcm,false));
  String xml=Files.readString(bundle.resolve("authored-v1/auction-mapping.xml")).replace("version=\"2.0\"","version=\"2.0\" sourcePolicy=\"IGNORE_ALL\"");
  assertThrows(IllegalArgumentException.class,()->new MappingXmlReader().parse(xml.getBytes(),"invalid"));
 }
}
