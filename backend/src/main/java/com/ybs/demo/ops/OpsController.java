package com.ybs.demo.ops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsService ops;

    public OpsController(OpsService ops) {
        this.ops = ops;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("drivers", ops.drivers());
        out.put("bunchingAlerts", ops.bunchingAlerts());
        out.put("incidentCounts", ops.incidentCounts());
        return out;
    }
}
