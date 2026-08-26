package com.ybs.demo.admin;

import com.ybs.demo.eta.EtaService;
import com.ybs.demo.network.NetworkService;
import com.ybs.demo.planning.StopGraph;
import com.ybs.demo.simulation.BusSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Rebuilds every in-memory cache after the data changes.
 *
 * <p>The network, the search graph and the fleet are all read once at startup and held in
 * memory, because the 1 Hz tick and A* consult them far more often than they change. That
 * is the right trade until something *does* change: without this, a stop added through the
 * admin screens would sit in the database and be invisible to the map, the planner and the
 * simulation until the next restart.
 *
 * <p>Order matters. The graph is built from the cached itineraries, and the simulator reads
 * those itineraries to move buses along them, so the network has to be refreshed first.
 */
@Service
public class NetworkReloader {
    private static final Logger log = LoggerFactory.getLogger(NetworkReloader.class);

    private final NetworkService network;
    private final StopGraph graph;
    private final BusSimulator simulator;
    private final EtaService eta;

    public NetworkReloader(NetworkService network, StopGraph graph,
                           BusSimulator simulator, EtaService eta) {
        this.network = network;
        this.graph = graph;
        this.simulator = simulator;
        this.eta = eta;
    }

    /** Call after any write. Cheap enough at this scale to do unconditionally. */
    public synchronized void reload() {
        long started = System.currentTimeMillis();
        eta.clearCache();     // an edited traffic factor must not keep serving the old value
        network.load();
        graph.build();
        simulator.load();
        log.info("Reloaded network, graph and fleet after an admin edit in {} ms",
                System.currentTimeMillis() - started);
    }
}
