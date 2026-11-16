package com.rizkysiregar.narrativ.util;

import com.rizkysiregar.narrativ.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@AllArgsConstructor
public class UsernameGenerator {
    private final UserRepository userRepository;

    public String generateFromEmail(String email) {
        String base = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase();

        if (base.length() < 3) base = "user" + base;

        String candidate = base;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + ThreadLocalRandom.current().nextInt(1000, 9999);
        }
        return candidate;
    }
}
