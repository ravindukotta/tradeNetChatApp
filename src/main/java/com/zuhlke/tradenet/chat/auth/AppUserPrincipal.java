package com.zuhlke.tradenet.chat.auth;

import com.zuhlke.tradenet.chat.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Wraps our own User entity instead of Spring Security's default User so downstream
// code (e.g. the WebSocket handshake interceptor in task 3.4) can pull the app-level
// numeric user id straight off the Authentication principal, not just a username string.
public class AppUserPrincipal implements UserDetails {

    private final User user;

    public AppUserPrincipal(User user) {
        this.user = user;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
