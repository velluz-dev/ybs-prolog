package com.ybs.demo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who can reach what.
 *
 * <p>The organising rule, and the one to state in a viva: <b>a rider is never asked to sign
 * in.</b> Planning a trip, the live map, the arrivals board, the A* trace and the Prolog
 * console are all public. Signing in unlocks two things and only two: a driver's own shift,
 * and the admin screens. Spring Security denies by default, so every public path is listed
 * here explicitly - the allowlist is the documentation.
 *
 * <h2>Three decisions worth defending</h2>
 *
 * <p><b>Sessions, not tokens.</b> There is one server, one browser and no third-party client.
 * A JWT would add signing keys, expiry handling and a revocation problem in exchange for
 * statelessness this app has no use for.
 *
 * <p><b>CSRF stays on, including for the public POSTs.</b> {@code /api/sim/speed} and
 * {@code /api/disruptions/flood} change global demo state without authentication, so strictly
 * they have no privilege for a forged request to borrow. They keep the protection anyway,
 * because "this endpoint is exempt and here is the threat-model argument" is a comment that
 * rots - and the cost is one header in the frontend's fetch helper. The token goes in a
 * readable cookie, which is the standard arrangement for a page that talks to its own API
 * with {@code fetch}.
 *
 * <p><b>Login answers with JSON, not a redirect.</b> The frontend is a static page driving
 * the API with {@code fetch}; a 302 to a login page would arrive as an opaque redirect it
 * cannot act on. So the handlers below return 200 or 401 with a body, and the page decides
 * what to show. Same for the entry point: an unauthenticated API call gets 401, not HTML.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Everything a passenger touches. Listed rather than pattern-matched loosely, so adding
     * an endpoint means deciding on purpose whether it is public.
     */
    private static final String[] PUBLIC_API = {
            "/api/health",
            "/api/network",
            "/api/routes/**",
            "/api/journeys/**",
            "/api/buses/**",
            "/api/stops/**",
            "/api/eta/**",
            "/api/voice/**",
            "/api/alert/**",
            "/api/sim/**",
            "/api/disruptions/**",
            "/api/prolog/**"
    };

    /** The static assets the public page is built from. */
    private static final String[] PUBLIC_PAGES = {
            "/", "/index.html", "/app.js", "/i18n.js", "/style.css",
            "/vendor/**", "/audio/**", "/voice/**", "/img/**", "/favicon.ico"
    };

    private final ObjectMapper json = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt at its default strength. The seeded hashes in data.sql were generated
        // with this same encoder; regenerate them if the strength ever changes.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrf = new CsrfTokenRequestAttributeHandler();
        // Hand the raw token to the cookie rather than deferring it, so the very first
        // page load already has an XSRF-TOKEN to send back.
        csrf.setCsrfRequestAttributeName(null);

        http
            .csrf(c -> c
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrf))

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_PAGES).permitAll()
                    .requestMatchers(PUBLIC_API).permitAll()
                    .requestMatchers("/api/auth/me").permitAll()   // "am I signed in?" must be askable

                    // A driver's own shift. Which driver is read from the session, never
                    // from the request - see OpsController.myShift.
                    .requestMatchers("/api/ops/me/**").hasRole("DRIVER")

                    // Everything else under ops is the supervisor's view of all drivers.
                    .requestMatchers("/api/ops/**").hasRole("ADMIN")
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/admin.html", "/admin.js", "/ops.html").hasRole("ADMIN")

                    .anyRequest().authenticated())

            .formLogin(form -> form
                    .loginProcessingUrl("/api/auth/login")
                    .successHandler((req, res, a) -> writeIdentity(res, HttpStatus.OK))
                    .failureHandler((req, res, e) -> writeError(res, "bad credentials")))

            .logout(out -> out
                    .logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout", "POST"))
                    .logoutSuccessHandler((req, res, a) -> writeIdentity(res, HttpStatus.OK))
                    .deleteCookies("JSESSIONID"))

            .exceptionHandling(e -> e
                    // 401 with a body, never a redirect to HTML: the caller is fetch().
                    .authenticationEntryPoint((req, res, ex) -> writeError(res, "authentication required"))
                    .accessDeniedHandler((req, res, ex) -> {
                        res.setStatus(HttpStatus.FORBIDDEN.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                        res.getWriter().write("{\"error\":\"forbidden\"}");
                    }))

            // Frames stay same-origin. This was relaxed for the H2 console, which went with
            // the move to MySQL; it is kept because nothing here is meant to be embedded
            // cross-origin either way.
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }

    /** The shape {@code /api/auth/me} returns, so login and logout answer in it too. */
    private void writeIdentity(HttpServletResponse res, HttpStatus status) throws java.io.IOException {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        Map<String, Object> body = AuthController.identityOf(auth);
        res.setStatus(status.value());
        // Charset explicitly, on all three of these. MediaType.APPLICATION_JSON_VALUE is
        // "application/json" with no charset, so getWriter() falls back to the container
        // default - cp1252 on this machine - and a driver called ဦးကျော်ဝင်း came back as
        // a row of question marks. Spring's own message converters get this right, which is
        // exactly why only the handlers hand-written here were wrong.
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        res.getWriter().write(json.writeValueAsString(body));
    }

    private void writeError(HttpServletResponse res, String message) throws java.io.IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", false);
        body.put("error", message);
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        res.getWriter().write(json.writeValueAsString(body));
    }
}
