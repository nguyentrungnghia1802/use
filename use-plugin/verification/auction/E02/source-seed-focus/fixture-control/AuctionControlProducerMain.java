package auctionfixture;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jason.infra.local.BaseLocalMAS;
import jason.asSemantics.Intention;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.SourceInfo;
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
        for(var entry:runner.getAgs().entrySet()) {
            var ts=entry.getValue().getTS();
            // Official Jason per-agent IA state, before managed Start releases reasoning.
            jason.stdlib.set_random_seed.getAgRandomIA(ts).setSeed(seed);
            int line=0;for(String text:lines){line++;if(text.isBlank()||text.startsWith("//"))continue;
                var plan=ASSyntax.parsePlan(text);plan.setSrcInfo(new SourceInfo(source.toString(),line));ts.getAg().getPL().add(plan);
            }
            var preview=new Random(seed);seeds.put(entry.getKey(),Map.of("seed",seed,"first",preview.nextDouble(),"second",preview.nextDouble()));
        }
        Files.write(fixture.resolve("ready.json"),CanonicalJson.encode(Map.of("profile","AUCTION_DETERMINISTIC_CONTROLS/1.0","producerPid",ProcessHandle.current().pid(),"seeds",seeds,
                "sourceTaskPolicy","canonical plans are untouched; supplemental control plans have no source-qualified Task markers")));
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
            architecture.getTS().getC().addAchvGoal(goal,Intention.EmptyInt);
            Files.write(fixture.resolve(String.format(Locale.ROOT,"injected-%03d.json",index)),CanonicalJson.encode(Map.of("producerPid",ProcessHandle.current().pid(),"agent",agent,"goal",goal.toString(),"meaning","request injected only; all outcome/state assertions must use TCP observations")));
        }
    }
    private static long failingSeed() {
        for(long candidate=0;candidate<1_000_000;candidate++) {var random=new Random(candidate);if(random.nextDouble()>=0.9&&random.nextDouble()>=0.9)return candidate;}
        throw new IllegalStateException("E02_SEED_NOT_FOUND");
    }
}
