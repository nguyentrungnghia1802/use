package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Secure SAX reader for compact mapping v3 specifications.
 * Enforces namespace urn:use:jacamo-mapping:3 and version 3.0.
 * Strictly disallows DTDs, external entities, XML bombs, and procedural legacy constructs.
 */
public final class CompactMappingReader {

    private static final int MAX_INPUT_BYTES = 2 * 1024 * 1024; // 2MB limit
    private static final int MAX_ELEMENT_COUNT = 5000;
    private static final int MAX_DEPTH = 16;

    private static final Set<String> FORBIDDEN_LEGACY_ELEMENTS = Set.of(
            "dataset", "datasets", "table", "tables", "join", "where", "filter", "project",
            "derive", "pipeline", "rule", "rules", "action", "script", "selectvalues",
            "populationclosure", "select"
    );

    public static CompactMappingSpecification read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Compact mapping file not found: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("COMPACT_MAPPING:FILE_TOO_LARGE: size " + size + " exceeds maximum allowed " + MAX_INPUT_BYTES);
        }
        byte[] bytes = Files.readAllBytes(path);
        return read(bytes, path.getFileName().toString());
    }

    public static CompactMappingSpecification read(String xmlContent, String sourceName) {
        Objects.requireNonNull(xmlContent, "xmlContent");
        byte[] bytes = xmlContent.getBytes(StandardCharsets.UTF_8);
        return read(bytes, sourceName);
    }

    public static CompactMappingSpecification read(byte[] bytes, String sourceName) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("COMPACT_MAPPING:INPUT_TOO_LARGE: " + bytes.length + " > " + MAX_INPUT_BYTES);
        }
        String sha256 = computeSha256(bytes);
        String name = (sourceName == null || sourceName.isBlank()) ? "compact-mapping.xml" : sourceName;

        try {
            SAXParserFactory spf = SAXParserFactory.newDefaultInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(false);
            spf.setXIncludeAware(false);

            // Defensive parser configuration against XXE and expansion attacks
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            SAXParser parser = spf.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            CompactHandler handler = new CompactHandler(name, sha256);
            parser.parse(new ByteArrayInputStream(bytes), handler);
            return handler.build();
        } catch (SAXParseException e) {
            throw new IllegalArgumentException("COMPACT_MAPPING:" + name + ":" + e.getLineNumber() + ":" + e.getColumnNumber() + ": " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("COMPACT_MAPPING:" + name + ": PARSE_ERROR: " + e.getMessage(), e);
        }
    }

    private static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static final class CompactHandler extends DefaultHandler {
        private final String sourceName;
        private final String sha256;

        private Locator locator;
        private int elementCount = 0;
        private int currentDepth = 0;

        private String mappingId;
        private final List<CompactMappingSpecification.Correspondence> cslMappings = new ArrayList<>();
        private final List<CompactMappingSpecification.Correspondence> istarMappings = new ArrayList<>();
        private final List<CompactMappingSpecification.ActorRuntime> actorRuntimes = new ArrayList<>();
        private final List<CompactMappingSpecification.Requirement> requirements = new ArrayList<>();
        private final List<CompactMappingSpecification.DependencyPopulation> dependencyPopulations = new ArrayList<>();
        private final List<CompactMappingSpecification.GoalRealization> goalRealizations = new ArrayList<>();
        private final List<CompactMappingSpecification.VerificationRequirement> verifications=new ArrayList<>();
        private final List<CompactMappingSpecification.ObserverView> observerViews = new ArrayList<>();
        private CompactMappingSpecification.Milestones milestones;
        private CompactMappingSpecification.Location rootLocation;

        private final Set<String> seenCslPairs = new HashSet<>();
        private final Set<String> seenIstarPairs = new HashSet<>();
        private final Set<String> sections = new HashSet<>(), actors = new HashSet<>(), requirementIds = new HashSet<>();
        private static final Set<String> REQUIREMENT_PARAMETERS=Set.of("sourceRef","class","field","type","organization","scheme","operations","policy","anchor","source","target");

        private String currentSection = null; // "csl", "istar", "runtime", "observers"
        private BuildingRequirement currentRequirement = null;
        private BuildingObserverView currentObserverView = null;

        private static final Set<String> ALLOWED_ROOT_ATTRS = Set.of("id", "version");
        private static final Set<String> ALLOWED_MAP_ATTRS = Set.of(
                "source", "target", "activateInvariant", "activationRole", "invariantActivationPolicy", "quantifier", "scope", "role", "context", "policy",
                "scheme", "variables", "artifactVariable", "artifactClass", "schemeVariable",
                "allowMissingArtifactAtEntry", "inputs", "actionReceipts", "enactsRoles", "contextAbstraction",
                "converter", "traceOnly", "self"
        );
        private static final Set<String> ALLOWED_ACTOR_RUNTIME_ATTRS = Set.of(
                "actor", "activation-policy", "activationPolicy", "episode-policy", "episodePolicy",
                "role", "context", "enactsRoles", "physicalActorView", "self"
        );

        CompactHandler(String sourceName, String sha256) {
            this.sourceName = sourceName;
            this.sha256 = sha256;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private CompactMappingSpecification.Location currentLocation() {
            int line = locator != null ? locator.getLineNumber() : -1;
            int col = locator != null ? locator.getColumnNumber() : -1;
            return new CompactMappingSpecification.Location(sourceName, line, col);
        }

        private void requireAllowedAttributes(Attributes attributes, Set<String> allowed, String elementName, CompactMappingSpecification.Location loc) throws SAXException {
            for (int i = 0; i < attributes.getLength(); i++) {
                String attrUri = attributes.getURI(i);
                if (attrUri != null && !attrUri.isEmpty() && !CompactMappingSpecification.NAMESPACE.equals(attrUri)) {
                    throw new SAXException(loc.error("FOREIGN_ATTRIBUTE_NAMESPACE: '" + attrUri + "' on <" + elementName + ">"));
                }
                String aName = attributes.getLocalName(i);
                if (aName == null || aName.isEmpty()) aName = attributes.getQName(i);
                if (aName.startsWith("xmlns")) continue;
                if (!allowed.contains(aName)) {
                    throw new SAXException(loc.error("UNKNOWN_ATTRIBUTE: '" + aName + "' on <" + elementName + ">"));
                }
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            elementCount++;
            currentDepth++;
            if (elementCount > MAX_ELEMENT_COUNT) {
                throw new SAXException(currentLocation().error("ELEMENT_COUNT_EXCEEDED: max allowed " + MAX_ELEMENT_COUNT));
            }
            if (currentDepth > MAX_DEPTH) {
                throw new SAXException(currentLocation().error("DEPTH_EXCEEDED: max allowed " + MAX_DEPTH));
            }

            String elemName = localName != null && !localName.isEmpty() ? localName : qName;
            String lowerName = elemName.toLowerCase(Locale.ROOT);

            if (FORBIDDEN_LEGACY_ELEMENTS.contains(lowerName)) {
                throw new SAXException(currentLocation().error("FORBIDDEN_PROCEDURAL_ELEMENT: '<" + elemName + ">' is not allowed in compact mapping v3"));
            }

            if (!CompactMappingSpecification.NAMESPACE.equals(uri)) {
                throw new SAXException(currentLocation().error("INVALID_NAMESPACE: expected '" + CompactMappingSpecification.NAMESPACE + "', got '" + uri + "' on <" + elemName + ">"));
            }

            if (currentDepth == 1) {
                // Root element
                if (!"mapping".equals(elemName)) {
                    throw new SAXException(currentLocation().error("INVALID_ROOT_ELEMENT: expected '<mapping>', got '<" + elemName + ">'"));
                }
                requireAllowedAttributes(attributes, ALLOWED_ROOT_ATTRS, elemName, currentLocation());
                String version = attributes.getValue("version");
                if (version == null || !CompactMappingSpecification.VERSION.equals(version.trim())) {
                    throw new SAXException(currentLocation().error("UNSUPPORTED_VERSION: expected '" + CompactMappingSpecification.VERSION + "', got '" + version + "'"));
                }
                mappingId = attributes.getValue("id");
                if (mappingId == null || mappingId.isBlank()) {
                    mappingId = sourceName.replaceFirst("\\.[^.]+$", "");
                }
                rootLocation = currentLocation();
                return;
            }

            if (currentDepth == 2) {
                requireAllowedAttributes(attributes, Set.of(), elemName, currentLocation());
                if(!sections.add(elemName))throw new SAXException(currentLocation().error("DUPLICATE_SECTION:"+elemName));
                switch (elemName) {
                    case "csl" -> currentSection = "csl";
                    case "istar" -> currentSection = "istar";
                    case "runtime" -> currentSection = "runtime";
                    case "observers" -> currentSection = "observers";
                    default -> throw new SAXException(currentLocation().error("UNKNOWN_ROOT_CHILD: '<" + elemName + ">'. Only <csl>, <istar>, <runtime>, <observers> allowed."));
                }
                return;
            }

            if (currentDepth == 3) {
                CompactMappingSpecification.Location loc = currentLocation();
                if ("csl".equals(currentSection)) {
                    if (!"map".equals(elemName)) {
                        throw new SAXException(loc.error("CSL_ONLY_ACCEPTS_MAP: '<" + elemName + ">'"));
                    }
                    parseCorrespondence(attributes, loc, cslMappings, seenCslPairs, "csl");
                } else if ("istar".equals(currentSection)) {
                    if (!"map".equals(elemName)) {
                        throw new SAXException(loc.error("ISTAR_ONLY_ACCEPTS_MAP: '<" + elemName + ">'"));
                    }
                    parseCorrespondence(attributes, loc, istarMappings, seenIstarPairs, "istar");
                } else if ("runtime".equals(currentSection)) {
                    parseRuntimeChild(elemName, attributes, loc);
                } else if ("observers".equals(currentSection)) {
                    if (!"view".equals(elemName)) {
                        throw new SAXException(loc.error("OBSERVERS_ONLY_ACCEPTS_VIEW: '<" + elemName + ">'"));
                    }
                    requireAllowedAttributes(attributes, Set.of("id", "name", "category", "keyFields", "source", "population"), elemName, loc);
                    currentObserverView = new BuildingObserverView(
                            attributes.getValue("id"),
                            attributes.getValue("name"),
                            attributes.getValue("category"),
                            attributes.getValue("keyFields"),attributes.getValue("source"),attributes.getValue("population"),
                            loc);
                } else {
                    throw new SAXException(loc.error("UNEXPECTED_ELEMENT_IN_SECTION: " + currentSection + " / " + elemName));
                }
                return;
            }

            if (currentDepth == 4 && "runtime".equals(currentSection) && currentRequirement != null) {
                if ("param".equals(elemName)) {
                    requireAllowedAttributes(attributes, Set.of("name", "value"), elemName, currentLocation());
                    String pName = attributes.getValue("name");
                    String pVal = attributes.getValue("value");
                    if (pName == null || pName.isBlank()) {
                        throw new SAXException(currentLocation().error("PARAM_NAME_REQUIRED"));
                    }
                    if(!REQUIREMENT_PARAMETERS.contains(pName.trim()))throw new SAXException(currentLocation().error("UNSUPPORTED_REQUIREMENT_PARAMETER:"+pName));
                    if(currentRequirement.parameters.putIfAbsent(pName.trim(), pVal != null ? pVal.trim() : "")!=null)
                        throw new SAXException(currentLocation().error("DUPLICATE_REQUIREMENT_PARAMETER:"+pName));
                    return;
                }
                throw new SAXException(currentLocation().error("UNKNOWN_REQUIREMENT_CHILD: '<" + elemName + ">'"));
            }

            if (currentDepth == 4 && "observers".equals(currentSection) && currentObserverView != null) {
                if (!"field".equals(elemName)) {
                    throw new SAXException(currentLocation().error("VIEW_ONLY_ACCEPTS_FIELD: '<" + elemName + ">'"));
                }
                requireAllowedAttributes(attributes, Set.of("name", "type", "origin"), elemName, currentLocation());
                currentObserverView.fields.add(new CompactMappingSpecification.ObserverView.ObserverField(
                        attributes.getValue("name"),
                        attributes.getValue("type"),
                        attributes.getValue("origin"),
                        currentLocation()));
                return;
            }

            throw new SAXException(currentLocation().error("UNEXPECTED_NESTED_ELEMENT: '<" + elemName + ">' at depth " + currentDepth));
        }

        @Override public void characters(char[] text,int start,int length) throws SAXException {
            for(int i=start;i<start+length;i++)if(!Character.isWhitespace(text[i]))throw new SAXException(currentLocation().error("TEXT_CONTENT_NOT_ALLOWED"));
        }
        @Override public InputSource resolveEntity(String publicId,String systemId) throws SAXException {
            throw new SAXException(currentLocation().error("EXTERNAL_ENTITY_NOT_ALLOWED"));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (currentDepth == 2) {
                currentSection = null;
            } else if (currentDepth == 3 && currentRequirement != null) {
                requirements.add(currentRequirement.build());
                currentRequirement = null;
            } else if (currentDepth == 3 && currentObserverView != null) {
                observerViews.add(currentObserverView.build());
                currentObserverView = null;
            }
            currentDepth--;
        }

        private void parseCorrespondence(Attributes attributes, CompactMappingSpecification.Location loc,
                                         List<CompactMappingSpecification.Correspondence> list,
                                         Set<String> seen, String section) throws SAXException {
            requireAllowedAttributes(attributes, ALLOWED_MAP_ATTRS, "map", loc);
            String source = attributes.getValue("source");
            String target = attributes.getValue("target");

            if (source == null || source.isBlank()) {
                throw new SAXException(loc.error("ATTRIBUTE_MISSING: 'source' is required"));
            }
            if (target == null || target.isBlank()) {
                throw new SAXException(loc.error("ATTRIBUTE_MISSING: 'target' is required"));
            }

            source = source.trim();
            target = target.trim();

            try {
                SourceReference.parse(source);
            } catch (Exception e) {
                throw new SAXException(loc.error("INVALID_SOURCE_REFERENCE '" + source + "': " + e.getMessage()));
            }

            try {
                TargetReference.parse(target);
            } catch (Exception e) {
                throw new SAXException(loc.error("INVALID_TARGET_REFERENCE '" + target + "': " + e.getMessage()));
            }

            String pairKey = source + "->" + target;
            if (!seen.add(pairKey)) {
                throw new SAXException(loc.error("DUPLICATE_MAPPING: pair [" + pairKey + "] already declared in " + section));
            }

            Map<String, String> extraAttrs = new LinkedHashMap<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                String aName = attributes.getLocalName(i);
                if (aName == null || aName.isEmpty()) aName = attributes.getQName(i);
                if (!"source".equals(aName) && !"target".equals(aName) && !aName.startsWith("xmlns")) {
                    extraAttrs.put(aName, attributes.getValue(i));
                }
            }

            list.add(new CompactMappingSpecification.Correspondence(source, target, extraAttrs, loc));
        }

        private void parseRuntimeChild(String elemName, Attributes attributes, CompactMappingSpecification.Location loc) throws SAXException {
            switch (elemName) {
                case "actor-runtime", "actorRuntime" -> {
                    requireAllowedAttributes(attributes, ALLOWED_ACTOR_RUNTIME_ATTRS, elemName, loc);
                    String actor = attributes.getValue("actor");
                    if (actor == null || actor.isBlank()) {
                        throw new SAXException(loc.error("ACTOR_RUNTIME_MISSING_ACTOR"));
                    }
                    String actPol = attributes.getValue("activation-policy");
                    if (actPol == null) actPol = attributes.getValue("activationPolicy");
                    String epPol = attributes.getValue("episode-policy");
                    if (epPol == null) epPol = attributes.getValue("episodePolicy");
                    Map<String, String> extraAttrs = new LinkedHashMap<>();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        String aName = attributes.getLocalName(i);
                        if (aName == null || aName.isEmpty()) aName = attributes.getQName(i);
                        if (!Set.of("actor", "activation-policy", "activationPolicy", "episode-policy", "episodePolicy").contains(aName) && !aName.startsWith("xmlns")) {
                            extraAttrs.put(aName, attributes.getValue(i));
                        }
                    }
                    if(!actors.add(actor.trim()))throw new SAXException(loc.error("DUPLICATE_ACTOR_RUNTIME:"+actor));
                    actorRuntimes.add(new CompactMappingSpecification.ActorRuntime(actor.trim(), actPol, epPol, extraAttrs, loc));
                }
                case "dependency-population", "dependencyPopulation" -> {
                    requireAllowedAttributes(attributes, Set.of("target", "quantifier"), elemName, loc);
                    String target = attributes.getValue("target");
                    String quantifier = attributes.getValue("quantifier");
                    if (target == null || target.isBlank()) throw new SAXException(loc.error("DEPENDENCY_POPULATION_MISSING_TARGET"));
                    if (quantifier == null || quantifier.isBlank()) throw new SAXException(loc.error("DEPENDENCY_POPULATION_MISSING_QUANTIFIER"));
                    dependencyPopulations.add(new CompactMappingSpecification.DependencyPopulation(target.trim(), quantifier.trim(), loc));
                }
                case "goal-realization", "goalRealization" -> {
                    requireAllowedAttributes(attributes, Set.of("target", "source", "sourceGoal", "sourceRef", "scheme", "policy", "reason"), elemName, loc);
                    String target = attributes.getValue("target");
                    String source = attributes.getValue("source");
                    if (target == null || target.isBlank() || source == null || source.isBlank()) {
                        throw new SAXException(loc.error("GOAL_REALIZATION_MISSING_TARGET_OR_SOURCE"));
                    }
                    goalRealizations.add(new CompactMappingSpecification.GoalRealization(
                            target.trim(),
                            source.trim(),
                            attributes.getValue("sourceGoal"),
                            attributes.getValue("sourceRef"),
                            attributes.getValue("scheme"),
                            attributes.getValue("policy"),
                            attributes.getValue("reason"),
                            loc
                    ));
                }
                case "milestones" -> {
                    requireAllowedAttributes(attributes, Set.of("kind"), elemName, loc);
                    if(milestones!=null)throw new SAXException(loc.error("DUPLICATE_MILESTONE_POLICY"));
                    String kind = attributes.getValue("kind");
                    milestones = new CompactMappingSpecification.Milestones(kind, loc);
                }
                case "requirement" -> {
                    var allowed=new HashSet<>(REQUIREMENT_PARAMETERS);allowed.addAll(Set.of("id","provider","required"));
                    requireAllowedAttributes(attributes, allowed, elemName, loc);
                    String id = attributes.getValue("id");
                    String provider = attributes.getValue("provider");
                    if (id == null || id.isBlank()) {
                        throw new SAXException(loc.error("REQUIREMENT_MISSING_ID"));
                    }
                    if(!requirementIds.add(id.trim()))throw new SAXException(loc.error("DUPLICATE_REQUIREMENT_ID:"+id));
                    if (provider == null || provider.isBlank()) {
                        throw new SAXException(loc.error("REQUIREMENT_MISSING_PROVIDER"));
                    }
                    String reqStr = attributes.getValue("required");
                    if (reqStr != null && !Set.of("true", "false").contains(reqStr.trim().toLowerCase(Locale.ROOT))) {
                        throw new SAXException(loc.error("REQUIREMENT_INVALID_BOOLEAN: '" + reqStr + "'"));
                    }
                    boolean required = reqStr == null || "true".equalsIgnoreCase(reqStr.trim());
                    Map<String, Object> params = new LinkedHashMap<>();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        String aName = attributes.getLocalName(i);
                        if (aName == null || aName.isEmpty()) aName = attributes.getQName(i);
                        if (!"id".equals(aName) && !"provider".equals(aName) && !"required".equals(aName) && !aName.startsWith("xmlns")) {
                            params.put(aName, attributes.getValue(i));
                        }
                    }
                    currentRequirement = new BuildingRequirement(id.trim(), provider.trim(), required, params, loc);
                }
                case "verify" -> {
                    requireAllowedAttributes(attributes,Set.of("target","scope"),elemName,loc);
                    String target=attributes.getValue("target");
                    if(target==null||target.isBlank())throw new SAXException(loc.error("VERIFICATION_TARGET_REQUIRED"));
                    String scope=Objects.requireNonNullElse(attributes.getValue("scope"),"");
                    if(verifications.stream().anyMatch(v->v.target().equals(target)&&v.scope().equals(scope)))throw new SAXException(loc.error("DUPLICATE_VERIFICATION_TARGET"));
                    verifications.add(new CompactMappingSpecification.VerificationRequirement(target,scope,loc));
                }
                default -> throw new SAXException(loc.error("UNKNOWN_RUNTIME_ELEMENT: '<" + elemName + ">'"));
            }
        }

        CompactMappingSpecification build() {
            if (rootLocation == null) {
                throw new IllegalArgumentException("EMPTY_DOCUMENT: no root mapping element parsed in " + sourceName);
            }
            if (milestones == null) {
                milestones = new CompactMappingSpecification.Milestones("DOMAIN", rootLocation);
            }
            return new CompactMappingSpecification(
                    mappingId,
                    sha256,
                    cslMappings,
                    istarMappings,
                    actorRuntimes,
                    requirements,
                    dependencyPopulations,
                    goalRealizations,
                    verifications,
                    observerViews,
                    milestones,
                    rootLocation
            );
        }
    }

    private static class BuildingObserverView {
        final String id, name, category, keyFields, source, population;
        final CompactMappingSpecification.Location location;
        final List<CompactMappingSpecification.ObserverView.ObserverField> fields = new ArrayList<>();

        BuildingObserverView(String id, String name, String category, String keyFields, String source, String population, CompactMappingSpecification.Location location) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.keyFields = keyFields;
            this.source = source;
            this.population = population;
            this.location = location;
        }

        CompactMappingSpecification.ObserverView build() {
            return new CompactMappingSpecification.ObserverView(id, name, category, keyFields, source, population, fields, location);
        }
    }

    private static class BuildingRequirement {
        final String id;
        final String provider;
        final boolean required;
        final Map<String, Object> parameters;
        final CompactMappingSpecification.Location location;

        BuildingRequirement(String id, String provider, boolean required, Map<String, Object> parameters, CompactMappingSpecification.Location location) {
            this.id = id;
            this.provider = provider;
            this.required = required;
            this.parameters = parameters;
            this.location = location;
        }

        CompactMappingSpecification.Requirement build() {
            return new CompactMappingSpecification.Requirement(id, provider, required, parameters, location);
        }
    }

    private CompactMappingReader() {}
}

