package com.ybs.demo.reasoning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prolog")
public class PrologController {

    private final PrologService prologService;

    public PrologController(PrologService prologService) {
        this.prologService = prologService;
    }

    /**
     * Live Prolog console. Shown during the demo with goals typed by hand, so the
     * examiner can confirm the rule base is really being consulted.
     * Example: {@code /api/prolog/query?goal=fare(monk1,'43',F)}
     */
    @GetMapping("/query")
    public Map<String, Object> query(@RequestParam String goal) {
        try {
            List<Map<String, String>> solutions = prologService.solveAll(goal, 25);
            return Map.of(
                    "goal", goal,
                    "proved", !solutions.isEmpty(),
                    "solutions", solutions);
        } catch (IllegalArgumentException e) {
            // A typo in a hand-typed goal should read as a syntax error, not a crash.
            return Map.of("goal", goal, "proved", false, "error", "syntax error in goal");
        }
    }
}
