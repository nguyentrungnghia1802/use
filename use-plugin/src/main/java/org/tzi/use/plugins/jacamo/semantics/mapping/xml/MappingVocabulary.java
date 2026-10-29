package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.nio.file.Path;
import java.util.*;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Versioned instruction/attribute allowlist. Parsed syntax alone never grants execution support. */
final class MappingVocabulary {
    private record Shape(Set<String> required,Set<String> allowed) { }
    private static final Map<String,Shape> SHAPES=shapes();
    private MappingVocabulary() { }
    static void validate(MappingSpecification spec) {
        Node root=spec.root();shape(root,"#document");
        if(!root.optional("sourcePolicy").isBlank()&&!Set.of("PINNED","VALIDATED_SOURCE_V1").contains(root.attr("sourcePolicy")))throw root.location().error("SOURCE_POLICY_UNSUPPORTED");
        if(!root.attr("version").equals("2.0"))throw root.location().error("VERSION_UNSUPPORTED");
        try{EngineSemanticsProfile.valueOf(root.attr("engineProfile"));}catch(IllegalArgumentException e){throw root.location().error("ENGINE_PROFILE_UNSUPPORTED");}
        if(root.children("presentation").size()>1)throw root.location().error("PRESENTATION_DUPLICATE");
        for(var presentation:root.children("presentation"))if(!presentation.optional("milestones").isEmpty()&&!Set.of("DOMAIN","DOMAIN_AND_EXECUTION").contains(presentation.attr("milestones")))throw presentation.location().error("PRESENTATION_MILESTONES_UNSUPPORTED");
        for(String section:List.of("sources","ocl","selectors","modelRules","istar","runtime","observers","bindings","requirements","omittedConcepts"))root.child(section);
        var project=spec.requireId(root.attr("jcmRef"));
        if(!project.name().equals("file")||!project.attr("kind").equals("JCM"))throw project.location().error("JCM_SOURCE_REQUIRED");
        if(root.child("sources").children("file").stream().filter(n->n.attr("kind").equals("JCM")).count()!=1)throw root.location().error("EXACTLY_ONE_JCM_REQUIRED");
        if(!root.child("modelRules").attr("targetVersion").equals("v4.0"))throw root.location().error("CSL_VERSION_UNSUPPORTED");
        var models=root.children("models");
        if(models.size()>1)throw root.location().error("AUTHORED_MODELS_DUPLICATE");
        if(!models.isEmpty()) {
            var declaration=models.getFirst();
            if(!declaration.attr("mode").equals("AUTHORED")
                    ||!declaration.attr("cslSha256").matches("[0-9a-f]{64}")
                    ||!declaration.attr("istarSha256").matches("[0-9a-f]{64}"))throw declaration.location().error("AUTHORED_MODEL_MODE_OR_HASH_INVALID");
            if(!root.child("modelRules").children().isEmpty()||!root.child("istar").children().isEmpty())
                throw declaration.location().error("AUTHORED_MODE_CANNOT_GENERATE_MODELS");
        }
        for(Node node:spec.nodes()) {
            if(node.attributes().containsKey("population")&&(!node.attr("population").equals("CARTAGO_BOARD_INVENTORY_V1")
                    ||!(node.name().equals("schemeCuts")||node.name().equals("facts")&&node.attr("authority").equals("moise")&&Set.of("GROUP_BOARD","SCHEME_BOARD").contains(node.attr("kind")))))
                throw node.location().error("POPULATION_POLICY_UNSUPPORTED");
            for(String key:List.of("sourceRef","selectRef","selectorRef","leftSelectorRef","rightSelectorRef"))if(node.attributes().containsKey(key)) {
                var target=spec.requireId(node.attr(key));String expected=key.equals("sourceRef")?"file":"select";
                if(!target.name().equals(expected))throw node.location().error("REFERENCE_KIND_MISMATCH:"+key);
            }
            if(node.attributes().containsKey("sha256")&&!node.attr("sha256").matches("[0-9a-f]{64}"))throw node.location().error("SHA256_REQUIRED");
            for(String key:List.of("optional","mutable","verifyInitialExpression","required"))if(node.attributes().containsKey(key)&&!Set.of("true","false").contains(node.attr(key)))throw node.location().error("BOOLEAN_REQUIRED:"+key);
            if(node.attributes().containsKey("expectedCount")&&!node.attr("expectedCount").matches("[0-9]{1,4}"))throw node.location().error("COUNT_REQUIRED");
            if(node.name().equals("file")) {
                if(!node.optional("layoutPolicy").isBlank()||!node.optional("layoutSha256").isBlank()) {
                    if(!node.attr("kind").equals("JCM")||!node.optional("layoutPolicy").equals("JCM_LINE_LAYOUT/1.0")
                            ||!node.optional("layoutSha256").matches("[0-9a-f]{64}"))throw node.location().error("JCM_LAYOUT_POLICY_UNSUPPORTED");
                }
                String path=node.attr("path");relative(path,node);
                if(!Set.of("JCM","ASL","MOISE","JAVA","RESOURCE").contains(node.attr("kind"))||path.toLowerCase(Locale.ROOT).matches(".*\\.(csl|istar|drawio)$"))throw node.location().error("ORACLE_NOT_PRODUCTION_SOURCE");
                if(node.attr("kind").equals("JCM")&&!path.endsWith(".jcm"))throw node.location().error("JCM_EXTENSION_REQUIRED");
            }
            if(node.name().equals("select")) {
                switch(node.attr("syntax")){case "regex"->node.child("pattern");case "xpath"->node.attr("expression");default->throw node.location().error("SELECTOR_SYNTAX_UNSUPPORTED");}
            }
            if(node.name().equals("createClassifier")&&!Set.of("OrgCtx","Role","Entity").contains(node.attr("kind")))throw node.location().error("CLASSIFIER_KIND_UNSUPPORTED");
        }
    }
    static Path relative(String value,Node at) {
        if(value.isBlank()||value.contains(":")||value.contains("\\")||value.startsWith("/"))throw at.location().error("RELATIVE_PATH_REQUIRED");
        Path path=Path.of(value);
        if(path.isAbsolute()||!path.normalize().equals(path))throw at.location().error("NORMALIZED_PATH_REQUIRED");
        for(Path part:path)if(part.toString().equals(".."))throw at.location().error("PATH_TRAVERSAL");
        return path;
    }
    private static void shape(Node node,String parent) {
        Shape shape=SHAPES.get(parent+"/"+node.name());
        if(shape==null||!shape.allowed.containsAll(node.attributes().keySet())||!node.attributes().keySet().containsAll(shape.required))throw node.location().error("ELEMENT_OR_ATTRIBUTE_UNSUPPORTED:"+parent+"/"+node.name());
        if(!Set.of("pattern","pre","post","condition","description","note").contains(node.name())&&!node.text().isBlank())throw node.location().error("TEXT_UNSUPPORTED");
        node.children().forEach(n->shape(n,node.name()));
    }
    private static Map<String,Shape> shapes() {
        var m=new HashMap<String,Shape>();
        add(m,"#document/mapping","version id engineProfile jcmRef","sourcePolicy");
        for(String section:List.of("sources","selectors","runtime","observers","bindings","requirements","omittedConcepts"))add(m,"mapping/"+section,"","");
        add(m,"mapping/ocl","sha256","");
        add(m,"mapping/presentation","","milestones");
        add(m,"presentation/state","owner","attributes");
        add(m,"mapping/models","mode cslSha256 istarSha256","");
        add(m,"mapping/assertions","","");
        add(m,"assertions/require","selectorRef field equals","");
        add(m,"assertions/requireSet","selectorRef field values","");
        add(m,"assertions/requireSameSet","leftSelectorRef leftField rightSelectorRef rightField","");
        add(m,"sources/file","id path sha256 kind","layoutPolicy layoutSha256");
        add(m,"selectors/select","id sourceRef syntax expectedCount","expression");
        add(m,"select/pattern","","");add(m,"select/capture","name expression","");
        add(m,"mapping/modelRules","targetVersion targetModel","");
        add(m,"modelRules/closedTarget","","orgContexts roles entities attributes declarationContainments invariants associations associationEnds generalizations compatibilities");
        add(m,"modelRules/rule","id selectRef","label phase");
        add(m,"rule/createClassifier","kind name","owner membership drawioCell");
        add(m,"rule/createAttribute","name owner type optional mutable","defaultFrom defaultEncoding drawioCell");
        add(m,"rule/mapObservable","matchOn owner","verifyInitialExpression");
        add(m,"rule/observableCase","sourceName targetName type optional mutable initialExpression","drawioCell runtimeConverter defaultLiteral defaultPolicy");
        add(m,"observableCase/initializerDependency","selectorRef field equals","");
        add(m,"rule/createPopulationInvariant","","caseKey counts");
        add(m,"rule/populationCase","sourceRole expectedMin expectedMax name context oclTemplate","");
        add(m,"rule/require","selectorRef field equals","");add(m,"rule/requireSet","selectorRef field values","");
        add(m,"rule/requireSameSet","leftSelectorRef leftField rightSelectorRef rightField","");
        add(m,"rule/trace","sourceRef locator","purpose");add(m,"rule/note","","");
        add(m,"mapping/istar","name","");
        add(m,"istar/actor","id name kind","selectRef");
        for(String kind:List.of("goal","task","resource"))add(m,"actor/"+kind,"id name","type selectRef");
        for(String kind:List.of("goal","task","resource")) {
            add(m,kind+"/description","","");add(m,kind+"/refine","kind target","");add(m,kind+"/neededBy","task","");
        }
        add(m,"goal/condition","","");add(m,"task/pre","","");add(m,"task/post","","");
        add(m,"istar/dependency","id name kind fromActor from toActor to","");
        add(m,"istar/association","id kind from to","");
        add(m,"bindings/static","id kind target sourceKind feature value reason","selectRef nativeSymbol");
        add(m,"static/metadata","key value","");
        add(m,"bindings/actorRuntime","actor bindingRef activationPolicy episodePolicy","roleBindingRef contextBindingRef");
        add(m,"bindings/elementRuntime","target actor self derived selfKind reason","contract stepScope scopePolicy entryPolicy failurePolicy stepSchemeColumn stepArtifactColumn");
        add(m,"elementRuntime/variable","name origin","type viewRef");
        add(m,"elementRuntime/actionReceipt","operationRef","");
        add(m,"elementRuntime/acceptedFailure","term bodyType line sourceFailure","");
        add(m,"bindings/requirement","target","");
        add(m,"bindings/oclClause","name context","");
        add(m,"bindings/operation","id owner signature clauses","task");
        add(m,"bindings/dependencyPopulation","target quantifier","");
        add(m,"observers/view","id name category keyFields","");
        add(m,"view/field","name type origin","");
        add(m,"observers/project","id viewRef tableRef keyFields","");
        add(m,"project/write","field column mode","historyColumn whenColumn beforeFreeze");
        add(m,"project/reference","field targetProject subjectColumn mode","");
        add(m,"reference/key","field column","");
        add(m,"project/lifecycle","kind column requestRef","");
        add(m,"project/derive","field expression completeColumn","");
        add(m,"derive/arg","name column type","onMissing");
        add(m,"requirements/capability","id provider required","sourceRef class field type hash hashOrigin organization scheme operations");
        add(m,"capability/property","name type","");
        add(m,"capability/plan","trigger","context");
        add(m,"plan/step","task first after variables","artifactVariable artifactClass schemeVariable allowMissingArtifactAtEntry");
        add(m,"step/input","name variable type","");
        add(m,"runtime/inputs","","");
        add(m,"inputs/input","id identityKind","identityFields tableRef reconcileMissing reconcileScopeField");
        add(m,"input/field","name type required","");
        add(m,"runtime/converters","","");
        add(m,"converters/converter","id inputType outputType algorithm onMismatch","onOverflowOrUnsupportedBackend sentinelReplacement");
        add(m,"runtime/rule","id inputRef","");
        add(m,"rule/when","field equals","");
        add(m,"rule/whenIn","field values","");
        add(m,"rule/whenFieldsEqual","left right","");
        add(m,"rule/requireEvidence","proof","");
        add(m,"rule/requireResolvedInstance","targetType identityFrom","");
        add(m,"rule/upsertInstance","targetType keyFields","contextType contextFrom membership playerFrom aliasFrom");
        add(m,"rule/assignSnapshot","targetType keyFields atomic missingValuePolicy","aliasFrom");
        add(m,"upsertInstance/assign","target sourceField converter","write");
        add(m,"assignSnapshot/assign","target sourceField converter","write");
        add(m,"rule/removeInstance","targetType keyFields authorityProof","");
        add(m,"runtime/tables","","");
        add(m,"runtime/occurrences","","");
        add(m,"occurrences/scope","id tableRef anchorColumn contextColumn keyFields","");
        add(m,"scope/actor","actor tableRef scopeColumn scopeKey playerColumn kind cardinality","roleColumn roleLookup completeColumn");
        add(m,"scope/self","target origin","column optional");
        add(m,"scope/resource","target column type","");
        add(m,"runtime/taskDemands","","");
        add(m,"taskDemands/demand","id target scopeRef tableRef anchorColumn playerColumn requiredColumn episodeFields","completeColumn scopeColumn scopeKey");
        add(m,"runtime/datasets","","");
        add(m,"datasets/facts","id authority kind","population");
        add(m,"datasets/removals","id authority kind","");
        add(m,"datasets/fieldCuts","id requestRef","");
        add(m,"datasets/clock","id requestRef","");
        add(m,"datasets/schemeCuts","id requestRef","population");
        add(m,"datasets/npl","id requestRef","");
        add(m,"datasets/operationCuts","id requestRef","");
        add(m,"datasets/operationReferences","id requestRef argumentIndex","");
        add(m,"datasets/jasonSteps","id requestRef","");
        add(m,"datasets/beliefViews","id namespace predicate arity selection","");
        add(m,"runtime/beliefBindings","","");
        add(m,"beliefBindings/bind","id roleType attribute datasetRef","");
        add(m,"datasets/artifactReferences","id requestRef","");
        add(m,"datasets/schemeReferences","id requestRef","");
        add(m,"tables/table","id dataset","");
        add(m,"table/where","path type equals","");
        add(m,"table/whereNot","path type equals","");
        add(m,"table/test","as path type equals negate","");
        add(m,"table/whereSize","path equals","");
        add(m,"table/whereAny","path type","");
        add(m,"whereAny/value","equals","");
        add(m,"table/whereBound","path bindingRef","");
        add(m,"table/whereExists","tableRef leftPath rightPath as","");
        add(m,"table/whereFieldsEqual","leftPath rightPath type","");
        add(m,"table/termString","path rawPath as mode","");
        add(m,"table/expand","path as","valueType");
        add(m,"table/firstBy","path groupPaths historyPath","");
        add(m,"table/firstMatch","path as orderPath completePath","");
        add(m,"firstMatch/match","path type equals","");
        add(m,"table/choose","as conditionPath truePath falsePath","");
        add(m,"table/selectValues","path as valuePath valueType completePath distinct","matchPath matchType equals");
        add(m,"selectValues/match","path type equals","");
        add(m,"table/relativeTime","as requestRef epochPath sourceClockPath","onMissing");
        add(m,"table/sampleClock","as requestRef sourceClockPath","");
        add(m,"table/join","tableRef leftPath rightPath as cardinality required","knowledge onMissing");
        add(m,"table/column","name path required","");
        add(m,"table/identity","mode","path fields as");
        add(m,"omittedConcepts/omit","id concept reason","handledBy sourceStep");
        return Map.copyOf(m);
    }
    private static void add(Map<String,Shape> map,String path,String required,String optional) {
        var r=words(required);var allowed=new HashSet<>(r);allowed.addAll(words(optional));
        if(map.put(path,new Shape(r,Set.copyOf(allowed)))!=null)throw new IllegalStateException("DUPLICATE_MAPPING_VOCABULARY");
    }
    private static Set<String> words(String words){return words.isBlank()?Set.of():Set.of(words.split(" "));}
}
