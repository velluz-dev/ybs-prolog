package com.ybs.demo.alerts;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private final ArrivalAlertService alerts;

    public AlertController(ArrivalAlertService alerts) {
        this.alerts = alerts;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestParam Long busId, @RequestParam Long stopId) {
        alerts.startTrip(busId, stopId);
        return Map.of("watching", true, "busId", busId, "stopId", stopId);
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel() {
        alerts.cancelTrip();
        return Map.of("watching", false);
    }

    /** Polled at 1 Hz; reports {@code triggered=true} exactly once per trip. */
    @GetMapping("/state")
    public Map<String, Object> state() {
        return alerts.poll();
    }
}
