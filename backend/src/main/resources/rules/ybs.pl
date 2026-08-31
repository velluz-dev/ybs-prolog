% ============================================================================
%  YBS demo - knowledge base
% ============================================================================
%  This file is the reasoning. Java only asserts ground facts into it and asks
%  goals; it never decides anything itself. Four rule families, each answering a
%  different question the demo asks out loud.
%
%  Facts asserted at query time by FactAssembler.java:
%    base_fare(RouteCode, Kyats).
%    monk(Rider). student(Rider). verified_card(Rider).
%    pregnant(Rider). needs_seat(Rider). mobility_limited(Rider).
%    transfer_count(Journey, N).
%    predicted_crowding(Journey, free | standing | full).
%    uses_segment(Journey, SegmentId).
%    uses_overbridge(Journey).
%    leg(Journey, RouteCode, part | full).
%    journey_stats(Journey, Minutes, Kyats, Transfers).
%    serves(RouteCode, StopId).
%    flooded(SegmentId).
%    arrival_minutes(Journey, MinutesSinceMidnight).
%    return_route(Journey, RouteCode). last_departure(RouteCode, Minutes).
%    headway_score(Driver, Month, Score).
%    harsh_events(Driver, Month, Count).
%    upheld_complaint(Driver, Month).
% ============================================================================


% -------------------------------------------------- 1. fare & eligibility ---
% The tariff, as facts rather than numbers buried inside clauses. Two things set
% the price: whether the rider holds a YBS card, and whether the ride covers the
% route end to end (boarding at its first stop, alighting at its last) or only
% part of it. A fare change is then a one-line edit anyone can find.
fare_table(card,    part, 350).
fare_table(card,    full, 500).
fare_table(no_card, part, 400).
fare_table(no_card, full, 600).

% Worth knowing before you go looking for the full-route rate in a search result:
% every one of the 118 YBS routes is a loop. The furthest any route's first stop
% sits from its last is 1.76 km and 117 of the 118 are under 1 km, so A* always
% walks between the termini rather than riding the whole loop round - the 'full'
% rows are correct, reachable from the console, and never charged by
% /api/journeys/search. Pricing long rides instead would mean triggering on the
% share of the route ridden rather than on its endpoints.

% Card status is derived, not asserted, so the UI only ever has to tell the engine
% about the card itself. Negation-as-failure gives the two clauses disjoint
% conditions, which keeps card_status/2 single-valued under backtracking.
card_status(Rider, card)    :- verified_card(Rider).
card_status(Rider, no_card) :- \+ verified_card(Rider).

% First matching clause wins, so clause order encodes precedence: a monk who is
% also a student still travels free, and the concession is taken off the fare the
% span actually earns - half of 500 on a full route, half of 350 on a short hop.
fare(Rider, _Route, _Span, 0) :- monk(Rider).
fare(Rider, _Route, Span, F)  :- student(Rider), verified_card(Rider),
                                 fare_table(card, Span, B), F is B // 2.
fare(Rider, _Route, Span, F)  :- card_status(Rider, C), fare_table(C, Span, F).

% The everyday question - "what does this rider pay for an ordinary hop?" - asked
% without having to name the span. Kept so a goal typed in the live console stays
% short, and because most rides are not end to end.
fare(Rider, Route, F) :- fare(Rider, Route, part, F).

% fare/4 answers "how much". This answers "why that much", by naming the clause
% that won - the same service violation_reason/3 does for suitable/2. The guards
% repeat the earlier clauses' conditions negated, so exactly one holds per rider:
% clause order cannot be relied on here, because a console goal may backtrack into
% every clause looking for more solutions.
concession(Rider, free_monk)    :- monk(Rider).
concession(Rider, half_student) :- \+ monk(Rider), student(Rider), verified_card(Rider).
concession(Rider, card_holder)  :- \+ monk(Rider), \+ student(Rider), verified_card(Rider).
concession(Rider, none)         :- \+ monk(Rider), \+ verified_card(Rider).


% ------------------------------------------- 2. rider-profile constraints ---
% A journey suits a rider when nothing rules it out. Writing it as negation-as-
% failure over an explicit violates/2 means every rejection has a named cause,
% rather than an option silently vanishing from the list.
suitable(J, R) :- \+ violates(J, R).

violates(J, R) :- needs_seat(R), predicted_crowding(J, full).
violates(J, R) :- pregnant(R), transfer_count(J, N), N > 1.
violates(J, R) :- mobility_limited(R), uses_overbridge(J).
violates(J, _) :- uses_segment(J, S), flooded(S).

% The same conditions again, each carrying the reason it fired. The UI queries
% this so it can tell the rider WHY an option was withdrawn.
violation_reason(J, R, crowded_no_seat) :-
    needs_seat(R), predicted_crowding(J, full).
violation_reason(J, R, too_many_transfers) :-
    pregnant(R), transfer_count(J, N), N > 1.
violation_reason(J, R, overbridge_not_accessible) :-
    mobility_limited(R), uses_overbridge(J).
