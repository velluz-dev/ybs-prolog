package com.ybs.demo.simulation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimController {

    private final BusSimulator simulator;

    public SimController(BusSimulator simulator) {
        this.simulator = simulator;
    }

    /** Polled at 1 Hz by the map. */
    @GetMapping("/buses/live")
    public List<Map<String, Object>> liveBuses() {
        return simulator.snapshot();
    }

    /**
     * Speed multiplier. The demo needs 60x to drive a bus into the arrival geofence
     * within seconds of pressing "start trip" rather than waiting out real minutes.
     */
    @PostMapping("/sim/speed")
    public Map<String, Object> setSpeed(@RequestParam int multiplier) {
        int clamped = switch (multiplier) {
            case 1, 10, 60 -> multiplier;
            default -> 1;
        };
        simulator.setSpeedMultiplier(clamped);
        return Map.of("multiplier", clamped);
    }

    @GetMapping("/sim/speed")
    public Map<String, Object> getSpeed() {
        return Map.of("multiplier", simulator.getSpeedMultiplier());
    }
}
