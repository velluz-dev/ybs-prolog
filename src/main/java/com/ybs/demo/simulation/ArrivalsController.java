package com.ybs.demo.simulation;

import com.ybs.demo.network.NetworkService;
import com.ybs.demo.network.Stop;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The two arrival views.
 *
 * <p>{@code /api/stops/{id}/arrivals} is the one a rider wants - the board at the stop
 * saying which bus is next and how long. {@code /api/buses/{id}/arrivals} is the one the
 * trip view wants once the rider is aboard.
 */
@RestController
@RequestMapping("/api")
public class ArrivalsController {

    private final ArrivalPredictor predictor;
    private final NetworkService network;

    public ArrivalsController(ArrivalPredictor predictor, NetworkService network) {
        this.predictor = predictor;
        this.network = network;
    }

    /** Remaining stops for one bus, in running order. */
    @GetMapping("/buses/{id}/arrivals")
    public Map<String, Object> forBus(@PathVariable Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("busId", id);
        out.put("arrivals", predictor.forBus(id));
        return out;
    }

    /** Buses due at one stop, soonest first. */
    @GetMapping("/stops/{id}/arrivals")
    public Map<String, Object> forStop(@PathVariable Long id) {
        Stop stop = network.stop(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stopId", id);
        out.put("stopNameMy", stop == null ? null : stop.getNameMy());
        out.put("stopNameEn", stop == null ? null : stop.getNameEn());
        out.put("arrivals", predictor.forStop(id));
        return out;
    }
}
