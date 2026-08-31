package com.ybs.demo.auth;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is signed in.
 *
 * <p>Login and logout are handled by Spring Security's own filters at
 * {@code POST /api/auth/login} and {@code POST /api/auth/logout} - there is no controller
 * method for them, which is the point: the password never passes through code written here.
 * All three answer in the same shape, so the page has one thing to read.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Public on purpose. A page that cannot ask "am I signed in?" without being signed in
     * has to guess, and would show a driver nav to someone whose session had quietly expired.
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return identityOf(authentication);
    }

    /**
     * Shared with {@link SecurityConfig} so login, logout and this endpoint cannot drift.
     *
     * <p>Never includes the password hash, and never includes anything about other users -
     * a driver learning the depot's roster from their own identity call would be the same
     * leak the dropdown was.
     */
    static Map<String, Object> identityOf(Authentication authentication) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserDetailsService.SignedInUser;

        out.put("authenticated", signedIn);
        if (!signedIn) {
            // A signed-out visitor is a rider, and riders are a real role here rather than
            // an absence - it is what the sidebar shows when nobody has logged in.
            out.put("role", "RIDER");
            return out;
        }

        var user = (AppUserDetailsService.SignedInUser) authentication.getPrincipal();
        out.put("username", user.getUsername());
        out.put("role", user.role().name());
        out.put("driverId", user.driverId());
        out.put("driverName", user.driverName());
        return out;
    }
}
