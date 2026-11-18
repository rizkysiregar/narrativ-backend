package com.rizkysiregar.narrativ.service.impl;


import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    final UserRepository userRepository;
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        Optional<UserEntity> user = userRepository.findByUsername(username);

        if (user.isEmpty()) {
            throw new UsernameNotFoundException(Constants.INVALID_USERNAME_PASSWORD);
        }

        return new UserPrincipal(user.get());
    }

    /** The lookup behind an access token: its subject is the user id, which never changes. */
    public @NonNull UserPrincipal loadUserById(@NonNull UUID userId) throws UsernameNotFoundException {
        return userRepository.findById(userId)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(Constants.INVALID_USERNAME_PASSWORD));
    }
}
