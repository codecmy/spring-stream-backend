package com.example.spring_stream_backend.auth;

import com.example.spring_stream_backend.Entity.Role;
import com.example.spring_stream_backend.Entity.User;
import com.example.spring_stream_backend.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationProvider implements AuthenticationProvider {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final Set<String> adminEmails;

    public JwtAuthenticationProvider(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            @Value("${admin.emails:}") String adminEmailsStr
    ) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.adminEmails = new HashSet<>();
        if (adminEmailsStr != null && !adminEmailsStr.isBlank()) {
            for (String email : adminEmailsStr.split(",")) {
                this.adminEmails.add(email.trim().toLowerCase());
            }
        }
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String token = (String) authentication.getCredentials();

        Claims claims;
        try {
            claims = jwtUtil.validateToken(token);
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid token", e);
        }

        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        if (userId == null || userId.isBlank()) {
            throw new BadCredentialsException("Invalid token: missing subject");
        }

        User user = userRepository.findById(userId).orElseGet(() -> {
            User newUser = User.builder()
                    .id(userId)
                    .email(email != null ? email : userId)
                    .role(Role.USER)
                    .createdAt(LocalDateTime.now())
                    .build();
            return userRepository.save(newUser);
        });

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
        }

        if (email != null && adminEmails.contains(email.toLowerCase()) && user.getRole() != Role.ADMIN) {
            user.setRole(Role.ADMIN);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new JwtAuthenticationToken(user, token, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return JwtAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
