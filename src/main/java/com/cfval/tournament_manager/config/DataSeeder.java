package com.cfval.tournament_manager.config;

import com.cfval.tournament_manager.model.Role;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        boolean adminExists = userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() == Role.ADMIN);

        if (!adminExists) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@thanos.com");
            admin.setPasswordHash(passwordEncoder.encode("Admin1234"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("Default admin user created (username: admin)");
        }
    }
}
