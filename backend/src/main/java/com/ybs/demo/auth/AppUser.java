package com.ybs.demo.auth;

import com.ybs.demo.ops.Driver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An account that can sign in.
 *
 * <p>Riders have no row here and never will: planning a trip must not require an account,
 * so the rider half of the API is public and this table exists only for the two roles that
 * see something not everyone should.
 *
 * <p>The {@code driver} link is the point of the table rather than an extra. Before it, the
 * driver screen had a "sign in as" dropdown listing all six drivers, which meant any visitor
 * could read any driver's headway score and complaints - a hole in the demo's own stated
 * terms. A driver account now names exactly one driver row, and the screen shows that one.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /**
     * BCrypt, never the password. {@code create-drop} rebuilds this table from
     * {@code data.sql} on every start, so the hashes are committed - which is fine for
     * BCrypt and for demo passwords, and is called out in the seed file so nobody mistakes
     * that file for something publishable.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Set for DRIVER accounts, null for ADMIN: which driver this person actually is. */
    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;

    public enum Role { ADMIN, DRIVER }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
}
