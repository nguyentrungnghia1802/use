package verification_oracle;
import java.nio.file.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
public class mark extends DefaultInternalAction {
    @Override public Object execute(TransitionSystem ts, Unifier u, Term[] args) throws Exception {
        String tag=((StringTerm)args[0]).getString();
        String row=tag+"\t"+ts.getAgArch().getAgName()+"\t"+System.currentTimeMillis()+"\n";
        Files.writeString(Path.of("verification-next-plan.txt"),row,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        System.out.println("VERIFICATION_NEXT_PLAN "+row.trim());
        return true;
    }
}
