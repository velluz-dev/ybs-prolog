package com.ybs.demo;

import com.ybs.demo.ops.OpsService;
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
    @Autowired com.ybs.demo.network.NetworkService network;
    @Autowired OpsService ops;
    @Autowired com.ybs.demo.reasoning.FactAssembler facts;

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
        // Half of the card tariff for an ordinary hop, 350, not half of the cash fare.
        assertEquals("175", prolog.solveAll("fare(r2, '43', F)", 1).get(0).get("F"));
    }

    @Test
    void studentWithoutCardPaysFullFare() {
        // The card matters: the half-fare clause needs both facts, so this falls through
        // to the cash tariff rather than silently granting the discount.
        prolog.assertFact("student(r3)");
        assertEquals("400", prolog.solveAll("fare(r3, '43', F)", 1).get(0).get("F"));
    }

    @Test
    void cardHolderPaysLessThanCashRider() {
        prolog.assertFact("verified_card(r10)");
        assertEquals("350", prolog.solveAll("fare(r10, '43', part, F)", 1).get(0).get("F"));
        assertEquals("400", prolog.solveAll("fare(r11, '43', part, F)", 1).get(0).get("F"));
    }

    @Test
    void ridingEndToEndCostsMoreThanAHopAlongTheSameRoute() {
        // The whole point of the span argument: same rider, same route, different price.
        prolog.assertFact("verified_card(r12)");
        assertEquals("350", prolog.solveAll("fare(r12, '43', part, F)", 1).get(0).get("F"));
        assertEquals("500", prolog.solveAll("fare(r12, '43', full, F)", 1).get(0).get("F"));

        assertEquals("400", prolog.solveAll("fare(r13, '43', part, F)", 1).get(0).get("F"));
        assertEquals("600", prolog.solveAll("fare(r13, '43', full, F)", 1).get(0).get("F"));
    }

    @Test
    void studentConcessionFollowsTheSpan() {
        // Half is taken off the fare the span actually earns, so an end-to-end student
        // ride is 250 and not 175. Getting this wrong would undercharge the long trips.
        prolog.assertFact("student(r14)");
        prolog.assertFact("verified_card(r14)");
        assertEquals("250", prolog.solveAll("fare(r14, '43', full, F)", 1).get(0).get("F"));
    }

    @Test
    void monkTravelsFreeEndToEndToo() {
        // The monk clause ignores the span, so no tariff row can outrank it.
        prolog.assertFact("monk(r15)");
        assertEquals("0", prolog.solveAll("fare(r15, '43', full, F)", 1).get(0).get("F"));
    }

    @Test
    void fareWithoutASpanMeansAnOrdinaryHop() {
        // fare/3 is the short form the live console types; it must agree with fare/4.
        prolog.assertFact("verified_card(r16)");
        assertEquals(prolog.solveAll("fare(r16, '43', part, F)", 1).get(0).get("F"),
                prolog.solveAll("fare(r16, '43', F)", 1).get(0).get("F"));
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

    @Test
    void opsDashboardDoesNotWipeWorldFacts() {
        // Regression for the bug where loading the ops dashboard reset the shared engine
        // and asserted only driver scores, wiping base_fare/2 - so a hand-typed console
        // goal like base_fare(...) silently failed afterwards. resetWorld() must restore it.
        // Take the route and its fare from the seeded network rather than hardcoding them:
        // the demo moved from 3 hand-built routes to the real 118-route export, where the
        // fare is the real flat 200 Ks, and a pinned 400 made this fail for the wrong reason.
        // Asked of base_fare/2 directly: since the tariff moved into fare_table/3, fare/3
        // answers from the rule base alone and would pass here even with the world wiped.
        var route = network.allRoutes().get(0);

        ops.drivers();
        List<Map<String, String>> solutions =
                prolog.solveAll("base_fare('" + route.getCode() + "', F)", 5);
        assertFalse(solutions.isEmpty(), "base_fare must survive an ops dashboard load");
        assertEquals(String.valueOf(route.getBaseFareKyats()), solutions.get(0).get("F"),
                "base_fare/2 should still report this route's posted fare");
    }

    @Test
    void aMissedBonusNamesTheRuleThatWithheldIt() {
        // The driver view shows the reason, not just the verdict, so every way of failing
        // bonus_eligible/2 must be reported by bonus_blocker/3 - otherwise a driver sees
        // "not eligible" with nothing under it.
        prolog.assertFact("headway_score(d20, '2026-08', 74)");
        prolog.assertFact("harsh_events(d20, '2026-08', 9)");
        prolog.assertFact("upheld_complaint(d20, '2026-08')");

        assertFalse(prolog.prove("bonus_eligible(d20, '2026-08')"));
        List<String> why = prolog.solveAll("bonus_blocker(d20, '2026-08', Why)", 10)
                .stream().map(m -> m.get("Why")).toList();
        assertTrue(why.contains("headway_below_target"), why.toString());
        assertTrue(why.contains("too_many_harsh_events"), why.toString());
        assertTrue(why.contains("upheld_complaint"), why.toString());
    }

    @Test
    void anEligibleDriverIsBlockedByNothing() {
        // The two directions have to agree: if bonus_eligible/2 holds, no blocker may fire.
        // A threshold edited in one rule and not the other shows up right here.
        prolog.assertFact("headway_score(d21, '2026-08', 91)");
        prolog.assertFact("harsh_events(d21, '2026-08', 2)");

        assertTrue(prolog.prove("bonus_eligible(d21, '2026-08')"));
        assertTrue(prolog.solveAll("bonus_blocker(d21, '2026-08', Why)", 10).isEmpty());
    }

    @Test
    void aDriverOnTheThresholdIsEligibleAndUnblocked() {
        // Exactly on both limits: headway 80 and 5 harsh events. The >= and =< in
        // bonus_eligible/2 and the < and > in bonus_blocker/3 have to be exact opposites,
        // and off-by-one here is the easiest way to strand a driver between the two rules.
        prolog.assertFact("headway_score(d22, '2026-08', 80)");
        prolog.assertFact("harsh_events(d22, '2026-08', 5)");

        assertTrue(prolog.prove("bonus_eligible(d22, '2026-08')"));
        assertTrue(prolog.solveAll("bonus_blocker(d22, '2026-08', Why)", 10).isEmpty());
    }

    /* ------------------------------------------------- the wider rule base --- */

    @Test
    void sumKyatsIsTheRecursionItLooksLike() {
        // tuProlog has no sum_list/2, so ybs.pl defines it. Base case first, and the
        // empty list must be 0 rather than failing - journey_fare/3 leans on that for a
        // journey with no ride legs at all.
        assertEquals("0", solve1("sum_kyats([], T)", "T"));
        assertEquals("1050", solve1("sum_kyats([350,350,350], T)", "T"));
        assertEquals("350", solve1("sum_kyats([350], T)", "T"));
    }

    @Test
    void journeyFareAddsUpEveryRideLeg() {
        prolog.assertFact("leg(j1, '43', part)");
        prolog.assertFact("leg(j1, '43', part)");
        prolog.assertFact("leg(j1, '99', full)");
        // Cash rider: 400 + 400 + 600.
        assertEquals("1400", solve1("journey_fare(j1, cash1, F)", "F"));

        prolog.assertFact("verified_card(card1)");
        // Card: 350 + 350 + 500.
        assertEquals("1200", solve1("journey_fare(j1, card1, F)", "F"));

        prolog.assertFact("monk(m9)");
        assertEquals("0", solve1("journey_fare(j1, m9, F)", "F"));
    }

    @Test
    void anAllWalkingJourneyCostsNothingWithoutASpecialCase() {
        // No leg/3 facts at all: findall/3 gives [] and sum_kyats/2 makes that 0. If this
        // ever fails instead of returning 0, the rule grew a case it does not need.
        prolog.assertFact("journey_stats(j7, 12, 0, 0)");
        assertEquals("0", solve1("journey_fare(j7, anyone, F)", "F"));
    }

    @Test
    void paretoDominanceMatchesWhatThePlannerMeansByIt() {
        // j1 is quicker AND cheaper than j2 with the same transfers, so it dominates.
        prolog.assertFact("journey_stats(j1, 40, 400, 1)");
        prolog.assertFact("journey_stats(j2, 50, 600, 1)");
        // j3 is quicker but dearer - a real trade-off, so neither dominates the other.
        prolog.assertFact("journey_stats(j3, 30, 900, 2)");

        assertTrue(prolog.prove("dominates(j1, j2)"));
        assertFalse(prolog.prove("dominates(j2, j1)"));
        assertFalse(prolog.prove("dominates(j1, j3)"));
        assertFalse(prolog.prove("dominates(j3, j1)"));

        assertTrue(prolog.prove("pareto_optimal(j1)"));
        assertFalse(prolog.prove("pareto_optimal(j2)"), "j2 is dominated by j1");
        assertTrue(prolog.prove("pareto_optimal(j3)"));
    }

    @Test
    void anEqualJourneyDoesNotDominateItsTwin() {
        // Dominance needs strictly better on at least one axis. Two identical options must
        // not knock each other out, or the Pareto set could come back empty.
        prolog.assertFact("journey_stats(a, 40, 400, 1)");
        prolog.assertFact("journey_stats(b, 40, 400, 1)");
        assertFalse(prolog.prove("dominates(a, b)"));
        assertTrue(prolog.prove("pareto_optimal(a)"));
        assertTrue(prolog.prove("pareto_optimal(b)"));
    }

    @Test
    void cheapestAndQuickestPickDifferentJourneys() {
        prolog.assertFact("journey_stats(fast, 30, 900, 2)");
        prolog.assertFact("journey_stats(cheap, 70, 200, 0)");
        assertEquals("cheap", solve1("cheapest(J)", "J"));
        assertEquals("fast", solve1("quickest(J)", "J"));
    }

    @Test
    void meetsReportsEachRouteOnceHoweverManyStopsTheyShare() {
        // shares_stop/2 succeeds once per shared stop; meets/2 exists to collapse that.
        // Getting this wrong floods the console with the same answer forty times.
        prolog.assertFact("serves('1', s10)");
        prolog.assertFact("serves('1', s11)");
        prolog.assertFact("serves('2', s10)");
        prolog.assertFact("serves('2', s11)");

        assertEquals(2, prolog.solveAll("shares_stop('1', R)", 10).size());
        assertEquals(1, prolog.solveAll("meets('1', R)", 10).size());
    }

    @Test
    void reachabilityTerminatesOnALoopAndRespectsItsDepthBound() {
        // 1 - 2 - 3 in a line, and 1 and 2 also share a second stop, so the graph has a
        // cycle. Without the visited list this would not terminate.
        prolog.assertFact("serves('1', s10)");
        prolog.assertFact("serves('2', s10)");
        prolog.assertFact("serves('2', s20)");
        prolog.assertFact("serves('3', s20)");

        assertTrue(prolog.prove("reachable('1', '2', 1)"), "one change");
        assertFalse(prolog.prove("reachable('1', '3', 1)"), "two changes needed");
        assertTrue(prolog.prove("reachable('1', '3', 2)"));
        assertTrue(prolog.prove("reachable('1', '1', 0)"), "already there");
    }

    @Test
    void reachableRoutesAreReportedOnceEach() {
        prolog.assertFact("serves('1', s10)");
        prolog.assertFact("serves('2', s10)");
        prolog.assertFact("serves('2', s20)");
        prolog.assertFact("serves('3', s20)");
        prolog.assertFact("serves('3', s10)");   // a second way to reach 3 from 1

        // Two paths to '3'; setof/3 in reachable_routes/3 must collapse them.
        List<Map<String, String>> found = prolog.solveAll("reachable('1', To, 2)", 20);
        List<String> tos = found.stream().map(m -> m.get("To")).toList();
        assertEquals(tos.size(), tos.stream().distinct().count(), "duplicates: " + tos);
    }

    @Test
    void transferPointNeedsTwoRoutes() {
        prolog.assertFact("serves('1', s10)");
        prolog.assertFact("serves('1', s11)");
        prolog.assertFact("serves('2', s11)");
        assertFalse(prolog.prove("transfer_point(s10)"), "one route is not a transfer");
        assertTrue(prolog.prove("transfer_point(s11)"));
    }

    @Test
    void concessionNamesExactlyOneFareClausePerRider() {
        prolog.assertFact("monk(r30)");
        prolog.assertFact("student(r31)");
        prolog.assertFact("verified_card(r31)");
        prolog.assertFact("verified_card(r32)");

        assertEquals(List.of("free_monk"), reasons("concession(r30, C)", "C"));
        assertEquals(List.of("half_student"), reasons("concession(r31, C)", "C"));
        assertEquals(List.of("card_holder"), reasons("concession(r32, C)", "C"));
        assertEquals(List.of("none"), reasons("concession(r33, C)", "C"));
    }

    @Test
    void countingWithAnUnboundVariableCountsEverything() {
        // The trap blocker_count/3 and near_miss/2 are written around. d40 has one blocker,
        // d41 has two; asking for d40's count must not report all three.
        prolog.assertFact("headway_score(d40, '2026-08', 70)");
        prolog.assertFact("harsh_events(d40, '2026-08', 0)");
        prolog.assertFact("headway_score(d41, '2026-08', 70)");
        prolog.assertFact("harsh_events(d41, '2026-08', 9)");

        assertEquals("1", solve1("count_solutions(bonus_blocker(d40, '2026-08', _), N)", "N"));
        assertEquals("2", solve1("count_solutions(bonus_blocker(d41, '2026-08', _), N)", "N"));
        assertEquals("3", solve1("count_solutions(bonus_blocker(_, '2026-08', _), N)", "N"));
    }

    @Test
    void nearMissIsTheDriverBlockedByExactlyOneThing() {
        prolog.assertFact("headway_score(d40, '2026-08', 70)");   // one blocker
        prolog.assertFact("harsh_events(d40, '2026-08', 0)");
        prolog.assertFact("headway_score(d41, '2026-08', 70)");   // two blockers
        prolog.assertFact("harsh_events(d41, '2026-08', 9)");
        prolog.assertFact("headway_score(d42, '2026-08', 95)");   // eligible
        prolog.assertFact("harsh_events(d42, '2026-08', 1)");

        List<String> near = reasons("near_miss(D, '2026-08')", "D");
        assertEquals(List.of("d40"), near);
    }

    @Test
    void blockerCountIsPerReasonNotPerDepot() {
        prolog.assertFact("headway_score(d40, '2026-08', 70)");
        prolog.assertFact("harsh_events(d40, '2026-08', 0)");
        prolog.assertFact("headway_score(d41, '2026-08', 70)");
        prolog.assertFact("harsh_events(d41, '2026-08', 9)");

        assertEquals("2", solve1("blocker_count('2026-08', headway_below_target, N)", "N"));
        assertEquals("1", solve1("blocker_count('2026-08', too_many_harsh_events, N)", "N"));
    }

    @Test
    void topHeadwayReportsEveryDriverTiedForBest() {
        prolog.assertFact("headway_score(d50, '2026-08', 91)");
        prolog.assertFact("headway_score(d51, '2026-08', 91)");
        prolog.assertFact("headway_score(d52, '2026-08', 70)");
        assertEquals(List.of("d50", "d51"), reasons("top_headway('2026-08', D)", "D"));
    }

    /* ----------------------------------------------------- KB self-checks --- */

    @Test
    void theRuleBaseDoesNotContradictItself() {
        // These are the goals ybs.pl section 9 exists for. Every one must have NO solution
        // against the seeded world; a solution means two rules written as opposites have
        // drifted apart, which is the failure mode a rule base has and a function does not.
        facts.resetWorld();
        assertFalse(prolog.prove("contradictory_bonus(D, M)"),
                "a driver cannot be both awarded and blocked");
        assertFalse(prolog.prove("unexplained_bonus_refusal(D, M)"),
                "a refused bonus must name a blocker");
        assertFalse(prolog.prove("missing_tariff(S, Sp)"),
                "every card/span combination needs a price");
    }

    @Test
    void aJourneyRejectedByTheConstraintsAlwaysSaysWhy() {
        prolog.assertFact("journey_stats(j1, 40, 400, 3)");
        prolog.assertFact("transfer_count(j1, 3)");
        prolog.assertFact("predicted_crowding(j1, full)");
        prolog.assertFact("pregnant(r60)");
        prolog.assertFact("needs_seat(r60)");

        assertFalse(prolog.prove("suitable(j1, r60)"));
        assertFalse(prolog.prove("unexplained_rejection(j1, r60)"),
                "violation_reason/3 must cover every condition violates/2 checks");
        assertFalse(prolog.prove("spurious_rejection(j1, r60)"));
    }

    @Test
    void aSuitableJourneyHasNoReasonAgainstIt() {
        prolog.assertFact("journey_stats(j2, 40, 400, 0)");
        prolog.assertFact("transfer_count(j2, 0)");
        prolog.assertFact("predicted_crowding(j2, free)");

        assertTrue(prolog.prove("suitable(j2, r61)"));
        assertFalse(prolog.prove("spurious_rejection(j2, r61)"));
        assertFalse(prolog.prove("unexplained_rejection(j2, r61)"));
    }

    /* ------------------------------------------------------------ helpers --- */

    /** First solution's binding for one variable, or null. */
    private String solve1(String goal, String var) {
        List<Map<String, String>> sols = prolog.solveAll(goal, 1);
        return sols.isEmpty() ? null : sols.get(0).get(var);
    }

    /** Every solution's binding for one variable, in order. */
    private List<String> reasons(String goal, String var) {
        return prolog.solveAll(goal, 20).stream().map(m -> m.get(var)).toList();
    }
}
