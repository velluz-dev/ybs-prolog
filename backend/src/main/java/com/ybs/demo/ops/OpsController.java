package com.ybs.demo.ops;

import com.ybs.demo.auth.AppUserDetailsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * The signed-in driver's own shift - their route, their month, and why the bonus went
     * the way it did.
     *
     * <p><b>The id comes from the session, never from the request.</b> This screen used to
     * be {@code /api/ops/drivers/{id}} with a dropdown in front of it, which meant anyone
     * could read any driver's headway score and complaints by changing a number in the URL.
     * There is deliberately no parameter here to change.
     */
    @GetMapping("/me/shift")
    public Map<String, Object> myShift(@AuthenticationPrincipal AppUserDetailsService.SignedInUser me) {
        if (me == null || me.driverId() == null) {
            return Map.of();
        }
        return ops.driverShift(me.driverId());
    }

    /** The whole roster. Supervisor's view, so ADMIN only - see SecurityConfig. */
    @GetMapping("/drivers")
    public List<Map<String, Object>> drivers() {
        return ops.driverList();
    }

    /** Any driver's shift, for a supervisor. ADMIN only, for the same reason. */
    @GetMapping("/drivers/{id}")
    public Map<String, Object> driver(@PathVariable Long id) {
        return ops.driverShift(id);
    }
}
