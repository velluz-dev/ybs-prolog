package com.ybs.demo.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to serve traffic with the seeded demo passwords still in place.
 *
 * <p>{@code data.sql} ships working accounts on purpose - {@code admin/admin123} and one per
 * driver - because a demo that cannot be signed into is not a demo, and the whole seed is
 * {@code INSERT IGNORE}, so every fresh database gets them. That is exactly right on a
 * laptop. It is an open door the moment the app has a public address, and the plaintext is
 * in a comment in a public repository.
 *
 * <p>So under the {@code prod} profile the app checks whether those passwords still work and
 * <b>stops</b> if they do. Refusing to start is deliberate: a warning in a log nobody reads
 * is how this kind of thing survives to production. Change the hashes in {@code data.sql},
 * or change the passwords in the running database, and the check passes.
 *
 * <p>If you genuinely want the demo logins on a public instance - a viva, a walkthrough, a
 * throwaway box - set {@code YBS_ALLOW_DEMO_LOGINS=true}. The escape hatch exists so this
 * stays a decision somebody made rather than an obstacle they worked around by deleting the
 * check.
 */
@Component
@Profile("prod")
public class DemoCredentialGuard implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoCredentialGuard.class);

    /** The passwords documented in the seed file, and therefore the ones an attacker tries. */
    private static final List<String> SEEDED = List.of("admin123", "driver123");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Environment env;

    public DemoCredentialGuard(AppUserRepository users, PasswordEncoder encoder, Environment env) {
        this.users = users;
        this.encoder = encoder;
        this.env = env;
    }

    /**
     * An {@link ApplicationRunner}, not an {@code ApplicationReadyEvent} listener - and the
     * difference is the whole feature. An exception thrown from a ready-event listener is
     * logged and swallowed: the first version of this printed its refusal and then served
     * traffic anyway, with the container reporting healthy. A runner that throws brings the
     * context down and the process with it, which is what "refuses to start" has to mean.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (Boolean.parseBoolean(env.getProperty("YBS_ALLOW_DEMO_LOGINS", "false"))) {
            log.warn("YBS_ALLOW_DEMO_LOGINS is set - the seeded demo passwords are accepted on "
                    + "this instance. Anyone who has read the repository can sign in as admin.");
            return;
        }

        List<String> exposed = users.findAll().stream()
                .filter(u -> SEEDED.stream().anyMatch(p -> encoder.matches(p, u.getPasswordHash())))
                .map(AppUser::getUsername)
                .toList();

        if (!exposed.isEmpty()) {
            throw new IllegalStateException("""
                    Refusing to start: these accounts still use the passwords published in \
                    data.sql - %s. Anyone who has read the repository can sign in as them.

                    Fix one of these, then start again:
                      * change the passwords in the database, or
                      * replace the BCrypt hashes in data.sql and reseed, or
                      * set YBS_ALLOW_DEMO_LOGINS=true if this instance is meant to be open.
                    """.formatted(String.join(", ", exposed)));
        }
        log.info("Credential check passed: no account uses a password published in data.sql.");
    }
}
