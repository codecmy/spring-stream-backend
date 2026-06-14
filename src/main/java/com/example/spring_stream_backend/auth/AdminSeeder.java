package com.example.spring_stream_backend.auth;

import com.example.spring_stream_backend.Entity.Role;
import com.example.spring_stream_backend.Entity.User;
import com.example.spring_stream_backend.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class AdminSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.emails:}")
    private String adminEmailsStr;

    @Value("${admin.password:}")
    private String adminPassword;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void seed() {
        if (adminPassword == null || adminPassword.isBlank()) return;

        String[] emails = adminEmailsStr.split(",");
        for (String raw : emails) {
            String email = raw.trim().toLowerCase();
            if (email.isBlank()) continue;

            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                if (user.getPassword() == null) {
                    user.setPassword(passwordEncoder.encode(adminPassword));
                    user.setRole(Role.ADMIN);
                    userRepository.save(user);
                }
            }, () -> {
                User admin = User.builder()
                        .id(UUID.randomUUID().toString())
                        .email(email)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(Role.ADMIN)
                        .createdAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
            });
        }
    }
}
