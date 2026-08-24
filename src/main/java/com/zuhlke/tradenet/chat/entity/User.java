package com.zuhlke.tradenet.chat.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Table name is "app_users", not "user" - USER is a reserved/ambiguous identifier in H2
// (it also exists as a built-in function), so using it as a table name invites subtle
// SQL parsing surprises.
@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // JPA
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
