package com.saproduction.command.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** A per-launch native secret protects even first-owner setup from other local web pages. */
@Component
public class DesktopBoundaryFilter extends OncePerRequestFilter {
  private final boolean desktop;
  private final String secret;

  public DesktopBoundaryFilter(
      @Value("${app.deployment:server}") String deployment,
      @Value("${SA_DESKTOP_SECRET:}") String secret) {
    desktop = deployment.equals("desktop");
    this.secret = secret;
    if (desktop && secret.length() < 64)
      throw new IllegalStateException("Native launch authorization is required.");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (desktop && !request.getMethod().equals("OPTIONS")) {
      String supplied = request.getHeader("X-SA-Desktop-Key");
      if (supplied == null
          || !MessageDigest.isEqual(
              secret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
        response.sendError(403);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
