package com.smarttrust.modules.user.config;

import com.smarttrust.modules.user.domain.entity.User;
import com.smarttrust.modules.user.domain.enums.UserRole;
import com.smarttrust.modules.user.domain.enums.UserStatus;
import com.smarttrust.modules.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

/**
 * Optional admin bootstrap: set ADMIN_PHONE + ADMIN_PASSWORD env vars (.env) to create
 * the first ADMIN account automatically (only if that phone is not already registered).
 * No-op when the variables are unset — safe for production.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminUserSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${smarttrust.admin.phone:}")
    private String adminPhone;

    @Value("${smarttrust.admin.email:}")
    private String adminEmail;

    @Value("${smarttrust.admin.password:}")
    private String adminPassword;

    @Bean
    public ApplicationRunner adminSeederRunner() {
        return args -> seedAdmin();
    }

    private void seedAdmin() {
        if (adminPhone == null || adminPhone.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped (ADMIN_PHONE/ADMIN_PASSWORD not set)");
            return;
        }
        String phone = adminPhone.trim();
        if (userRepository.existsByPhone(phone)) {
            log.info("Admin bootstrap skipped (phone already registered: {})", phone);
            return;
        }
        User admin = User.builder()
                .phone(phone)
                .email(adminEmail == null || adminEmail.isBlank() ? null : adminEmail.trim().toLowerCase(Locale.ROOT))
                .fullName("SmartTrust Admin")
                .phoneVerified(true)
                .emailVerified(true)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);
        log.info("ADMIN account created phone={} — change this password after first login", phone);
    }
}
