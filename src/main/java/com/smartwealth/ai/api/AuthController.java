package com.smartwealth.ai.api;

import com.smartwealth.ai.domain.Tenant;
import com.smartwealth.ai.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TenantRepository tenantRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JavaMailSender mailSender;
    private final String domain;

    public AuthController(TenantRepository tenantRepository,
                          JavaMailSender mailSender,
                          @Value("${stripe.domain}") String domain) {
        this.tenantRepository = tenantRepository;
        this.mailSender = mailSender;
        this.domain = domain;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Valid email is required"));
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        if (tenantRepository.findByEmail(email.trim().toLowerCase()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "This email is already registered"));
        }

        String tenantId = "study_" + UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(email.trim().toLowerCase());
        tenant.setTenantGroup("study");
        tenant.setEmail(email.trim().toLowerCase());
        tenant.setPasswordHash(passwordEncoder.encode(password));
        tenant.setTrialEndsAt(LocalDate.now().plusDays(7));
        tenantRepository.save(tenant);

        return ResponseEntity.ok(Map.of(
                "tenantId", tenantId,
                "email", email.trim().toLowerCase(),
                "tenantGroup", "study",
                "trialEndsAt", LocalDate.now().plusDays(7).toString()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        Tenant tenant = tenantRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (tenant == null || tenant.getPasswordHash() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        if (!passwordEncoder.matches(password, tenant.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tenantId", tenant.getTenantId());
        result.put("email", tenant.getEmail());
        result.put("name", tenant.getName());
        result.put("tenantGroup", tenant.getTenantGroup());
        result.put("trialEndsAt", tenant.getTrialEndsAt() != null ? tenant.getTrialEndsAt().toString() : null);
        result.put("subscriptionExpiry", tenant.getSubscriptionExpiry() != null ? tenant.getSubscriptionExpiry().toString() : null);
        result.put("subscriptionActive", tenant.isSubscriptionActive());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Valid email is required"));
        }

        Tenant tenant = tenantRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        // Always return success to avoid email enumeration
        if (tenant == null || tenant.getPasswordHash() == null) {
            return ResponseEntity.ok(Map.of("message", "If this email is registered, a reset link has been sent"));
        }

        String token = UUID.randomUUID().toString().substring(0, 12);
        tenant.setResetToken(token);
        tenant.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        tenantRepository.save(tenant);

        String resetUrl = domain + "/login.html?reset=" + token;

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(tenant.getEmail());
            msg.setSubject("SmartRAG — Password Reset");
            msg.setText("Hi,\n\n"
                    + "We received a request to reset your SmartRAG password.\n\n"
                    + "Click the link below to reset your password (valid for 1 hour):\n"
                    + resetUrl + "\n\n"
                    + "If you didn't request this, you can ignore this email.\n\n"
                    + "— SmartRAG");
            mailSender.send(msg);
        } catch (Exception e) {
            // Mail failed — log it and return the token URL for dev/debug
            System.err.println("Failed to send password reset email: " + e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "message", "If this email is registered, a reset link has been sent",
                    "debugResetUrl", resetUrl
            ));
        }

        return ResponseEntity.ok(Map.of("message", "If this email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reset token is required"));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> token.equals(t.getResetToken()))
                .findFirst().orElse(null);

        if (tenant == null || tenant.getResetTokenExpiry() == null
                || tenant.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid or expired reset token"));
        }

        tenant.setPasswordHash(passwordEncoder.encode(newPassword));
        tenant.setResetToken(null);
        tenant.setResetTokenExpiry(null);
        tenantRepository.save(tenant);

        return ResponseEntity.ok(Map.of("message", "Password has been reset. You can now log in."));
    }
}
