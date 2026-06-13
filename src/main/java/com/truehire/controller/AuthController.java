package com.truehire.controller;

import com.truehire.model.Role;
import com.truehire.model.User;
import com.truehire.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Упрощённая «авторизация» для демо: вход одной кнопкой под демо-пользователем
 * нужной роли. Без Spring Security — храним id пользователя в HTTP-сессии.
 */
@Controller
public class AuthController {

    public static final String DEMO_EMPLOYER_EMAIL = "employer@truehire.io";
    public static final String DEMO_CANDIDATE_EMAIL = "candidate@truehire.io";

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
    public String login(@RequestParam Role role, HttpSession session) {
        String email = (role == Role.EMPLOYER) ? DEMO_EMPLOYER_EMAIL : DEMO_CANDIDATE_EMAIL;
        User user = userRepository.findByEmail(email).orElseThrow();
        session.setAttribute("userId", user.getId());
        return (role == Role.EMPLOYER) ? "redirect:/employer" : "redirect:/candidate";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
