package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.math.BigDecimal;
import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClock;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;

/** Bounded relational instructions on already qualified source rows. No source authority is inferred here. */
public final class RuntimeTables {
    private static final int MAX_TABLES=64, MAX_ROWS=4096, MAX_WORK=1_000_000;
    public record Row(String identity,Map<String,Object> values,Completeness quality,
                      Map<String,String> proofs,Set<String> aliases) {
        public Row {
            if(identity==null||identity.isBlank())throw new IllegalArgumentException("TABLE_ROW_IDENTITY_REQUIRED");
            values=SemanticJson.object(values);Objects.requireNonNull(quality);proofs=Map.copyOf(proofs);aliases=Set.copyOf(aliases);
            if(!aliases.contains(identity)||aliases.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("TABLE_ROW_ALIASES_INVALID");
        }
    }
    public record Dataset(List<Row> rows,Completeness populationQuality) {
        public Dataset { rows=List.copyOf(rows);Objects.requireNonNull(populationQuality);bounded(rows.size()); }
    }
    public record Result(List<Row> rows,Completeness populationQuality,List<String> diagnostics) {
        public Result { rows=List.copyOf(rows);Objects.requireNonNull(populationQuality);diagnostics=List.copyOf(diagnostics); }
    }
    private record Table(Node node,List<Node> operations) { }
    private record Working(Row source,Map<String,Object> data,Map<String,Object> columns,
                           Completeness quality,String identity,Set<String> aliases,Map<String,String> proofs) {
        Working partial(){return new Working(source,data,columns,merge(quality,Completeness.PARTIAL),identity,aliases,proofs);}
        Working data(Map<String,Object> next){return new Working(source,next,columns,quality,identity,aliases,proofs);}
        Working evidence(String alias,Row joined) {
            var enriched=new LinkedHashMap<>(proofs);
            joined.proofs().forEach((key,value)->{
                String name="join:"+alias+":"+key;
                if(name.length()>512||enriched.putIfAbsent(name,value)!=null)throw new IllegalArgumentException("RUNTIME_JOIN_PROOF_COLLISION_OR_DEPTH");
            });
            if(enriched.size()>256)throw new IllegalArgumentException("RUNTIME_JOIN_PROOF_CAPACITY");
            // Source values are already deeply owned by Row. Adding immutable
            // proof metadata must not copy their complete history on every join.
            return new Working(source,data,columns,quality,identity,aliases,Map.copyOf(enriched));
        }
    }
    private static final class Budget {
        private int work;
        void spend(){if(++work>MAX_WORK)throw new IllegalArgumentException("RUNTIME_TABLE_WORK_LIMIT");}
    }
    private final List<Table> tables;
    private final Map<Node,String> boundIdentities;

    public RuntimeTables(MappingSpecification specification) {
        this(specification,null);
    }
    public RuntimeTables(MappingSpecification specification,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex bindings) {
        var sections=specification.root().child("runtime").children("tables");
        if(sections.size()>1)throw specification.root().location().error("RUNTIME_TABLES_DUPLICATE");
        var compiled=new ArrayList<Table>();var prior=new HashSet<String>();var bound=new HashMap<Node,String>();
        if(!sections.isEmpty())for(Node table:sections.getFirst().children("table")) {
            if(compiled.size()>=MAX_TABLES)throw table.location().error("RUNTIME_TABLE_LIMIT");
            identifier(table.attr("dataset"),table);var columns=new HashSet<String>();var aliases=new HashSet<String>();
            boolean projected=false,identity=false;
            if(table.children().size()>64)throw table.location().error("RUNTIME_TABLE_INSTRUCTION_LIMIT");
            for(Node operation:table.children()) {
                if(identity)throw operation.location().error("IDENTITY_MUST_BE_LAST");
                switch(operation.name()) {
                    case "whereBound" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);
                        Node declaration=specification.requireId(operation.attr("bindingRef"));
                        if(!declaration.name().equals("static")||bindings==null)throw operation.location().error("FROZEN_STATIC_BINDING_REQUIRED");
                        var selected=bindings.bindings().stream().filter(b->b.ruleId().equals(declaration.attr("id"))).toList();
                        if(selected.size()!=1||!selected.getFirst().kind().name().equals(declaration.attr("kind"))||!selected.getFirst().targetId().equals(declaration.attr("target")))throw operation.location().error("STATIC_BINDING_IDENTITY_NOT_UNIQUE");
                        bound.put(operation,selected.getFirst().sourceId());
                    }
                    case "whereExists" -> {
                        beforeProjection(projected,operation);path(operation.attr("leftPath"),operation);path(operation.attr("rightPath"),operation);
                        if(!prior.contains(operation.attr("tableRef")))throw operation.location().error("EXISTENCE_REQUIRES_PRIOR_TABLE");
                        name(operation.attr("as"),operation);if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                    }
                    case "where", "whereNot" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);
                        literal(operation.attr("equals"),operation.attr("type"),operation);
                    }
                    case "test" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);name(operation.attr("as"),operation);bool(operation,"negate");literal(operation.attr("equals"),operation.attr("type"),operation);
                        if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                    }
                    case "whereFieldsEqual" -> {
                        beforeProjection(projected,operation);path(operation.attr("leftPath"),operation);path(operation.attr("rightPath"),operation);
                        if(!Set.of("String","Boolean","Integer").contains(operation.attr("type")))throw operation.location().error("TABLE_EQUALITY_TYPE_UNSUPPORTED");
                    }
                    case "whereSize" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);
                        if(!operation.attr("equals").matches("0|[1-9][0-9]{0,3}")||Integer.parseInt(operation.attr("equals"))>MAX_ROWS)throw operation.location().error("TABLE_SIZE_LITERAL_INVALID");
                    }
                    case "whereAny" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);
                        if(operation.children("value").isEmpty()||operation.children("value").size()>8)throw operation.location().error("TABLE_TYPED_ALTERNATIVE_LIMIT");
                        for(Node alternative:operation.children("value"))literal(alternative.attr("equals"),operation.attr("type"),alternative);
                    }
                    case "termString" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);path(operation.attr("rawPath"),operation);name(operation.attr("as"),operation);
                        if(!Set.of("JACA_3_1_STRING_OR_ATOM","JACA_3_1_STRING_OR_GROUND_TERM").contains(operation.attr("mode")))throw operation.location().error("TERM_STRING_MODE_UNSUPPORTED");
                        if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                    }
                    case "expand" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);
                        name(operation.attr("as"),operation);if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                        if(!operation.optional("valueType").isEmpty()&&!Set.of("String","Boolean","Integer").contains(operation.attr("valueType")))throw operation.location().error("SCALAR_EXPANSION_TYPE_UNSUPPORTED");
                    }
                    case "firstBy" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);path(operation.attr("historyPath"),operation);
                        var grouping=words(operation.attr("groupPaths"));if(grouping.isEmpty()||new HashSet<>(grouping).size()!=grouping.size())throw operation.location().error("FIRST_BY_GROUP_PATHS_REQUIRED");
                        grouping.forEach(p->path(p,operation));
                    }
                    case "selectValues" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);path(operation.attr("valuePath"),operation);path(operation.attr("completePath"),operation);
                        name(operation.attr("as"),operation);if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");bool(operation,"distinct");
                        if(!Set.of("String","Boolean","Integer").contains(operation.attr("valueType")))throw operation.location().error("TABLE_VALUE_TYPE_UNSUPPORTED");
                        long match=List.of("matchPath","matchType","equals").stream().filter(operation.attributes()::containsKey).count();
                        if(match!=0&&match!=3)throw operation.location().error("TABLE_VALUE_MATCH_REQUIRES_COMPLETE_SELECTOR");
                        if(match==3){path(operation.attr("matchPath"),operation);literal(operation.attr("equals"),operation.attr("matchType"),operation);}
                        if(operation.children("match").size()>8||match!=0&&!operation.children("match").isEmpty())throw operation.location().error("TABLE_VALUE_PREDICATES_MIXED_OR_LIMIT");
                        for(Node predicate:operation.children("match")){path(predicate.attr("path"),predicate);literal(predicate.attr("equals"),predicate.attr("type"),predicate);}
                    }
                    case "firstMatch" -> {
                        beforeProjection(projected,operation);path(operation.attr("path"),operation);path(operation.attr("orderPath"),operation);path(operation.attr("completePath"),operation);name(operation.attr("as"),operation);
                        if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                        if(operation.children("match").isEmpty()||operation.children("match").size()>8)throw operation.location().error("FIRST_MATCH_TYPED_PREDICATES_REQUIRED");
                        for(Node match:operation.children("match")){path(match.attr("path"),match);literal(match.attr("equals"),match.attr("type"),match);}
                    }
                    case "choose" -> {
                        beforeProjection(projected,operation);name(operation.attr("as"),operation);for(String field:List.of("conditionPath","truePath","falsePath"))path(operation.attr(field),operation);
                        if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                    }
                    case "relativeTime", "sampleClock" -> {
                        if(!operation.optional("onMissing").isEmpty()&&!operation.attr("onMissing").equals("unavailable-value"))throw operation.location().error("RELATIVE_TIME_MISSING_POLICY_UNSUPPORTED");
                        beforeProjection(projected,operation);if(operation.name().equals("relativeTime"))path(operation.attr("epochPath"),operation);path(operation.attr("sourceClockPath"),operation);name(operation.attr("as"),operation);
                        if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                        Node request=specification.requireId(operation.attr("requestRef"));
                        if(!request.name().equals("capability")||!request.attr("provider").equals("npl.source-clock.v1")||!request.optional("hash").matches("[0-9a-f]{64}"))throw operation.location().error("RELATIVE_TIME_PINNED_SOURCE_CLOCK_REQUIRED");
                        if(specification.root().child("runtime").children("datasets").stream().flatMap(s->s.children("clock").stream()).noneMatch(c->c.attr("requestRef").equals(request.attr("id"))))throw operation.location().error("RELATIVE_TIME_CLOCK_DATASET_REQUIRED");
                    }
                    case "join" -> {
                        beforeProjection(projected,operation);path(operation.attr("leftPath"),operation);path(operation.attr("rightPath"),operation);
                        if(!prior.contains(operation.attr("tableRef")))throw operation.location().error("JOIN_REQUIRES_PRIOR_TABLE");
                        if(!Set.of("ONE","MANY").contains(operation.attr("cardinality")))throw operation.location().error("JOIN_CARDINALITY_UNSUPPORTED");
                        if(!operation.optional("knowledge").isEmpty()&&(!operation.attr("knowledge").equals("QUALIFIED_PHYSICAL_REFERENCE")||!operation.attr("cardinality").equals("ONE")))throw operation.location().error("JOIN_KNOWLEDGE_POLICY_UNSUPPORTED");
                        if(!operation.optional("onMissing").isEmpty()&&(!operation.attr("onMissing").equals("unavailable-value")||!operation.attr("cardinality").equals("ONE")||!operation.attr("required").equals("false")||!operation.optional("knowledge").equals("QUALIFIED_PHYSICAL_REFERENCE")))throw operation.location().error("JOIN_MISSING_VALUE_POLICY_UNSUPPORTED");
                        bool(operation,"required");name(operation.attr("as"),operation);
                        if(!aliases.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_ALIAS");
                    }
                    case "column" -> {
                        projected=true;name(operation.attr("name"),operation);path(operation.attr("path"),operation);bool(operation,"required");
                        if(!columns.add(operation.attr("name")))throw operation.location().error("DUPLICATE_TABLE_COLUMN");
                    }
                    case "identity" -> {
                        identity=true;
                        switch(operation.attr("mode")) {
                            case "PHYSICAL" -> {
                                path(operation.attr("path"),operation);
                                if(!operation.optional("fields").isEmpty())throw operation.location().error("PHYSICAL_IDENTITY_FIELDS_UNSUPPORTED");
                            }
                            case "LOGICAL_ROLE" -> {
                                var fields=words(operation.attr("fields"));
                                if(!operation.optional("path").isEmpty()||fields.isEmpty()||new HashSet<>(fields).size()!=fields.size()||!columns.containsAll(fields))throw operation.location().error("LOGICAL_IDENTITY_COLUMNS_REQUIRED");
                            }
                            default -> throw operation.location().error("TABLE_IDENTITY_UNSUPPORTED");
                        }
                        if(!operation.optional("as").isEmpty()) {
                            name(operation.attr("as"),operation);
                            if(!columns.add(operation.attr("as")))throw operation.location().error("DUPLICATE_TABLE_COLUMN");
                        }
                    }
                    default -> throw operation.location().error("TABLE_INSTRUCTION_UNSUPPORTED:"+operation.name());
                }
            }
            if(columns.isEmpty())throw table.location().error("TABLE_COLUMNS_REQUIRED");
            compiled.add(new Table(table,List.copyOf(table.children())));prior.add(table.attr("id"));
        }
        tables=List.copyOf(compiled);boundIdentities=Map.copyOf(bound);
    }

    public Map<String,Result> execute(Map<String,Dataset> datasets) {
        return execute(datasets,Map.of());
    }
    public Map<String,Result> execute(Map<String,Dataset> datasets,Map<String,SourceClock> clocks) {
        var results=new LinkedHashMap<String,Result>();var budget=new Budget();
        for(Table table:tables) {
            var dataset=datasets.get(table.node().attr("dataset"));var diagnostics=new ArrayList<String>();
            if(dataset==null) {
                results.put(table.node().attr("id"),new Result(List.of(),Completeness.UNAVAILABLE,List.of("DATASET_UNAVAILABLE:"+table.node().attr("dataset"))));continue;
            }
            var rows=new ArrayList<Working>();Completeness population=dataset.populationQuality();
            for(Row row:dataset.rows())rows.add(new Working(row,row.values(),new LinkedHashMap<>(),row.quality(),row.identity(),row.aliases(),row.proofs()));
            for(Node op:table.operations()) {
                if(op.name().equals("firstBy")){rows=firstBy(rows,op,budget,diagnostics);continue;}
                var next=new ArrayList<Working>();
                for(Working row:rows) {
                    budget.spend();
                    switch(op.name()) {
                        case "whereBound" -> {
                            Object value=resolve(row.data(),op.attr("path"));
                            if(!(value instanceof String)) {next.add(row.partial());diagnose(diagnostics,op,"BOUND_IDENTITY_VALUE_UNAVAILABLE");}
                            else if(value.equals(boundIdentities.get(op)))next.add(row);
                        }
                        case "whereExists" -> {
                            Result right=results.get(op.attr("tableRef"));Object key=resolve(row.data(),op.attr("leftPath"));
                            boolean uncertain=right.populationQuality()!=Completeness.COMPLETE;Row witness=null;
                            for(Row candidate:right.rows()) {
                                budget.spend();Object other=resolve(candidate.values(),op.attr("rightPath"));
                                if(!(other instanceof String)||candidate.quality()!=Completeness.COMPLETE){uncertain=true;continue;}
                                if(key instanceof String&&key.equals(other)&&(witness==null||candidate.identity().compareTo(witness.identity())<0))witness=candidate;
                            }
                            if(key instanceof String&&witness!=null)next.add(row.evidence(op.attr("as"),witness));
                            else if(!(key instanceof String)||uncertain){next.add(row.partial());diagnose(diagnostics,op,"EXISTENCE_UNAVAILABLE");}
                            // Known absence in a complete qualified relation filters a row; unknown absence never does.
                        }
                        case "where", "whereNot" -> {
                            Object value=resolve(row.data(),op.attr("path"));Object expected=literal(op.attr("equals"),op.attr("type"),op);
                            if(!type(value,op.attr("type"))) { next.add(row.partial());diagnose(diagnostics,op,"WHERE_VALUE_UNAVAILABLE"); }
                            else if(equal(value,expected)!=op.name().equals("whereNot"))next.add(row);
                        }
                        case "test" -> {
                            Object value=resolve(row.data(),op.attr("path"));
                            if(!type(value,op.attr("type"))){next.add(row.partial());diagnose(diagnostics,op,"TEST_VALUE_UNAVAILABLE");}
                            else next.add(row.data(withAlias(row.data(),op.attr("as"),equal(value,literal(op.attr("equals"),op.attr("type"),op))!=bool(op,"negate"),op)));
                        }
                        case "whereFieldsEqual" -> {
                            Object left=resolve(row.data(),op.attr("leftPath")),right=resolve(row.data(),op.attr("rightPath"));
                            if(!type(left,op.attr("type"))||!type(right,op.attr("type"))){next.add(row.partial());diagnose(diagnostics,op,"WHERE_EQUALITY_VALUE_UNAVAILABLE");}
                            else if(equal(left,right))next.add(row);
                        }
                        case "whereSize" -> {
                            Object value=resolve(row.data(),op.attr("path"));
                            if(!(value instanceof List<?> list)){next.add(row.partial());diagnose(diagnostics,op,"WHERE_SIZE_COLLECTION_UNAVAILABLE");}
                            else {budget.spend();bounded(list.size());if(list.size()==Integer.parseInt(op.attr("equals")))next.add(row);}
                        }
                        case "whereAny" -> {
                            Object value=resolve(row.data(),op.attr("path"));
                            if(!type(value,op.attr("type"))){next.add(row.partial());diagnose(diagnostics,op,"WHERE_VALUE_UNAVAILABLE");}
                            else if(op.children("value").stream().anyMatch(v->equal(value,literal(v.attr("equals"),op.attr("type"),v))))next.add(row);
                        }
                        case "termString" -> {
                            String value=termString(resolve(row.data(),op.attr("path")),resolve(row.data(),op.attr("rawPath")),op.attr("mode"));
                            if(value==null){next.add(row.partial());diagnose(diagnostics,op,"TERM_STRING_SOURCE_OR_TYPE_UNAVAILABLE");}
                            else next.add(row.data(withAlias(row.data(),op.attr("as"),value,op)));
                        }
                        case "expand" -> {
                            Object value=resolve(row.data(),op.attr("path"));
                            if(!(value instanceof List<?> list)) {next.add(row.partial());diagnose(diagnostics,op,"EXPANSION_UNAVAILABLE");}
                            else for(Object item:list) {
                                budget.spend();
                                String scalar=op.optional("valueType");
                                if(scalar.isEmpty()?!(item instanceof Map<?,?>):!type(item,scalar)) {next.add(row.partial());diagnose(diagnostics,op,"EXPANSION_ROW_UNAVAILABLE");}
                                else next.add(row.data(withAlias(row.data(),op.attr("as"),scalar.isEmpty()?item:Map.of("value",item),op)));
                                bounded(next.size());
                            }
                        }
                        case "firstMatch" -> {
                            Map<String,Object> selected=firstMatch(row.data(),op,budget);
                            next.add(row.data(withAlias(row.data(),op.attr("as"),selected,op)));
                            if(!Boolean.TRUE.equals(selected.get("known")))diagnose(diagnostics,op,"FIRST_MATCH_PREFIX_OR_UNIQUE_MINIMUM_UNAVAILABLE");
                        }
                        case "choose" -> {
                            Object condition=resolve(row.data(),op.attr("conditionPath")),value=null;
                            if(condition instanceof Boolean flag)value=resolve(row.data(),op.attr(flag?"truePath":"falsePath"));
                            if(value==null){next.add(row.partial());diagnose(diagnostics,op,"CHOSEN_VALUE_OR_CONDITION_UNAVAILABLE");}
                            else next.add(row.data(withAlias(row.data(),op.attr("as"),value,op)));
                        }
                        case "selectValues" -> {
                            Object source=resolve(row.data(),op.attr("path"));boolean known=Boolean.TRUE.equals(resolve(row.data(),op.attr("completePath")));
                            var values=new ArrayList<Object>();
                            if(source instanceof List<?> list) {
                                bounded(list.size());
                                for(Object item:list) {
                                    budget.spend();boolean selected=true;
                                    if(op.attributes().containsKey("matchPath")) {
                                        Object value=resolve(item,op.attr("matchPath"));
                                        if(!type(value,op.attr("matchType"))){known=false;continue;}
                                        selected=equal(value,literal(op.attr("equals"),op.attr("matchType"),op));
                                    }else if(!op.children("match").isEmpty()) {
                                        Boolean match=matches(item,op.children("match"),budget);
                                        if(match==null){known=false;continue;}selected=match;
                                    }
                                    if(selected) {
                                        Object value=resolve(item,op.attr("valuePath"));
                                        if(!type(value,op.attr("valueType"))){known=false;continue;}values.add(value);
                                    }
                                }
                            }else known=false;
                            if(known){if(bool(op,"distinct"))values=new ArrayList<>(new LinkedHashSet<>(values));next.add(row.data(withAlias(row.data(),op.attr("as"),List.copyOf(values),op)));}
                            else {next.add(row.partial());diagnose(diagnostics,op,"SELECT_VALUES_COLLECTION_OR_COVERAGE_UNAVAILABLE");}
                        }
                        case "relativeTime" -> {
                            SourceClock clock=clocks.get(op.attr("requestRef"));Object raw=resolve(row.data(),op.attr("epochPath")),identity=resolve(row.data(),op.attr("sourceClockPath"));Integer relative=null;
                            if(clock!=null&&clock.quality()==Completeness.COMPLETE&&clock.clockIdentity().equals(identity)&&raw instanceof String text)try {
                                long epoch=Long.parseLong(text);if(epoch>=0&&epoch<9007199254740992L)relative=clock.relative(epoch).value();
                            }catch(NumberFormatException ignored){ }
                            if(relative==null){next.add(op.optional("onMissing").equals("unavailable-value")?row:row.partial());diagnose(diagnostics,op,"RELATIVE_SOURCE_TIME_UNAVAILABLE_OR_RANGE");}
                            else next.add(row.data(withAlias(row.data(),op.attr("as"),relative,op)));
                        }
                        case "sampleClock" -> {
                            SourceClock clock=clocks.get(op.attr("requestRef"));Integer relative=null;
                            if(clock!=null&&clock.quality()==Completeness.COMPLETE&&clock.clockIdentity().equals(resolve(row.data(),op.attr("sourceClockPath"))))relative=clock.relative(clock.nowEpochMillis()).value();
                            // Availability belongs to this value, not to independent qualified columns.
                            Map<String,Object> sample=relative==null?Map.of("known",false):Map.of("known",true,"now",relative);
                            next.add(row.data(withAlias(row.data(),op.attr("as"),sample,op)));
                            if(relative==null)diagnose(diagnostics,op,"CURRENT_SOURCE_TIME_UNAVAILABLE");
                        }
                        case "join" -> {
                            Result right=results.get(op.attr("tableRef"));Object key=resolve(row.data(),op.attr("leftPath"));
                            var matches=new ArrayList<Row>();boolean uncertain=right.populationQuality()!=Completeness.COMPLETE;int aliasCandidates=0;
                            for(Row candidate:right.rows()) {
                                budget.spend();Object other=resolve(candidate.values(),op.attr("rightPath"));
                                if(key instanceof String identity&&candidate.aliases().contains(identity))aliasCandidates++;
                                if(other==null||candidate.quality()!=Completeness.COMPLETE)uncertain=true;
                                if(key!=null&&other!=null&&equal(key,other))matches.add(candidate);
                            }
                            if(key==null||(op.attr("cardinality").equals("ONE")&&matches.size()>1)||matches.isEmpty()) {
                                if(key instanceof String&&matches.isEmpty()&&op.optional("onMissing").equals("unavailable-value")){next.add(row);diagnose(diagnostics,op,"OPTIONAL_PHYSICAL_VALUE_UNAVAILABLE_NOT_ABSENT");}
                                else if(key!=null&&matches.isEmpty()&&!uncertain&&!bool(op,"required"))next.add(row);
                                else {next.add(row.partial());diagnose(diagnostics,op,"JOIN_UNAVAILABLE_OR_AMBIGUOUS");}
                            } else for(Row match:matches) {
                                Working joined=row.data(withAlias(row.data(),op.attr("as"),match.values(),op)).evidence(op.attr("as"),match);
                                String sourceCut=row.source().proofs().getOrDefault("source-cut","");
                                boolean physical=op.optional("knowledge").equals("QUALIFIED_PHYSICAL_REFERENCE")&&matches.size()==1&&aliasCandidates==1
                                    &&key instanceof String identity&&match.aliases().contains(identity)&&match.identity().equals(match.proofs().get("physical-identity"))
                                    &&!sourceCut.isBlank()&&sourceCut.equals(match.proofs().get("source-cut"))&&match.quality()==Completeness.COMPLETE;
                                if(uncertain&&!physical||match.quality()!=Completeness.COMPLETE) {joined=joined.partial();diagnose(diagnostics,op,"JOIN_POPULATION_INCOMPLETE");}
                                next.add(joined);bounded(next.size());
                            }
                        }
                        case "column" -> {
                            Object value=resolve(row.data(),op.attr("path"));var columns=new LinkedHashMap<>(row.columns());
                            Working updated=row;
                            if(value!=null)columns.put(op.attr("name"),value);
                            else if(bool(op,"required")){updated=row.partial();diagnose(diagnostics,op,"COLUMN_UNAVAILABLE:"+op.attr("name"));}
                            next.add(new Working(updated.source(),updated.data(),columns,updated.quality(),updated.identity(),updated.aliases(),updated.proofs()));
                        }
                        case "identity" -> {
                            if(op.attr("mode").equals("PHYSICAL")) {
                                Object id=resolve(row.data(),op.attr("path"));
                                if(id instanceof String text&&!text.isBlank()&&row.source().aliases().contains(text))next.add(identified(row,text,row.aliases(),op));
                                else {next.add(row.partial());diagnose(diagnostics,op,"PHYSICAL_IDENTITY_UNVERIFIED");}
                            } else {
                                var tuple=new LinkedHashMap<String,Object>();boolean valid=true;
                                for(String field:words(op.attr("fields"))) {
                                    Object value=row.columns().get(field);
                                    if(!(value instanceof String text)||text.isBlank()){valid=false;break;}tuple.put(field,value);
                                }
                                if(valid) {
                                    String id="logical:"+SemanticJson.hash(Map.of("source",row.source().identity(),"table",table.node().attr("id"),"tuple",tuple));
                                    next.add(identified(row,id,Set.of(id),op));
                                } else {next.add(row.partial());diagnose(diagnostics,op,"LOGICAL_IDENTITY_UNAVAILABLE");}
                            }
                        }
                        default -> throw op.location().error("TABLE_INSTRUCTION_UNSUPPORTED");
                    }
                    bounded(next.size());
                }
                rows=next;
            }
            var output=new ArrayList<Row>();
            for(Working row:rows){population=merge(population,row.quality());output.add(new Row(row.identity(),row.columns(),row.quality(),row.proofs(),row.aliases()));}
            if(!diagnostics.isEmpty())population=merge(population,Completeness.PARTIAL);
            results.put(table.node().attr("id"),new Result(output,population,diagnostics));
        }
        return Collections.unmodifiableMap(results);
    }

    /** An exported identity is a proven relation key, not a new source alias from a join. */
    private static Working identified(Working row,String identity,Set<String> aliases,Node operation) {
        var columns=new LinkedHashMap<>(row.columns());
        if(!operation.optional("as").isEmpty())columns.put(operation.attr("as"),identity);
        return new Working(row.source(),row.data(),columns,row.quality(),identity,aliases,row.proofs());
    }
    public static Set<String> outputColumns(Node table) {
        if(!table.name().equals("table"))throw table.location().error("RUNTIME_TABLE_REQUIRED");
        var result=new LinkedHashSet<String>();
        for(Node operation:table.children()) {
            String field=operation.name().equals("column")?operation.attr("name"):operation.name().equals("identity")?operation.optional("as"):"";
            if(!field.isEmpty()&&!result.add(field))throw operation.location().error("DUPLICATE_TABLE_COLUMN");
        }
        return Set.copyOf(result);
    }

    private static ArrayList<Working> firstBy(List<Working> rows,Node op,Budget budget,List<String> diagnostics) {
        var groups=new LinkedHashMap<String,List<Working>>();var selected=new ArrayList<Working>();
        for(Working row:rows) {
            budget.spend();var keys=new ArrayList<Object>();boolean valid=true;
            for(String path:words(op.attr("groupPaths"))) {
                Object value=resolve(row.data(),path);
                if(value==null||!(value instanceof String||value instanceof Boolean||value instanceof Number)){valid=false;break;}keys.add(value);
            }
            if(!valid){selected.add(row.partial());diagnose(diagnostics,op,"FIRST_BY_GROUP_KEY_UNAVAILABLE");}
            else groups.computeIfAbsent(SemanticJson.hash(keys),key->new ArrayList<>()).add(row);
        }
        for(var group:groups.values()) {
            Working candidate=null;BigDecimal minimum=null;boolean qualified=true,ambiguous=false;
            for(Working row:group) {
                budget.spend();Object value=resolve(row.data(),op.attr("path"));BigDecimal order=null;
                if(value instanceof Number number)try{var exact=new BigDecimal(number.toString());exact.toBigIntegerExact();order=exact;}catch(NumberFormatException|ArithmeticException ignored){ }
                if(order==null||row.quality()!=Completeness.COMPLETE||!Boolean.TRUE.equals(resolve(row.data(),op.attr("historyPath"))))qualified=false;
                if(order!=null&&(minimum==null||order.compareTo(minimum)<0)){minimum=order;candidate=row;ambiguous=false;}
                else if(order!=null&&minimum!=null&&order.compareTo(minimum)==0)ambiguous=true;
            }
            if(!qualified||ambiguous||candidate==null){selected.add(group.getFirst().partial());diagnose(diagnostics,op,"FIRST_BY_PREFIX_OR_UNIQUE_MINIMUM_UNAVAILABLE");}
            else selected.add(candidate);
        }
        bounded(selected.size());return selected;
    }

    private static Map<String,Object> withAlias(Map<String,Object> data,String alias,Object value,Node op) {
        if(data.containsKey(alias))throw op.location().error("TABLE_ALIAS_SOURCE_COLLISION:"+alias);
        var result=new LinkedHashMap<>(data);result.put(alias,value);return result;
    }
    /** Exact producer rendering after the pinned JaCa 3.1 conversion; no consumer parser or term evaluation. */
    private static String termString(Object typed,Object raw,String mode) {
        if(!(typed instanceof Map<?,?> term)||!(raw instanceof String syntax)||!syntax.equals(term.get("raw"))
                ||!Boolean.TRUE.equals(term.get("supported"))||!Boolean.TRUE.equals(term.get("ground"))||!(term.get("key") instanceof Map<?,?> key))return null;
        if("STRING".equals(key.get("kind")))return key.get("value") instanceof String value?value:null;
        if(!"LITERAL".equals(key.get("kind"))||!"default".equals(key.get("namespace"))||!Boolean.FALSE.equals(key.get("negative"))
                ||!(key.get("terms") instanceof List<?> args)||!(key.get("functor") instanceof String functor)||functor.isBlank())return null;
        if(args.isEmpty())return Set.of("true","false").contains(functor)?null:syntax;
        return mode.equals("JACA_3_1_STRING_OR_GROUND_TERM")?syntax:null;
    }
    /** Select within one qualified local prefix. No global population/absence authority is gained. */
    private static Map<String,Object> firstMatch(Map<String,Object> data,Node operation,Budget budget) {
        Object raw=resolve(data,operation.attr("path"));boolean known=Boolean.TRUE.equals(resolve(data,operation.attr("completePath")));
        if(!(raw instanceof List<?> list))return Map.of("known",false);
        bounded(list.size());Object selected=null;BigDecimal minimum=null;boolean ambiguous=false;
        for(Object item:list) {
            budget.spend();Boolean matches=matches(item,operation.children("match"),budget);
            if(Boolean.FALSE.equals(matches))continue;
            if(matches==null){known=false;continue;}
            Object sequence=resolve(item,operation.attr("orderPath"));BigDecimal order=null;
            if(sequence instanceof Number number)try{order=new BigDecimal(number.toString());order.toBigIntegerExact();if(order.signum()<0)order=null;}catch(NumberFormatException|ArithmeticException invalid){order=null;}
            if(order==null){known=false;continue;}
            if(minimum==null||order.compareTo(minimum)<0){minimum=order;selected=item;ambiguous=false;}
            else if(order.compareTo(minimum)==0)ambiguous=true;
        }
        if(!known||ambiguous)return Map.of("known",false);
        return selected==null?Map.of("known",true,"found",false):Map.of("known",true,"found",true,"value",selected);
    }
    private static Boolean matches(Object item,List<Node> predicates,Budget budget) {
        boolean known=true;
        for(Node predicate:predicates) {
            budget.spend();Object value=resolve(item,predicate.attr("path"));
            if(!type(value,predicate.attr("type")))known=false;
            else if(!equal(value,literal(predicate.attr("equals"),predicate.attr("type"),predicate)))return Boolean.FALSE;
        }
        return known?Boolean.TRUE:null;
    }
    private static Object resolve(Object data,String path) {
        Object value=data;
        for(String part:path.split("\\.")) {
            if(value instanceof Map<?,?> map)value=map.get(part);
            else if(value instanceof List<?> list&&part.matches("0|[1-9][0-9]{0,3}")){int index=Integer.parseInt(part);value=index<list.size()?list.get(index):null;}
            else return null;
        }
        return value;
    }
    private static void path(String value,Node at) {
        var parts=value.split("\\.",-1);
        if(parts.length>16||parts.length==0)throw at.location().error("TABLE_PATH_INVALID");
        for(String part:parts)if(!part.matches("[A-Za-z_][A-Za-z0-9_]*|0|[1-9][0-9]{0,3}"))throw at.location().error("TABLE_PATH_INVALID:"+value);
    }
    private static void name(String value,Node at){if(!value.matches("[A-Za-z_][A-Za-z0-9_]*"))throw at.location().error("TABLE_NAME_INVALID");}
    private static void identifier(String value,Node at){if(!value.matches("[A-Za-z_][A-Za-z0-9_.-]*"))throw at.location().error("TABLE_DATASET_INVALID");}
    private static void beforeProjection(boolean projected,Node at){if(projected)throw at.location().error("TABLE_RELATION_AFTER_PROJECTION");}
    private static boolean bool(Node at,String name){if(!Set.of("true","false").contains(at.attr(name)))throw at.location().error("BOOLEAN_REQUIRED:"+name);return at.attr(name).equals("true");}
    private static List<String> words(String value){return value.isBlank()?List.of():List.of(value.trim().split("\\s+"));}
    private static Object literal(String value,String type,Node at) {
        return switch(type) {
            case "String"->value;
            case "Boolean"->{if(!Set.of("true","false").contains(value))throw at.location().error("TABLE_BOOLEAN_LITERAL_INVALID");yield Boolean.valueOf(value);}
            case "Integer"->{try{yield new BigDecimal(value).toBigIntegerExact();}catch(NumberFormatException|ArithmeticException e){throw at.location().error("TABLE_INTEGER_LITERAL_INVALID");}}
            default->throw at.location().error("TABLE_LITERAL_TYPE_UNSUPPORTED");
        };
    }
    private static boolean type(Object value,String type) {
        if(type.equals("String"))return value instanceof String;
        if(type.equals("Boolean"))return value instanceof Boolean;
        if(type.equals("Integer")&&value instanceof Number n)try{new BigDecimal(n.toString()).toBigIntegerExact();return true;}catch(NumberFormatException|ArithmeticException e){return false;}
        return false;
    }
    private static boolean equal(Object left,Object right) {
        if(left instanceof Number a&&right instanceof Number b)return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;
        return Objects.equals(left,right);
    }
    private static Completeness merge(Completeness a,Completeness b){return a.ordinal()>=b.ordinal()?a:b;}
    private static void bounded(int size){if(size>MAX_ROWS)throw new IllegalArgumentException("RUNTIME_TABLE_ROW_LIMIT");}
    private static void diagnose(List<String> diagnostics,Node op,String message){if(diagnostics.size()<256)diagnostics.add(op.location()+":"+message);}
}
