from pathlib import Path
p=Path('use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/compact/SourceSymbolCatalog.java')
s=p.read_text(encoding='utf-8')
a=s.index('    private void inspectClassBytecode(');b=s.index('    public void register(SourceSymbol symbol)',a)
s=s[:a]+s[b:]
s=s.replace('            if (indexedArtifactClasses.add(fqcn + "@" + ev.sourceDigest())) inspectClassBytecode(fqcn, ev.sourceUri(), ev.sourceDigest());\n','')
a=s.index('        for (String diagnostic : ev.diagnostics()) if (diagnostic.startsWith("SIGNATURE:"))')
s=s[:a]+'''        if(ev.diagnostics().contains("SOURCE_ACTION_CARTAGO_ARTIFACT_FIELD") || ev.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_FIELD")) {
            var signatures=ev.diagnostics().stream().filter(t->t.startsWith("FIELD_SIGNATURE:")).toList();
            if(signatures.size()!=1||!ev.diagnostics().contains("OWNER:"+fqcn))throw new IllegalArgumentException("SOURCE_FIELD_INTERFACE_PROOF_REQUIRED:"+fqcn);
            Object value=org.jacamo.bridge.contract.CanonicalJson.decode(signatures.getFirst().substring("FIELD_SIGNATURE:".length()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!(value instanceof List<?> parts)||parts.size()!=3||!fqcn.equals(parts.get(0))||!(parts.get(1) instanceof String name)||!(parts.get(2) instanceof String type))
                throw new IllegalArgumentException("SOURCE_FIELD_INTERFACE_INVALID:"+fqcn);
            register(new SourceSymbol(SourceReference.Kind.ARTIFACT_FIELD,fqcn+"/"+name,fqcn,name,type,ev.sourceDigest(),ev.sourceUri()));
        }
'''+s[a:]
# Remove obsolete consumer IO imports.
for line in ['import java.io.ByteArrayInputStream;\n','import java.io.DataInputStream;\n','import java.io.InputStream;\n','import java.net.URI;\n','import java.net.URL;\n']:
 s=s.replace(line,'')
p.write_text(s,encoding='utf-8')
