package com.ybs.demo.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads an account for Spring Security, and carries the driver id along with it.
 *
 * <p>{@link SignedInUser} is a {@link UserDetails} with one extra field. Without it, knowing
 * "this request is driver Kyaw Win" would mean a second database lookup by username on every
 * request that needs it - and, worse, the driver id would have to be trusted from the client,
 * which is exactly the hole the login was added to close.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByUsername(username)
                // Deliberately the same message whether the account is missing or the
                // password is wrong: a different one tells an attacker which usernames exist.
                .orElseThrow(() -> new UsernameNotFoundException("bad credentials"));

        return new SignedInUser(
                user.getUsername(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getRole(),
                user.getDriver() == null ? null : user.getDriver().getId(),
                user.getDriver() == null ? null : user.getDriver().getName());
    }

    /** The authenticated principal: standard {@link UserDetails} plus who the driver is. */
    public static class SignedInUser extends org.springframework.security.core.userdetails.User {

        private final AppUser.Role role;
        private final Long driverId;
        private final String driverName;

        public SignedInUser(String username, String passwordHash,
                            List<SimpleGrantedAuthority> authorities,
                            AppUser.Role role, Long driverId, String driverName) {
            super(username, passwordHash, authorities);
            this.role = role;
            this.driverId = driverId;
            this.driverName = driverName;
        }

        public AppUser.Role role() { return role; }

        /** Null for an admin. The one authority on which driver a request is about. */
        public Long driverId() { return driverId; }

        public String driverName() { return driverName; }
    }
}