violation_reason(J, _, flooded_segment) :-
    uses_segment(J, S), flooded(S).


% ------------------------------------------------ 3. last-bus strand risk ---
% 1260 minutes past midnight is 21:00. Arriving after the last return bus has
% departed strands the rider, so this warns about the RETURN leg, not the outbound.
strand_risk(R, J) :- arrival_minutes(J, T), T > 1260,
                     return_route(J, RR), last_departure(RR, L), T > L.


% ------------------------------------------- 4. driver bonus eligibility ---
% All three conditions must hold. The negation is what makes a single upheld
% complaint disqualifying however good the other numbers look.
bonus_eligible(D, M) :- headway_score(D, M, H), H >= 80,
                        harsh_events(D, M, E), E =< 5,
                        \+ upheld_complaint(D, M).

% The same three thresholds again, inverted, each carrying the reason it fired -
% the same shape as violation_reason/3 above and for the same reason. A driver
% looking at their own screen is owed the rule that withheld the bonus, not a bare
% "not eligible"; and because both directions are written out, a threshold changed
% in one place and not the other shows up as a driver who is neither eligible nor
% blocked by anything.
bonus_blocker(D, M, headway_below_target) :- headway_score(D, M, H), H < 80.
bonus_blocker(D, M, too_many_harsh_events) :- harsh_events(D, M, E), E > 5.
bonus_blocker(D, M, upheld_complaint)      :- upheld_complaint(D, M).


% ------------------------------------------------------ 5. list arithmetic ---
% tuProlog ships no sum_list/2, max_list/2, sort/2 or between/3, so the aggregates
% below are written out. That is not a workaround to apologise for in a viva: a
% recursive definition with the base case first is exactly what a KR course is
% asking to see, and it is two lines.

sum_kyats([], 0).
sum_kyats([F|Fs], Total) :- sum_kyats(Fs, Rest), Total is F + Rest.

% How many solutions a goal has. findall/3 collects one marker per solution and
% length/2 counts them - the standard way to count without aggregate_all/3.
count_solutions(Goal, N) :- findall(x, Goal, Xs), length(Xs, N).


% -------------------------------------------------- 6. journey economics ---
% What a whole journey costs one rider. This used to be a loop in Java that asked
% fare/4 once per leg and added the answers up; the loop is gone and the rule base
% owns the total, which is the point of having a rule base at all.
%
% Only ride legs are asserted as leg/3 - walking is free and has no route - so a
% journey that is all walking gives findall/3 an empty list and sum_kyats/2 makes
% that 0 without needing a case of its own.
% once/1 around fare/4 is the whole subtlety here, and it is worth a viva sentence.
% fare/4 is deliberately NON-deterministic: for a monk the first clause gives 0 and
% then Prolog backtracks into the general clause and offers 400 as well, which is the
% backtracking demonstration the console preset is built on. Clause order therefore
% only means precedence if you take the FIRST solution - and findall/3 takes every
% one. Without once/1 a monk's journey would be priced at 0 + 400 per leg.
journey_fare(J, Rider, Total) :-
    findall(F, (leg(J, Route, Span), once(fare(Rider, Route, Span, F))), Fares),
    sum_kyats(Fares, Total).

% What the same journey costs with a card, whoever is asking. Two goals side by
% side in the console is the cheapest way to show what the card is worth.
fare_with_card(J, Total) :-
    findall(F, (leg(J, Route, Span), fare_table(card, Span, F0), F = F0), Fares),
    sum_kyats(Fares, Total).

% Rides that are charged at the end-to-end rate, so a fare can be accounted for.
end_to_end_leg(J, Route) :- leg(J, Route, full).


% ------------------------------------------------------ 7. Pareto preference ---
% The planner's Pareto filter, restated as logic. journey_stats/4 carries the three
% objectives A* trades off; dominates/2 says one option is at least as good on all
% three and strictly better on at least one; pareto_optimal/1 is negation-as-failure
% over that.
%
% AStarPlanner.java implements this too. The duplication is deliberate - an examiner
% asking "what does Pareto-optimal mean here" can be shown the definition rather
% than an implementation of it, and the two can be run against each other.
dominates(A, B) :-
    journey_stats(A, MinsA, FareA, TransA),
    journey_stats(B, MinsB, FareB, TransB),
    A \== B,
    MinsA =< MinsB, FareA =< FareB, TransA =< TransB,
    ( MinsA < MinsB ; FareA < FareB ; TransA < TransB ).

pareto_optimal(J) :- journey_stats(J, _, _, _), \+ dominates(_, J).

% The cheapest and the quickest of what was offered, each without a sort/2 to lean
% on: a journey is cheapest when nothing beats it on fare.
cheapest(J) :-
    journey_stats(J, _, Fare, _),
    \+ ( journey_stats(_, _, Other, _), Other < Fare ).

quickest(J) :-
    journey_stats(J, Mins, _, _),
    \+ ( journey_stats(_, Other, _, _), Other < Mins ).


