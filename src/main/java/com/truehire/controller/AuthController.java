package com.truehire.controller;

import com.truehire.model.Role;
import com.truehire.model.User;
import com.truehire.repository.UserRepository;
import com.truehire.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;
import java.util.regex.Pattern;

@Controller
public class AuthController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final MessageSource messages;

    public AuthController(UserRepository userRepository, PasswordService passwordService,
                          MessageSource messages) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.messages = messages;
    }

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        Object userId = session.getAttribute("userId");
        if (userId != null) {
            userRepository.findById((Long) userId)
                    .ifPresent(u -> model.addAttribute("currentUser", u));
        }
        return "index";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) Role role, Model model) {
        model.addAttribute("selectedRole", role == null ? Role.EMPLOYER : role);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        @RequestParam Role role,
                        HttpServletRequest request,
                        Model model,
                        Locale locale) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null || user.getRole() != role || !passwordService.matches(password, user.getPassword())) {
            model.addAttribute("error", message("error.login", locale));
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("selectedRole", role);
            return "login";
        }

        if (passwordService.needsUpgrade(user.getPassword())) {
            user.setPassword(passwordService.encode(password));
            userRepository.save(user);
        }
        startSession(request, user);
        return redirectFor(user.getRole());
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) Role role, Model model) {
        model.addAttribute("selectedRole", role == null ? Role.CANDIDATE : role);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam String phone,
                           @RequestParam(defaultValue = "false") boolean telegramEnabled,
                           @RequestParam(defaultValue = "false") boolean whatsappEnabled,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam Role role,
                           HttpServletRequest request,
                           Model model,
                           Locale locale) {
        String normalizedEmail = normalizeEmail(email);
        String error = validateRegistration(firstName, lastName, phone, normalizedEmail, password, locale);
        if (error == null && userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            error = message("error.account_exists", locale);
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("firstName", firstName.trim());
            model.addAttribute("lastName", lastName.trim());
            model.addAttribute("phone", phone.trim());
            model.addAttribute("email", normalizedEmail);
            model.addAttribute("telegramEnabled", telegramEnabled);
            model.addAttribute("whatsappEnabled", whatsappEnabled);
            model.addAttribute("selectedRole", role);
            return "register";
        }

        User user = userRepository.save(new User(
                normalizedEmail,
                passwordService.encode(password),
                firstName.trim(),
                lastName.trim(),
                phone.trim(),
                telegramEnabled,
                whatsappEnabled,
                role));
        startSession(request, user);
        return redirectFor(role);
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private String validateRegistration(String firstName, String lastName, String phone,
                                        String email, String password, Locale locale) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            return message("error.name_required", locale);
        }
        if (!INTERNATIONAL_PHONE.matcher(phone == null ? "" : phone.trim()).matches()) {
            return message("error.phone", locale);
        }
        if (!EMAIL.matcher(email).matches()) {
            return message("error.email", locale);
        }
        if (password == null || password.length() < 8) {
            return message("error.password", locale);
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String message(String code, Locale locale) {
        return messages.getMessage(code, null, locale);
    }

    private void startSession(HttpServletRequest request, User user) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true).setAttribute("userId", user.getId());
    }

    private String redirectFor(Role role) {
        return role == Role.EMPLOYER ? "redirect:/employer" : "redirect:/candidate";
    }
}
