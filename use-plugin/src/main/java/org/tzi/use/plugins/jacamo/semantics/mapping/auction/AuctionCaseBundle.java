package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.TraceComplement;

/** Frozen case inputs. Original paths are provenance only, never fallback IO authority.
 * This owns bytes and their derivation receipts, not a USE or JaCaMo runtime. */
public final class AuctionCaseBundle {
    public static final String MANIFEST = "case-manifest.json";
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    private static final Set<String> MANIFEST_FIELDS = Set.of("schemaVersion", "caseId", "rootAliases", "assets", "runtimeRequirements");
    private static final Set<String> ASSET_FIELDS = Set.of("id", "path", "sha256", "format", "origin", "archiveSha256", "archiveEntry");
    public record Input(String id, String path, String format, String sha256, String origin,
                        String archiveSha256, String archiveEntry) { }
    public record Derivation(String bundleHash, String sourceId, String sourceHash, String recipe,
                             TraceComplement.Asset derived) { }

    private final String caseId, hash;
    private final Map<String, String> roots, requirements;
    private final Map<String, Input> inputs;
    private final Map<String, TraceComplement.Asset> assets;
    private final TraceComplement.Asset manifest;

    private AuctionCaseBundle(String caseId, byte[] manifest, Map<String, String> roots, Map<String, String> requirements,
                              Map<String, Input> inputs, Map<String, TraceComplement.Asset> assets) {
        this.caseId = caseId; this.hash = sha256(manifest);this.manifest=TraceComplement.Asset.of(MANIFEST,"AUCTION_CASE_MANIFEST/1.0",manifest); this.roots = Map.copyOf(roots); this.requirements = Map.copyOf(requirements);
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.assets = Collections.unmodifiableMap(new LinkedHashMap<>(assets));
    }

    public static AuctionCaseBundle read(Path directory) {
        try {
            Path root = directory.toRealPath();
            if (!Files.isDirectory(root)) throw invalid("DIRECTORY_REQUIRED");
            byte[] manifest = readBounded(root, MANIFEST);
            return load(manifest,path->readBounded(root,path));
        } catch (IOException | ArithmeticException | org.jacamo.bridge.contract.ContractException error) {
            throw new IllegalArgumentException("CASE_BUNDLE_UNREADABLE_OR_CAPACITY", error);
        }
    }
    @FunctionalInterface private interface InputReader {byte[] read(String path)throws IOException;}
    private static AuctionCaseBundle load(byte[] manifest,InputReader reader)throws IOException {
            if(manifest==null||manifest.length>MAX_FILE_BYTES)throw invalid("MANIFEST_CAPACITY");
            var body = CanonicalJson.object(CanonicalJson.decode(manifest));
            if (!body.keySet().equals(MANIFEST_FIELDS)) throw invalid("MANIFEST_FIELDS");
            if (!"1.0.0".equals(body.get("schemaVersion"))) throw invalid("VERSION_UNSUPPORTED");
            String id = text(body, "caseId");
            var roots = strings(body.get("rootAliases"));
            if (roots.isEmpty()) throw invalid("ROOT_ALIASES_REQUIRED");
            roots.values().forEach(AuctionCaseBundle::relative);
            var requirements = strings(body.get("runtimeRequirements"));
            if (requirements.isEmpty()) throw invalid("RUNTIME_REQUIREMENTS_REQUIRED");
            if (!(body.get("assets") instanceof List<?> entries) || entries.isEmpty() || entries.size() > 64)
                throw invalid("ASSET_COUNT");
            var inputs = new LinkedHashMap<String, Input>();
            var assets = new LinkedHashMap<String, TraceComplement.Asset>();
            var paths = new HashSet<String>();
            int total = manifest.length;
            for (Object entry : entries) {
                var item = CanonicalJson.object(entry);
                if (!item.keySet().equals(ASSET_FIELDS)) throw invalid("ASSET_FIELDS");
                String assetId = text(item, "id"), path = text(item, "path"), expected = digest(text(item, "sha256"));
                String format = text(item, "format"), origin = text(item, "origin");
                String archiveHash = string(item, "archiveSha256"), archiveEntry = string(item, "archiveEntry");
                if (archiveHash.isEmpty() != archiveEntry.isEmpty()) throw invalid("ARCHIVE_PROVENANCE_INCOMPLETE:" + assetId);
                if (!archiveHash.isEmpty()) { digest(archiveHash); relative(archiveEntry); }
                if (inputs.containsKey(assetId) || !paths.add(relative(path).toString().toLowerCase(Locale.ROOT)))
                    throw invalid("ASSET_ID_OR_PATH_DUPLICATE:" + assetId);
                byte[] bytes = reader.read(path);
                if(bytes==null||bytes.length>MAX_FILE_BYTES)throw invalid("FILE_CAPACITY:"+path);
                total = Math.addExact(total, bytes.length);
                if (total > MAX_TOTAL_BYTES) throw invalid("TOTAL_CAPACITY");
                if (!sha256(bytes).equals(expected)) throw invalid("CHECKSUM_MISMATCH:" + assetId);
                inputs.put(assetId, new Input(assetId, path, format, expected, origin, archiveHash, archiveEntry));
                assets.put(assetId, TraceComplement.Asset.of(assetId, format, bytes));
            }
            return new AuctionCaseBundle(id, manifest, roots, requirements, inputs, assets);
    }

