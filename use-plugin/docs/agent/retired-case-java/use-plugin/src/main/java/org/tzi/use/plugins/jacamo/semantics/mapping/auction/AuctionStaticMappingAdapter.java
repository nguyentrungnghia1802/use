package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.semantic.engine.api.*;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;

/** Executes the selected case XML. It never routes through the JSON-rule interpreter or a legacy model. */
public final class AuctionStaticMappingAdapter {
    public static final String PROPERTY="use.jacamo.auction.specification";
    public record Selection(AuctionCaseBundle bundle,AuctionMappingSpecification specification) {
        public Selection {
            Objects.requireNonNull(bundle);Objects.requireNonNull(specification);
            if(!bundle.require("case-rules").sha256().equals(specification.sourceHash()))throw new IllegalArgumentException("AUCTION_SELECTION_SPECIFICATION_MISMATCH");
            new AuctionMappingValidator().validate(specification,bundle);
        }
        public String hash(){return SemanticJson.hash(Map.of("format","AUCTION_XML/1.0","bundle",bundle.hash(),"specification",specification.sourceHash()));}
    }
    public static Selection read(Path xml) {
        try {
            Path selected=xml.toRealPath(),directory=selected.getParent();var bundle=AuctionCaseBundle.read(directory);
            Path expected=directory.resolve(bundle.inputs().get("case-rules").path()).normalize();
            if(!selected.equals(expected)||!Arrays.equals(Files.readAllBytes(selected),bundle.require("case-rules").bytes()))throw new IllegalArgumentException("AUCTION_SELECTED_XML_NOT_BUNDLE_SPECIFICATION");
            return new Selection(bundle,new AuctionMappingXmlLoader().load(bundle));
        }catch(java.io.IOException error){throw new IllegalArgumentException("AUCTION_SPECIFICATION_UNREADABLE",error);}
    }
    public SemanticProgram map(ModelSnapshot input,Selection selection) {
        return map(input,selection,AuctionJcmDerivation.original(selection.bundle(),input));
    }
    public SemanticProgram map(ModelSnapshot input,Selection selection,Path selectedJcm) {
        return map(input,selection,AuctionJcmDerivation.read(selection.bundle(),input,selectedJcm));
    }
    public SemanticProgram restore(ModelSnapshot input,Selection selection,AuctionJcmDerivation derivation) {
        derivation.requireOwnership(selection.bundle(),input);return map(input,selection,derivation);
    }
    private SemanticProgram map(ModelSnapshot input,Selection selection,AuctionJcmDerivation derivation) {
        EngineInstallationGuard.verify();var bundle=selection.bundle();var spec=selection.specification();
        var source=new SourceModelNormalizer().normalize(input);var selectors=new AuctionSourceSelector().execute(spec,bundle,input,derivation);
        var csl=new AuctionCslRuleExecutor().generate(spec,bundle,selectors);
        var iStar=new AuctionIStarSourceAdapter().prepare(bundle);
        var profile=new JaCaMoMappingProfile(AuctionBindingPlan.PROFILE_ID,AuctionBindingPlan.VERSION,EngineSemanticsProfile.DERIVED_AND_RESOURCE_V1,
                Set.of("csl.v4","istar.original","ocl.native75","source.complement","case.xml/1.0","derived.and.resource.v1"));
        var goals=new IStarFrontend().compile(iStar.executableSource(),"Auction:preserved-metadata-view",profile.engine()).requireModel();
        var ocl=new AuctionOclSpecificationLoader().load(bundle);
        var plan=new AuctionBindingPlan(input,source,bundle,spec,selectors,csl,goals,iStar,ocl,derivation);
        // The existing immutable rule container remains a program identity/export surface;
        // its options explicitly identify the XML executor, not a JSON input receipt.
        var rules=new MappingRuleSet("2.0",spec.root().child("profile").attr("id"),profile.id(),profile.version(),source.sourceHash(),
                csl.generatedSource(),iStar.executableSource(),plan.bindings().bindings(),List.of(),List.of(),
                Map.of("inputFormat","AUCTION_XML/1.0","bundleHash",bundle.hash(),"specificationHash",spec.sourceHash(),"casePlanHash",plan.hash()));
        var preserved=new SemanticTransformationService().preserveCanonical(input,source,plan.bindings());
        var assets=new ArrayList<>(preserved.assets());assets.addAll(bundle.assets().values());
        assets.add(derivation.derived());
        assets.add(TraceComplement.Asset.of("auction:case-plan","AUCTION_BINDING_PLAN_JSON",org.jacamo.bridge.contract.CanonicalJson.encode(plan.evidence())));
        var complement=new TraceComplement(preserved.version(),preserved.ruleVersion(),preserved.sourceHash(),preserved.metamodelHash(),assets,preserved.decisions());
        return new SemanticProgram(profile,rules,source,csl.model(),goals,plan.bindings(),complement,List.of(),plan.goalBindings(),plan);
    }
}