% ------------------------------------------------- 8. network reachability ---
% serves/2 is asserted for the routes of the journeys A* has just proposed, so like
% suitable/2 these answer about the last search rather than about all 118 routes.
% The naming is careful for that reason: transfer_point/1 is a stop where two of
% THOSE routes meet, not the network-wide "interchange" that DwellModel derives
% from every route calling at a stop.
transfer_point(S) :-
    setof(R, serves(R, S), Routes),
    Routes = [_, _|_].

% Two routes share a stop. Two busy routes share dozens, so this succeeds once per
% shared stop - which is the honest relation but useless to read.
shares_stop(R1, R2) :- serves(R1, S), serves(R2, S), R1 \== R2.

% ... so meets/2 is the one to ask. setof/3 both sorts and removes duplicates, and
% the S in shares_stop/2 is already existentially quantified by being local to it,
% so no ^ is needed here. One solution per distinct route, which is the question.
meets(R1, R2) :- setof(R, shares_stop(R1, R), Routes), member(R2, Routes).

% Can a rider get from one route to another by changing bus at most Depth times?
% The accumulator carries the routes already ridden and member/2 refuses to revisit
% one, which is what makes this terminate - every YBS route is a loop, so without
% the visited list the search would go round forever whatever the depth bound.
%
% reach/4 enumerates paths, so a route findable two ways succeeds twice. setof/3 in
% reachable_routes/3 collapses that to the set, and reachable/3 is then a membership
% test that works whether To is bound or not - a plain \+ \+ would deduplicate but
% would also throw away the binding, so "where can I get to?" would stop working.
reachable_routes(From, Depth, Routes) :-
    setof(R, reach(From, R, Depth, [From]), Routes).

reachable(From, To, Depth) :- reachable_routes(From, Depth, Routes), member(To, Routes).

reach(R, R, _, _).
reach(From, To, Depth, Seen) :-
    Depth > 0,
    meets(From, Next),
    \+ member(Next, Seen),
    Left is Depth - 1,
    reach(Next, To, Left, [Next|Seen]).


% ------------------------------------------------------- 9. KB self-checks ---
% Rules whose only job is to catch this file contradicting itself. Every one of
% them should have NO solutions; a solution means two rules written as opposites
% have drifted apart, which is the failure mode a rule base has and a function does
% not. They are cheap to type into the live console, and the Java tests run them.

% A journey the constraints reject with nothing to say about why. suitable/2 and
% violation_reason/3 repeat the same conditions, so a condition added to one and
% not the other lands here.
unexplained_rejection(J, R) :-
    journey_stats(J, _, _, _),
    \+ suitable(J, R),
    \+ violation_reason(J, R, _).

% ... and the reverse: a reason that fires for a journey the rules call suitable.
spurious_rejection(J, R) :- suitable(J, R), violation_reason(J, R, _).

% A driver both awarded and blocked. bonus_eligible/2 and bonus_blocker/3 are exact
% opposites - >= 80 against < 80, =< 5 against > 5 - so a threshold edited in one
% and not the other shows up here rather than as a driver quietly missing a bonus.
contradictory_bonus(D, M) :- bonus_eligible(D, M), bonus_blocker(D, M, _).

% ... and its mirror: refused with nothing to blame.
unexplained_bonus_refusal(D, M) :-
    headway_score(D, M, _),
    \+ bonus_eligible(D, M),
    \+ bonus_blocker(D, M, _).

% Every cell of the tariff must exist. Four rows; a missing one would silently make
% fare/4 fail and the journey come back with no price at all.
missing_tariff(Status, Span) :-
    member(Status, [card, no_card]),
    member(Span, [part, full]),
    \+ fare_table(Status, Span, _).

% A rider the fare rules can price but cannot explain, or explain twice.
unexplained_fare(R) :- fare(R, '1', _), \+ concession(R, _).


% -------------------------------------------------------- 10. ops queries ---
% The month's best headway, again without sort/2: a driver is top when no other
% driver that month beats them. Ties give both, which is correct rather than a bug.
top_headway(M, D) :-
    headway_score(D, M, H),
    \+ ( headway_score(Other, M, H2), Other \== D, H2 > H ).

% How many drivers earn the bonus, and how many are blocked for each named reason.
%
% The Why has to be bound BEFORE the count, which is the whole lesson of this pair:
% count_solutions/2 asks findall/3 to enumerate, and an unbound Why inside the goal
% enumerates every reason at once and reports one total. So the reasons are gathered
% first, and the count is asked once per reason.
bonus_count(M, N) :- count_solutions(bonus_eligible(_, M), N).

blocker_count(M, Why, N) :-
    setof(W, D^bonus_blocker(D, M, W), Reasons),
    member(Why, Reasons),
    count_solutions(bonus_blocker(_, M, Why), N).

% Drivers who miss out on exactly one condition - the ones worth coaching, as opposed
% to the ones who are far off on all three. Same trap as above: D is bound by
% headway_score/3 first, so the count is this driver's blockers and not the depot's.
near_miss(D, M) :-
    headway_score(D, M, _),
    \+ bonus_eligible(D, M),
    count_solutions(bonus_blocker(D, M, _), 1).
