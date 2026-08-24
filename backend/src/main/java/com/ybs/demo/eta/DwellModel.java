package com.ybs.demo.eta;

import com.ybs.demo.network.NetworkService;
import org.springframework.stereotype.Service;

/**
 * How long a bus stands still at a stop.
 *
 * <p>Before this existed, dwell was faked: the simulator multiplied <i>driving</i> time by
 * 1.0-1.25 depending on how full the bus was, and no bus ever actually stopped. That is
 * cheap but it cannot answer "when does my bus get here?", because the time a rider spends
 * waiting is exactly the time the model had smeared away.
 *
 * <p>Three things make a bus sit longer:
 * <ul>
 *   <li><b>Crowding</b> - a full bus loads through one door, so everyone queues.
 *   <li><b>Interchange</b> - stops where routes meet see people changing bus, not just
 *       arriving, so both doors are busy for longer.
 *   <li><b>Terminus</b> - the driver takes a break before running the route again.
 * </ul>
 *
 * <p><b>Why these constants live in code and the traffic prior lives in data.sql:</b> the
 * traffic prior is a per-segment x per-bucket matrix that genuinely varies street by street
 * and hour by hour, so it belongs in a table someone can edit. These are four structural
 * numbers about how buses work, not about Yangon geography. They are still hand-chosen
 * assumptions with no measurement behind them, and should be described that way.
 *
 * <p>This class sits in {@code eta} rather than {@code simulation} because both
 * {@code simulation} and {@code planning} need it, and {@code eta} already sits below both.
 * Putting it in {@code simulation} would force {@code planning -> simulation} and create a
 * package cycle.
 */
@Service
public class DwellModel {

    /** Doors open, a few people on and off, doors close. */
    public static final int BASE_SECONDS = 20;

    /** Extra seconds when the bus is standing-room-only, and when it is full. */
    public static final int CROWDING_STANDING_SECONDS = 8;
    public static final int CROWDING_FULL_SECONDS = 20;

    /** Extra seconds at a stop where more than one route calls. */
    public static final int INTERCHANGE_SECONDS = 25;

    /** Driver break at the end of the route, before it loops and runs again. */
    public static final int LAYOVER_SECONDS = 180;

    /**
     * What the planner assumes when it cannot know which bus the rider will board.
     * Standing room is the honest middle: assuming empty would flatter every estimate,
     * assuming full would make the planner pessimistic about every journey in the city.
     */
    public static final int ASSUMED_CROWDING_FOR_PLANNING = 1;

    private final NetworkService network;

    public DwellModel(NetworkService network) {
        this.network = network;
    }

    /**
     * Seconds the bus holds this stop.
     *
     * @param crowdingLevel 0 = seats free, 1 = standing room, 2 = full
     */
    public int dwellSeconds(Long stopId, int crowdingLevel, boolean isTerminus) {
        if (isTerminus) {
            return LAYOVER_SECONDS;
        }
        int seconds = BASE_SECONDS + crowdingExtra(crowdingLevel);
        if (network.routeCountAt(stopId) > 1) {
            seconds += INTERCHANGE_SECONDS;
        }
        return seconds;
    }

    /** Same figure in minutes, for the planner's edge costs. */
    public double dwellMinutes(Long stopId, int crowdingLevel, boolean isTerminus) {
        return dwellSeconds(stopId, crowdingLevel, isTerminus) / 60.0;
    }

    /** Planner-side dwell: crowding is unknown at plan time, so it is assumed. */
    public double plannedDwellMinutes(Long stopId) {
        return dwellMinutes(stopId, ASSUMED_CROWDING_FOR_PLANNING, false);
    }

    private static int crowdingExtra(int crowdingLevel) {
        return switch (crowdingLevel) {
            case 0 -> 0;
            case 1 -> CROWDING_STANDING_SECONDS;
            default -> CROWDING_FULL_SECONDS;
        };
    }
}
