package com.amp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);
    private final UserAccountRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DevDataInitializer(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        userRepository.findAll().forEach(user -> {
            if (user.getPasswordHash() == null || !isValidBcrypt(user.getPasswordHash())) {
                user.setPasswordHash(encoder.encode("admin123"));
                userRepository.save(user);
                log.info("Reset password for dev user: {}", user.getEmail());
            } else if (!encoder.matches("admin123", user.getPasswordHash())) {
                user.setPasswordHash(encoder.encode("admin123"));
                userRepository.save(user);
                log.info("Fixed password hash for dev user: {}", user.getEmail());
            }
        });
    }

    private boolean isValidBcrypt(String hash) {
        return hash != null && hash.startsWith("$2a$") && hash.length() >= 59;
    }
}
