package com.saproduction.command.auth;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
  private final UserRepository users;

  public DatabaseUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    User user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("Owner not found"));
    return org.springframework.security.core.userdetails.User.withUsername(user.email)
        .password(user.passwordHash)
        .roles(user.role)
        .build();
  }
}
