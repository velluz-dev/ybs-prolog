package com.ybs.demo.reasoning;

import alice.tuprolog.InvalidTheoryException;
import alice.tuprolog.MalformedGoalException;
import alice.tuprolog.NoMoreSolutionException;
import alice.tuprolog.NoSolutionException;
import alice.tuprolog.Prolog;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Theory;
import alice.tuprolog.Var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Java-to-Prolog bridge. Deliberately thin: it loads the rule base, lets callers
 * assert facts, and solves goals. All the actual reasoning lives in
 * {@code src/main/resources/rules/ybs.pl}, which is where an examiner should be pointed.
 *
 * <p>Every goal is logged before it is solved, so the console becomes a visible trace of
 * the engine reasoning rather than a black box.
 */
@Service
public class PrologService {
    private static final Logger log = LoggerFactory.getLogger(PrologService.class);

    private final Prolog engine = new Prolog();
    private final String baseTheory;

    public PrologService(@Value("classpath:rules/ybs.pl") Resource ruleFile) throws IOException {
        this.baseTheory = new String(ruleFile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        resetToBaseTheory();
        log.info("Prolog rule base loaded from {} ({} chars)", ruleFile.getFilename(), baseTheory.length());
    }

    /**
     * Drops every asserted fact and reloads just the rules. Called before each planning
     * request so a stale fact from a previous query cannot leak into the next answer -
     * which would look exactly like the engine "remembering" a hardcoded result.
     */
    public synchronized void resetToBaseTheory() {
        try {
            engine.setTheory(new Theory(baseTheory));
        } catch (InvalidTheoryException e) {
            throw new IllegalStateException("ybs.pl failed to parse - check the rule base", e);
        }
    }

    /** Asserts one ground fact, e.g. {@code assertFact("pregnant(r1)")}. */
    public synchronized void assertFact(String fact) {
        try {
            engine.addTheory(new Theory(fact + "."));
        } catch (InvalidTheoryException e) {
            throw new IllegalArgumentException("Not a valid Prolog fact: " + fact, e);
        }
    }

    /**
     * tuProlog's parser rejects a goal that does not end in a period. During the demo the
     * goal is typed by hand into a URL, so we forgive the missing dot rather than fail.
     */
    private static String normaliseGoal(String goal) {
        String g = goal.trim();
        return g.endsWith(".") ? g : g + ".";
    }

    /** True if the goal has at least one solution. */
    public synchronized boolean prove(String rawGoal) {
        String goal = normaliseGoal(rawGoal);
        log.info("?- {}", goal);
        try {
            boolean proved = engine.solve(goal).isSuccess();
            log.info("   {}", proved ? "yes" : "no");
            return proved;
        } catch (MalformedGoalException e) {
            throw new IllegalArgumentException("Malformed goal: " + goal, e);
        }
    }

    /**
     * All solutions with their variable bindings. Backs {@code GET /api/prolog/query},
     * which is how the demo proves the answers are derived rather than looked up.
     */
    public synchronized List<Map<String, String>> solveAll(String rawGoal, int limit) {
        String goal = normaliseGoal(rawGoal);
        log.info("?- {}", goal);
        List<Map<String, String>> solutions = new ArrayList<>();
        try {
            SolveInfo info = engine.solve(goal);
            while (info.isSuccess() && solutions.size() < limit) {
                solutions.add(bindingsOf(info));
                if (!engine.hasOpenAlternatives()) {
                    break;
                }
                info = engine.solveNext();
            }
        } catch (MalformedGoalException e) {
            throw new IllegalArgumentException("Malformed goal: " + goal, e);
        } catch (NoMoreSolutionException e) {
            // Nothing left to enumerate; whatever was already collected is the answer.
        }
        log.info("   {} solution(s)", solutions.size());
        return solutions;
    }

    private Map<String, String> bindingsOf(SolveInfo info) {
        Map<String, String> bindings = new LinkedHashMap<>();
        try {
            for (Var v : info.getBindingVars()) {
                bindings.put(v.getName(), v.getTerm().toString());
            }
        } catch (NoSolutionException e) {
            // Goal was ground (no variables to report) - an empty binding map is correct.
        }
        return bindings;
    }
}
