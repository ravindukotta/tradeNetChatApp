package com.zuhlke.tradenet.chat.auth;

import com.zuhlke.tradenet.chat.entity.User;
import com.zuhlke.tradenet.chat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final List<String> DEMO_USERNAMES = List.of("alice", "bob", "carol", "dave");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,  @Value("${app.seed.password}") String seedPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {

        // runs only if there is no data
        if (userRepository.count() == 0) {

            String hash = passwordEncoder.encode(seedPassword);
            for (String username : DEMO_USERNAMES) {
                userRepository.save(new User(username, hash));
            }
        }

    }
}
