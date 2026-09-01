package com.ybs.demo.eta;

import com.ybs.demo.planning.AStarPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Travel-time estimation under uncertainty.
 *
 * <p>The model is deliberately simple and deliberately honest:
 * <pre>segmentMinutes = baseMinutes x trafficFactor(segment, timeOfDayBucket)</pre>
 *
 * <p>The traffic factors are a <b>hand-collected prior</b> - authored from local
 * knowledge of which Yangon corridors clog and when - <b>not a learned model</b>. There is
 * no training data behind them and no claim that there is. What makes this a reasoning
 * -under-uncertainty component is the second half: the answer is returned as an interval,
 * not a point. A rider is told "18 to 30 minutes", because a single number would imply a
 * confidence the model does not have.
 */
@Service
public class EtaService {
    private static final Logger log = LoggerFactory.getLogger(EtaService.class);

    /**
     * Half-width of the confidence band, as a fraction of the point estimate.
     * +/-25% is a judgement call, not a measurement: it is roughly the spread we would
     * expect between a good run and a bad run on the same corridor at the same hour.
     * Stated as an assumption rather than dressed up as a computed variance.
     */
    public static final double CONFIDENCE_BAND = 0.25;

    private final TrafficFactorRepository trafficFactorRepository;

    /**
     * Priors change only through the admin screens (or a data.sql reseed), and
     * {@link com.ybs.demo.admin.NetworkReloader} calls {@link #clearCache()} after any edit,
     * so this simple cache is safe.
     * ConcurrentHashMap, not HashMap: the 1 Hz simulation tick fills this from the
     * scheduler thread while HTTP request threads read and fill it concurrently.
     */
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    public EtaService(TrafficFactorRepository trafficFactorRepository) {
        this.trafficFactorRepository = trafficFactorRepository;
    }

    /** Time-of-day buckets matching the ones seeded in data.sql. */
    public static String bucketFor(LocalTime time) {
        int h = time.getHour();
        if (h >= 6 && h < 10) return "morning_peak";
        if (h >= 10 && h < 16) return "midday";
        if (h >= 16 && h < 20) return "evening_peak";
        return "night";
    }

    public static String currentBucket() {
        return bucketFor(LocalTime.now());
    }

    /** {@code "<routeCode>:<fromStopId>-<toStopId>"} - the key used in data.sql. */
    public static String segmentId(String routeCode, Long fromStopId, Long toStopId) {
        return routeCode + ":" + fromStopId + "-" + toStopId;
    }

    /**
     * The prior for a segment in a bucket, falling back to the DEFAULT row when the
     * segment has no hand-authored entry of its own.
     */
    public double trafficFactor(String segmentId, String bucket) {
        String key = segmentId + "|" + bucket;
        Double cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        double factor = trafficFactorRepository.findBySegmentIdAndBucket(segmentId, bucket)
                .or(() -> trafficFactorRepository.findBySegmentIdAndBucket("DEFAULT", bucket))
                .map(TrafficFactor::getFactor)
                .orElse(1.0);
        cache.put(key, factor);
        return factor;
    }

    /**
     * The smallest factor anywhere in the prior.
     *
     * <p>Needed by the A* heuristic. Night-time factors are below 1.0, meaning a segment
     * runs <i>faster</i> than its free-flow base time, so a speed bound taken from
     * free-flow timings alone is not actually an upper bound. Dividing by this value
     * restores the guarantee. See {@code AStarPlanner}.
     */
    public double minTrafficFactor() {
        return trafficFactorRepository.findAll().stream()
                .mapToDouble(TrafficFactor::getFactor)
                .filter(f -> f > 0)
                .min()
                .orElse(1.0);
    }

    /** Drops the cached priors, so an edited traffic factor takes effect immediately. */
    public void clearCache() {
        cache.clear();
    }

    /** Point estimate for one segment, in minutes. */
    public double segmentMinutes(double baseMinutes, String segmentId, String bucket) {
        return baseMinutes * trafficFactor(segmentId, bucket);
    }

    /**
     * Wraps a point estimate in its confidence band. The demo line is:
     * "the estimate is a distribution, not a point".
     */
    public Estimate estimate(double pointMinutes, String bucket) {
        Estimate e = new Estimate(
                round(pointMinutes),
                round(pointMinutes * (1 - CONFIDENCE_BAND)),
                round(pointMinutes * (1 + CONFIDENCE_BAND)),
                bucket);
        log.debug("ETA {} min [{}..{}] in bucket {}", e.etaMinutes(), e.etaLowerBound(), e.etaUpperBound(), bucket);
        return e;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public record Estimate(double etaMinutes, double etaLowerBound, double etaUpperBound, String bucket) {}
}
