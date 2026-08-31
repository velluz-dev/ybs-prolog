package com.ybs.demo.ops;

import com.ybs.demo.network.Route;
import com.ybs.demo.reasoning.FactAssembler;
import com.ybs.demo.reasoning.PrologService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Backs the ops dashboard.
 *
 * <p>The bonus badge is the only reason this page exists: it is computed by asking the
 * Prolog engine, per driver, at request time. The seeded {@code bonus_eligible} column is
 * deliberately false for everyone, so if the rule engine were dead every badge would read
 * "not eligible" and the failure would be visible rather than hidden behind stale data.
 */
@Service
public class OpsService {

    private final DriverMonthlyScoreRepository scoreRepository;
    private final IncidentRepository incidentRepository;
    private final DriverRepository driverRepository;
    private final PrologService prolog;
    private final FactAssembler facts;

    public OpsService(DriverMonthlyScoreRepository scoreRepository,
                      IncidentRepository incidentRepository,
                      DriverRepository driverRepository,
                      PrologService prolog,
                      FactAssembler facts) {
        this.scoreRepository = scoreRepository;
        this.incidentRepository = incidentRepository;
        this.driverRepository = driverRepository;
        this.prolog = prolog;
        this.facts = facts;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> drivers() {
        // resetWorld() reloads the rules and re-asserts every standing fact, and
        // assertWorldFacts now includes the driver scores - so asserting them again
        // here would put a duplicate copy of each headway_score/3 in the engine.
        facts.resetWorld();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (DriverMonthlyScore s : scoreRepository.findAll()) {
            String month = s.getId().getMonthKey();
            // The goal is logged by PrologService, so the console shows one query per row.
            boolean eligible = prolog.prove("bonus_eligible(d" + s.getDriver().getId() + ", '" + month + "')");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("driverId", s.getDriver().getId());
            row.put("driverName", s.getDriver().getName());
            row.put("month", month);
            row.put("headwayScore", s.getHeadwayScore());
            row.put("harshEvents", s.getHarshEvents());
            row.put("upheldComplaints", s.getUpheldComplaints());
            row.put("bonusEligible", eligible);
            rows.add(row);
        }
        return rows;
    }

    /**
     * The one seeded bunching alert. Bunching is when two buses on a route close up and
     * run nose to tail, so one is packed and the next is empty; the fix is to hold the
     * follower. Buses 12 and 15 are seeded close together to make this real on the map.
     */
    public List<Map<String, Object>> bunchingAlerts() {
        return List.of(Map.of(
                "routeCode", "43",
                "busA", 12,
                "busB", 15,
                "headwaySeconds", 90,
                "action", "hold bus 15",
                "messageMy", "လမ်း ၄၃ — ကား ၁၂ နှင့် ၁၅ ၉၀ စက္ကန့်သာ ကွာသည်။ ကား ၁၅ ကို ရပ်ထားပါ။",
                "messageEn", "Route 43 — buses 12 and 15 are 90s apart, hold bus 15"));
    }

    /** Incident counts by route and hour of day. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> incidentCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Incident i : incidentRepository.findAll()) {
            String key = i.getRouteId() + "|" + i.getTimestamp().getHour() + "|" + i.getType();
            counts.merge(key, 1, Integer::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((key, count) -> {
            String[] parts = key.split("\\|");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("routeId", Long.valueOf(parts[0]));
            row.put("hour", Integer.valueOf(parts[1]));
            row.put("type", parts[2]);
            row.put("count", count);
            rows.add(row);
        });
        return rows;
    }

    /** Every driver, for the role switcher's "who am I" picker. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> driverList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Driver d : driverRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("driverId", d.getId());
            row.put("driverName", d.getName());
            row.put("routeCode", d.getRoute() == null ? null : d.getRoute().getCode());
            rows.add(row);
        }
        return rows;
    }

    /**
     * One driver's own shift, for the driver view.
     *
     * <p>The supervisor's dashboard answers "which of my drivers earn the bonus". A driver
     * asking about their own month wants the opposite: not the comparison, but the reason.
     * So this asks the engine twice - {@code bonus_eligible/2} for the verdict and
     * {@code bonus_blocker/3} for what withheld it - and a driver who missed out is told
     * which rule fired rather than being left to infer it from three numbers.
     *
     * <p>Live buses are deliberately not included. The page already polls
     * {@code /api/buses/live} at 1 Hz for the map and filters it by route code, so
     * duplicating that shaping here would give the screen two clocks to disagree with.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> driverShift(Long driverId) {
        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null) {
            return Map.of();
        }
        facts.resetWorld();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("driverId", driver.getId());
        out.put("driverName", driver.getName());
        Route route = driver.getRoute();
        out.put("routeId", route == null ? null : route.getId());
        out.put("routeCode", route == null ? null : route.getCode());
        out.put("routeNameMy", route == null ? null : route.getNameMy());
        // Colour is not on the entity - it is assigned per route by /api/network, which the
        // page has already loaded. Looking it up there keeps one source for it.

        List<Map<String, Object>> months = new ArrayList<>();
        for (DriverMonthlyScore s : scoreRepository.findAll()) {
            if (!s.getDriver().getId().equals(driverId)) continue;
            String month = s.getId().getMonthKey();
            String d = "d" + driverId;
            String m = "'" + month + "'";

            List<String> blockers = new ArrayList<>();
            for (Map<String, String> sol :
                    prolog.solveAll("bonus_blocker(" + d + ", " + m + ", Why)", 10)) {
                String why = sol.get("Why");
                if (why != null && !blockers.contains(why)) {
                    blockers.add(why);
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", month);
            row.put("headwayScore", s.getHeadwayScore());
            row.put("harshEvents", s.getHarshEvents());
            row.put("upheldComplaints", s.getUpheldComplaints());
            row.put("bonusEligible", prolog.prove("bonus_eligible(" + d + ", " + m + ")"));
            row.put("blockers", blockers);
            months.add(row);
        }
        out.put("months", months);

        List<Map<String, Object>> incidents = new ArrayList<>();
        for (Incident i : incidentRepository.findAll()) {
            if (route == null || !route.getId().equals(i.getRouteId())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", i.getType().name());
            row.put("hour", i.getTimestamp().getHour());
            row.put("stopNameMy", i.getStop() == null ? null : i.getStop().getNameMy());
            row.put("stopNameEn", i.getStop() == null ? null : i.getStop().getNameEn());
            incidents.add(row);
        }
        out.put("incidents", incidents);

        // The bunching alert is per route, so a driver only sees it when it is theirs.
        out.put("bunching", bunchingAlerts().stream()
                .filter(a -> route != null && route.getCode().equals(a.get("routeCode")))
                .toList());
        return out;
    }
}
