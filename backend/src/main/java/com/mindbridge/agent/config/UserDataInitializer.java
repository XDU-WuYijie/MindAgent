package com.mindbridge.agent.config;

import com.mindbridge.agent.entity.AppUser;
import com.mindbridge.agent.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserDataInitializer {

    @Bean
    public CommandLineRunner initUsers(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
            if (!userRepository.existsByUsername("user")) {
                AppUser user = new AppUser();
                user.setUsername("user");
                user.setPasswordHash(passwordEncoder.encode("user123"));
                user.setRole("USER");
                userRepository.save(user);
            }
        };
    }
}
