package com.ybs.demo.simulation;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the 1 Hz {@link com.ybs.demo.simulation.BusSimulator} tick.
 *
 * <p>Live positions are delivered by polling {@code GET /api/buses/live} once a second
 * rather than pushed over STOMP. The brief allows either; polling was chosen because it
 * needs no client library, has no handshake to fail on stage, and at six buses on
 * localhost the difference in cost is not measurable.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