    /** Offline replay owns these bytes. Manifest locations/origins are labels;
     * the live reader's catalog, capacity and checksum checks are reused. */
    public static AuctionCaseBundle fromFrozen(byte[] manifest,Map<String,TraceComplement.Asset> frozen) {
        var body=CanonicalJson.object(CanonicalJson.decode(manifest));var paths=new HashMap<String,TraceComplement.Asset>();var ids=new HashSet<String>();
        if(!(body.get("assets") instanceof List<?> entries))throw invalid("ASSET_COUNT");
        for(var value:entries){var entry=CanonicalJson.object(value);String id=text(entry,"id"),path=text(entry,"path");var asset=frozen.get(id);
            if(asset==null||!id.equals(asset.id())||!text(entry,"format").equals(asset.format())||!ids.add(id)||paths.put(path,asset)!=null)throw invalid("FROZEN_ASSET_CATALOG");}
        if(!ids.equals(frozen.keySet()))throw invalid("FROZEN_ASSET_CATALOG");
        try{return load(manifest,path->{var asset=paths.get(path);if(asset==null)throw invalid("FROZEN_ASSET_REQUIRED:"+path);return asset.bytes();});}
        catch(IOException|ArithmeticException error){throw new IllegalArgumentException("CASE_BUNDLE_FROZEN_UNREADABLE_OR_CAPACITY",error);}
    }
    public TraceComplement.Asset manifest(){return manifest;}

    public String caseId() { return caseId; }
    public String hash() { return hash; }
    public Map<String, String> rootAliases() { return roots; }
    public Map<String, String> runtimeRequirements() { return requirements; }
    public Map<String, Input> inputs() { return inputs; }
    public Map<String, TraceComplement.Asset> assets() { return assets; }
    public TraceComplement.Asset require(String id) {
        var asset = assets.get(id);
        if (asset == null) throw invalid("ASSET_REQUIRED:" + id);
        return asset;
    }

    /** Ensures the XML's logical root/path resolves to the already frozen asset. */
    public void requireLocation(String id, String rootRef, String sourcePath, String sourceHash) {
        var input = inputs.get(id);
        String alias = roots.get(rootRef);
        if (input == null || alias == null) throw invalid("SOURCE_REFERENCE_UNRESOLVED:" + id);
        Path expected = relative(alias).resolve(relative(sourcePath)).normalize();
        if (!relative(input.path()).equals(expected) || !input.sha256().equals(digest(sourceHash)))
            throw invalid("SOURCE_LOCATION_OR_CHECKSUM_MISMATCH:" + id);
    }

