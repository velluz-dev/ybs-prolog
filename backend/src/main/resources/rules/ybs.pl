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
%    flooded(SegmentId).
%    arrival_minutes(Journey, MinutesSinceMidnight).
%    return_route(Journey, RouteCode). last_departure(RouteCode, Minutes).
%    headway_score(Driver, Month, Score).
%    harsh_events(Driver, Month, Count).
%    upheld_complaint(Driver, Month).
% ============================================================================


% -------------------------------------------------- 1. fare & eligibility ---
% First matching clause wins, so clause order encodes precedence: a monk who is
% also a student still travels free.
fare(Rider, _Route, 0) :- monk(Rider).
fare(Rider, Route, F)  :- student(Rider), verified_card(Rider),
                          base_fare(Route, B), F is B // 2.
fare(_Rider, Route, F) :- base_fare(Route, F).


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
