package com.truehire.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users") // "user" — зарезервированное слово в H2
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    private String firstName;

    private String lastName;

    private String phone;

    private boolean telegramEnabled;

    private boolean whatsappEnabled;

    private String cvFileName;

    private String cvContentType;

    private String cvStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public User() {
    }

    public User(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    public User(String email, String password, String firstName, String lastName,
                String phone, boolean telegramEnabled, boolean whatsappEnabled, Role role) {
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.name = (firstName + " " + lastName).trim();
        this.phone = phone;
        this.telegramEnabled = telegramEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isTelegramEnabled() { return telegramEnabled; }
    public void setTelegramEnabled(boolean telegramEnabled) { this.telegramEnabled = telegramEnabled; }

    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public void setWhatsappEnabled(boolean whatsappEnabled) { this.whatsappEnabled = whatsappEnabled; }

    public String getCvFileName() { return cvFileName; }
    public void setCvFileName(String cvFileName) { this.cvFileName = cvFileName; }

    public String getCvContentType() { return cvContentType; }
    public void setCvContentType(String cvContentType) { this.cvContentType = cvContentType; }

    public String getCvStorageKey() { return cvStorageKey; }
    public void setCvStorageKey(String cvStorageKey) { this.cvStorageKey = cvStorageKey; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