    public Derivation derive(String sourceId, String recipe, byte[] derived) {
        if (recipe == null || recipe.isBlank() || recipe.length() > 1024) throw invalid("DERIVATION_RECIPE_REQUIRED");
        if (derived == null || derived.length > MAX_FILE_BYTES) throw invalid("DERIVATION_CAPACITY");
        var source = require(sourceId);
        return new Derivation(hash, sourceId, source.sha256(), recipe,
                TraceComplement.Asset.of(sourceId + ":derived", source.format(), derived));
    }

    /** The caller supplies the actual selected producer binary, never an XML path. */
    public void verifyRuntimeArtifact(String fileName, byte[] actualBytes) {
        String expected = requirements.get("sha256:" + fileName);
        if (actualBytes == null || expected == null || !sha256(actualBytes).equals(expected))
            throw invalid("RUNTIME_ARTIFACT_MISMATCH:" + fileName);
    }

    public void verifyArchive(String assetId, byte[] actualArchive) {
        var input = inputs.get(assetId);
        if (input == null || input.archiveEntry().isEmpty() || actualArchive == null
                || !sha256(actualArchive).equals(input.archiveSha256())) throw invalid("ARCHIVE_MISMATCH:" + assetId);
        int matches = 0;
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(actualArchive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.getName().equals(input.archiveEntry())) continue;
                byte[] bytes = zip.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES || !sha256(bytes).equals(input.sha256())) throw invalid("ARCHIVE_ENTRY_MISMATCH:" + assetId);
                matches++;
            }
        } catch (IOException error) { throw new IllegalArgumentException("CASE_BUNDLE_ARCHIVE_UNREADABLE:" + assetId, error); }
        if (matches != 1) throw invalid("ARCHIVE_ENTRY_NOT_UNIQUE:" + assetId);
    }

    private static byte[] readBounded(Path root, String path) throws IOException {
        Path candidate = root.resolve(relative(path)).normalize();
        if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)) throw invalid("PATH_UNSAFE:" + path);
        Path actual = candidate.toRealPath();
        if (!actual.startsWith(root) || !Files.isRegularFile(actual)) throw invalid("PATH_OUTSIDE_BUNDLE:" + path);
        try (var stream = Files.newInputStream(actual)) {
            byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw invalid("FILE_CAPACITY:" + path);
            return bytes;
        }
    }

    private static Path relative(String value) {
        if (".".equals(value)) return Path.of("");
        if (value == null || value.isBlank() || value.contains("\\") || value.contains(":") || value.startsWith("/"))
            throw invalid("RELATIVE_PATH_REQUIRED");
        Path path = Path.of(value);
        if (path.isAbsolute() || !path.normalize().equals(path)) throw invalid("PATH_NOT_NORMALIZED:" + value);
        for (Path part : path) if (part.toString().equals("..")) throw invalid("PATH_TRAVERSAL");
        return path;
    }

    private static Map<String, String> strings(Object value) {
        var result = new LinkedHashMap<String, String>();
        CanonicalJson.object(value).forEach((k, v) -> {
            if (k.isBlank() || !(v instanceof String text) || text.isBlank()) throw invalid("STRING_MAP_REQUIRED");
            result.put(k, text);
        });
        return result;
    }
    private static String text(Map<String, Object> map, String key) {
        String value = string(map, key);
        if (value.isBlank()) throw invalid("FIELD_REQUIRED:" + key);
        return value;
    }
    private static String string(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof String value)) throw invalid("STRING_REQUIRED:" + key);
        return value;
    }
    private static String digest(String value) {
        if (!value.matches("[0-9a-f]{64}")) throw invalid("SHA256_REQUIRED");
        return value;
    }
    private static String sha256(byte[] bytes) { return ExternalOclConstraintService.sha256(bytes); }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("CASE_BUNDLE_" + reason); }
}
