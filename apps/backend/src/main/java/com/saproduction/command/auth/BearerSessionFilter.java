package com.saproduction.command.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerSessionFilter extends OncePerRequestFilter {
  private final ApiSessionService sessions;

  public BearerSessionFilter(ApiSessionService sessions) {
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      User user = sessions.authenticate(header.substring(7));
      if (user != null) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    user.email,
                    null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.role))));
      }
    }
    chain.doFilter(request, response);
  }
}
