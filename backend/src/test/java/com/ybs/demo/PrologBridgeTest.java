package com.ybs.demo;

import com.ybs.demo.reasoning.PrologService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the rule base actually decides things, and that the Java bridge reports
 * what it decided. If these pass, the demo's claim that nothing is hardcoded holds.
 */
@SpringBootTest
class PrologBridgeTest {

    @Autowired PrologService prolog;

    @BeforeEach
    void freshWorld() {
        prolog.resetToBaseTheory();
        prolog.assertFact("base_fare('43', 400)");
    }

    @Test
    void monkTravelsFree() {
        prolog.assertFact("monk(r1)");
        List<Map<String, String>> solutions = prolog.solveAll("fare(r1, '43', F)", 1);
        assertEquals("0", solutions.get(0).get("F"));
    }

    @Test
    void verifiedStudentPaysHalfFare() {
        prolog.assertFact("student(r2)");
        prolog.assertFact("verified_card(r2)");
        assertEquals("200", prolog.solveAll("fare(r2, '43', F)", 1).get(0).get("F"));
    }

    @Test
    void studentWithoutCardPaysFullFare() {
        // The card matters: the half-fare clause needs both facts, so this falls through
        // to the base fare rather than silently granting the discount.
        prolog.assertFact("student(r3)");
        assertEquals("400", prolog.solveAll("fare(r3, '43', F)", 1).get(0).get("F"));
    }

    @Test
    void pregnantRiderRejectsJourneyWithTwoTransfers() {
        prolog.assertFact("pregnant(r4)");
        prolog.assertFact("transfer_count(j1, 2)");
        prolog.assertFact("predicted_crowding(j1, free)");

        assertFalse(prolog.prove("suitable(j1, r4)"));
        assertEquals("too_many_transfers",
                prolog.solveAll("violation_reason(j1, r4, Why)", 1).get(0).get("Why"));
    }

    @Test
    void pregnantRiderAcceptsSingleTransferJourney() {
        prolog.assertFact("pregnant(r5)");
        prolog.assertFact("transfer_count(j2, 1)");
        prolog.assertFact("predicted_crowding(j2, free)");

        assertTrue(prolog.prove("suitable(j2, r5)"));
    }

    @Test
    void seatedRiderRejectsFullBus() {
        prolog.assertFact("needs_seat(r6)");
        prolog.assertFact("transfer_count(j3, 0)");
        prolog.assertFact("predicted_crowding(j3, full)");

        assertFalse(prolog.prove("suitable(j3, r6)"));
    }

    @Test
    void floodedSegmentRulesOutAnyRider() {
        prolog.assertFact("transfer_count(j4, 0)");
        prolog.assertFact("predicted_crowding(j4, free)");
        prolog.assertFact("uses_segment(j4, s15)");
        prolog.assertFact("flooded(s15)");

        assertFalse(prolog.prove("suitable(j4, anyone)"));
    }

    @Test
    void bonusNeedsAllThreeConditions() {
        prolog.assertFact("headway_score(d1, '2026-08', 91)");
        prolog.assertFact("harsh_events(d1, '2026-08', 2)");
        assertTrue(prolog.prove("bonus_eligible(d1, '2026-08')"));

        // One upheld complaint disqualifies regardless of the other numbers.
        prolog.assertFact("headway_score(d2, '2026-08', 99)");
        prolog.assertFact("harsh_events(d2, '2026-08', 0)");
        prolog.assertFact("upheld_complaint(d2, '2026-08')");
        assertFalse(prolog.prove("bonus_eligible(d2, '2026-08')"));
    }

    @Test
    void goalWithoutTrailingPeriodStillParses() {
        // The live demo types goals by hand into a URL; a missing dot must not 500.
        prolog.assertFact("monk(r9)");
        assertTrue(prolog.prove("fare(r9, '43', F)"));
    }
}
