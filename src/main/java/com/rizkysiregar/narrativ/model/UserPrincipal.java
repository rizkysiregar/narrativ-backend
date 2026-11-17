package com.rizkysiregar.narrativ.model;

import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private UserEntity userEntity;
    public UserPrincipal(UserEntity userEntity) {
        this.userEntity = userEntity;
    }

    // customer
    public UUID getUserId() {
        return this.userEntity.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //return Collections.singleton(new SimpleGrantedAuthority("USER"));
        // produces: "ROLE_USER" or "ROLE_ADMIN"
        return List.of(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole().name()));
    }

    @Override
    public String getPassword() {
        return this.userEntity.getPassword();
    }

    @Override
    public String getUsername() {
        return this.userEntity.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        //return UserDetails.super.isAccountNonExpired();
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        //return UserDetails.super.isAccountNonLocked();
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        //return UserDetails.super.isCredentialsNonExpired();
        return  true;
    }

    // "enabled" only covers the account the user deleted himself; a ban is reported separately (USER_BANNED)
    @Override
    public boolean isEnabled() {
        return !this.userEntity.isDeleted();
    }

    public String getBanReason() {
        return this.userEntity.getBanReason();
    }

    public boolean isBanned() {
        return this.userEntity.getStatus() == UserStatus.BANNED;
    }
}
