package auctionfixture;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jason.infra.local.BaseLocalMAS;
import jason.asSemantics.Intention;
import jason.asSemantics.GoalListener;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.SourceInfo;
import jason.asSyntax.Trigger;
import jason.asSyntax.Term;
import org.jacamo.bridge.contract.CanonicalJson;

/** Explicit test fixture, compiled into the owned producer staging directory.
 * No JUnit, consumer, fake observations or JaCaMo/core patches. Canonical source
 * files and their instrumented source task bodies remain unchanged. */
public final class AuctionControlProducerMain {
    public static void main(String[] args)throws Exception {
        Path fixture=Path.of(System.getProperty("auction.fixture.directory")).toRealPath();
        Path startup=Path.of(args[6]).toAbsolutePath(),stop=Path.of(args[1]).toAbsolutePath();
        Thread.ofPlatform().daemon().name("auction-test-fixture-controls").start(()->{
            try {control(fixture,startup,stop);}catch(Throwable error) {
                error.printStackTrace();try{Files.writeString(fixture.resolve("failure.txt"),error.toString());}catch(Exception suppressed){error.addSuppressed(suppressed);}
            }
        });
        jason.infra.local.LiveJaCaMoLauncherMain.main(args);
    }
    private static void control(Path fixture,Path startup,Path stop)throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(!Files.isRegularFile(startup.resolve("waiting.json"))&&System.nanoTime()<until&&!Files.exists(stop))Thread.sleep(20);
        if(!Files.isRegularFile(startup.resolve("waiting.json")))throw new IllegalStateException("E02_MANAGED_WAIT_REQUIRED");
        var runner=BaseLocalMAS.getRunner();if(runner==null||runner.getAgs().size()!=5)throw new IllegalStateException("E02_FIVE_REAL_AGENTS_REQUIRED");
        Path source=fixture.resolve("control.asl");var lines=Files.readAllLines(source);long seed=failingSeed();
        var seeds=new TreeMap<String,Object>();
        var failures=java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        var failedSchemes=java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        var initialCommitmentsRequested=new java.util.concurrent.atomic.AtomicBoolean();
        var lateRequested=new java.util.concurrent.atomic.AtomicBoolean();
        for(var entry:runner.getAgs().entrySet()) {
            var ts=entry.getValue().getTS();
            // Official Jason per-agent IA state, before managed Start releases reasoning.
            jason.stdlib.set_random_seed.getAgRandomIA(ts).setSeed(seed);
            int line=0;for(String text:lines){line++;if(text.isBlank()||text.startsWith("//"))continue;
                var plan=ASSyntax.parsePlan(text);plan.setSrcInfo(new SourceInfo(source.toString(),line));ts.getAg().getPL().add(plan);
            }
            var preview=new Random(seed);seeds.put(entry.getKey(),Map.of("seed",seed,"first",preview.nextDouble(),"second",preview.nextDouble()));
            String actor=entry.getKey();ts.addGoalListener(new GoalListener() {
                @Override public void goalStarted(jason.asSemantics.Event event){observe(fixture,actor,"STARTED",event.getTrigger(),"");}
                @Override public void goalExecuting(Trigger goal,Term reason){observe(fixture,actor,"EXECUTING",goal,Objects.toString(reason,""));}
                @Override public void goalFailed(Trigger goal,Term reason){observe(fixture,actor,"FAILED",goal,Objects.toString(reason,""));}
                @Override public void goalFinished(Trigger goal,GoalStates state){observe(fixture,actor,"FINISHED",goal,Objects.toString(state,""));}
            });
            if(!entry.getKey().equals("bob")&&(!Boolean.getBoolean("auction.fixture.lateRole")||!entry.getKey().equals("giacomo"))) {
                String name=entry.getKey();ts.addGoalListener(new GoalListener() {
                    @Override public void goalFailed(Trigger goal,Term reason) {
                        if(!goal.getLiteral().getFunctor().equals("bid"))return;
                        failures.add(name+":"+goal.getLiteral());
                        var scheme=goal.getLiteral().getAnnot("scheme");if(scheme!=null)failedSchemes.add(scheme.getTerm(0).toString());
                        try {
                        // mParticipant has min=1. Canonical norm handling does not promise
                        // every configured role player will commit both schemes. Explicit
                        // fixture requests make that extra test roster requirement real.
                        // SchemeBoard.commitMission is idempotent for an existing player.
                        if(failedSchemes.containsAll(Set.of("sch_a1","sch_a2"))&&initialCommitmentsRequested.compareAndSet(false,true)) {
                            var requested=new TreeSet<String>();
                            for(String participant:List.of("alice","maria","francois","giacomo")) {
                                if(Boolean.getBoolean("auction.fixture.lateRole")&&participant.equals("giacomo"))continue;
                                enqueue(runner.getAg(participant),"e02_commit_both");requested.add(participant);
                            }
                            write(fixture.resolve("initial-commit-request.json"),Map.of("meaning","explicit fixture commitment requests after actual bid failure in both enabled schemes; outcomes still require TCP evidence","players",requested,"failedSchemes",new TreeSet<>(failedSchemes)));
                        }
                        if(Boolean.getBoolean("auction.fixture.lateRole")&&failures.size()==6&&lateRequested.compareAndSet(false,true)) {
                            enqueue(runner.getAg("giacomo"),"e02_join_role");
                            write(fixture.resolve("late-role-request.json"),Map.of("meaning","actual first three players failed both bid goals before late role request","failedSourceGoals",new TreeSet<>(failures)));
                        }
                        }catch(Exception error){try{Files.writeString(fixture.resolve("failure.txt"),error.toString());}catch(Exception ignored){error.printStackTrace();}}
                    }
                });
            }
        }
        write(fixture.resolve("ready.json"),Map.of("profile","AUCTION_DETERMINISTIC_CONTROLS/1.0","producerPid",ProcessHandle.current().pid(),"seeds",seeds,
                "sourceTaskPolicy","canonical plans are untouched; supplemental control plans have no source-qualified Task markers"));
        for(int index=1;!Files.exists(stop);index++) {
            Path request=fixture.resolve(String.format(Locale.ROOT,"command-%03d.json",index));
            while(!Files.isRegularFile(request)&&!Files.exists(stop))Thread.sleep(25);
            if(Files.exists(stop))return;
            if(Files.size(request)>65536)throw new IllegalArgumentException("E02_COMMAND_CAPACITY");
            var body=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(request)));
            if(!body.keySet().equals(Set.of("profile","agent","goal"))||!"AUCTION_DETERMINISTIC_CONTROLS/1.0".equals(body.get("profile")))throw new IllegalArgumentException("E02_COMMAND_SCHEMA");
            String agent=(String)body.get("agent");var architecture=runner.getAg(agent);if(architecture==null)throw new IllegalArgumentException("E02_AGENT_UNKNOWN");
            var goal=ASSyntax.parseLiteral((String)body.get("goal"));
            if(!Set.of("e02_start","e02_bid","e02_stop").contains(goal.getFunctor()))throw new IllegalArgumentException("E02_CONTROL_GOAL_NOT_ALLOWED");
            enqueue(architecture,goal.toString());
            write(fixture.resolve(String.format(Locale.ROOT,"injected-%03d.json",index)),Map.of("producerPid",ProcessHandle.current().pid(),"agent",agent,"goal",goal.toString(),"meaning","request injected only; all outcome/state assertions must use TCP observations"));
        }
    }
    private static void enqueue(jason.infra.local.LocalAgArch architecture,String text)throws Exception {
        var goal=ASSyntax.parseLiteral(text);goal.addAnnot(jason.bb.BeliefBase.TSelf);
        // Jason documents this hook for changes to C from another thread.
        architecture.getTS().runAtBeginOfNextCycle(()->architecture.getTS().getC().addAchvGoal(goal,Intention.EmptyInt));
        architecture.wake();
    }
    private static long failingSeed() {
        for(long candidate=0;candidate<1_000_000;candidate++) {var random=new Random(candidate);if(random.nextDouble()>=0.9&&random.nextDouble()>=0.9)return candidate;}
        throw new IllegalStateException("E02_SEED_NOT_FOUND");
    }
    private static void write(Path file,Object value)throws Exception {
        Path temporary=file.resolveSibling(file.getFileName()+".part");Files.write(temporary,CanonicalJson.encode(value));Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);
    }
    private static synchronized void observe(Path fixture,String actor,String phase,Trigger goal,String detail) {
        if(!goal.getLiteral().getFunctor().startsWith("e02_")&&!goal.getLiteral().getFunctor().equals("bid"))return;
        try {Files.writeString(fixture.resolve("control-lifecycle.ndjson"),new String(CanonicalJson.encode(Map.of("actor",actor,"phase",phase,"goal",goal.toString(),"detail",detail)),java.nio.charset.StandardCharsets.UTF_8)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(Exception error){error.printStackTrace();}
    }
}
