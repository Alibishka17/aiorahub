package com.truehire.service;

import com.truehire.model.User;
import com.truehire.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AccountSettingsService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public AccountSettingsService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public String update(User user,
                         String firstName,
                         String lastName,
                         String phone,
                         String email,
                         boolean telegramEnabled,
                         boolean whatsappEnabled,
                         String currentPassword,
                         String newPassword) {
        String normalizedFirstName = trim(firstName);
        String normalizedLastName = trim(lastName);
        String normalizedPhone = trim(phone);
        String normalizedEmail = trim(email).toLowerCase(Locale.ROOT);
        String normalizedNewPassword = newPassword == null ? "" : newPassword;

        if (normalizedFirstName.isBlank() || normalizedLastName.isBlank()) {
            return "error.name_required";
        }
        if (!INTERNATIONAL_PHONE.matcher(normalizedPhone).matches()) {
            return "error.phone";
        }
        if (!EMAIL.matcher(normalizedEmail).matches()) {
            return "error.email";
        }
        if (userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId())).isPresent()) {
            return "error.account_exists";
        }
        if (!normalizedNewPassword.isBlank()) {
            if (!passwordService.matches(currentPassword, user.getPassword())) {
                return "settings.error.current_password";
            }
            if (normalizedNewPassword.length() < 8) {
                return "error.password";
            }
        }

        user.setFirstName(normalizedFirstName);
        user.setLastName(normalizedLastName);
        user.setName(normalizedFirstName + " " + normalizedLastName);
        user.setPhone(normalizedPhone);
        user.setEmail(normalizedEmail);
        user.setTelegramEnabled(telegramEnabled);
        user.setWhatsappEnabled(whatsappEnabled);
        if (!normalizedNewPassword.isBlank()) {
            user.setPassword(passwordService.encode(normalizedNewPassword));
        }
        userRepository.save(user);
        return null;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
