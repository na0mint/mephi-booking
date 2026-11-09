package ru.mephi.booking;

import jakarta.persistence.*;
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
public class User {
    public enum Role { USER, ADMIN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 50) String username;
    @Column(nullable = false) String password;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Role role;
    @Column(nullable = false) boolean active = true;
    @Version long version;
    protected User() {
    }
    User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }
    public Long getId() {
        return id;
    }
}
