package com.saproduction.command.finance.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FinanceAccessFilter extends OncePerRequestFilter {

  private final FinanceAccessService accessService;
  private final AuditService audit;
  private final ObjectMapper objectMapper;

  public FinanceAccessFilter(
      FinanceAccessService accessService, AuditService audit, ObjectMapper objectMapper) {
    this.accessService = accessService;
    this.audit = audit;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
      chain.doFilter(request, response);
      return;
    }

    String path = request.getRequestURI();
    if (isProtectedFinancePath(path)) {
      String header = request.getHeader("Authorization");
      String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      boolean isOwner = auth != null && auth.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));

      boolean unlocked = token != null && isOwner && accessService.isUnlocked(token);

      if (!unlocked) {
        String actor = auth != null ? auth.getName() : "anonymous";
        audit.record(
            "FINANCE_ACCESS",
            "FINANCE_ACCESS_DENIED",
            actor,
            null,
            Map.of("path", path, "reason", isOwner ? "LOCKED" : "FORBIDDEN"));

        response.setStatus(isOwner ? HttpStatus.FORBIDDEN.value() : HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> errorBody = Map.of(
            "error",
            Map.of(
                "code", isOwner ? "FINANCE_LOCKED" : "FORBIDDEN",
                "message", isOwner ? "Finance surface is locked. Please unlock in Developer settings." : "Access denied."));
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        return;
      }
    }

    chain.doFilter(request, response);
  }

  private boolean isProtectedFinancePath(String path) {
    if (path.startsWith("/api/v1/finance-access/")) {
      return false; // unlock, lock, status endpoints must remain accessible
    }
    if (path.startsWith("/api/v1/finance")) {
      return true;
    }
    if (path.startsWith("/api/v1/billing")) {
      return true;
    }
    if (path.startsWith("/api/v1/payroll")) {
      return true;
    }
    if (path.matches("^/api/v1/productions/[^/]+/finance.*$")) {
      return true;
    }
    if (path.matches("^/api/v1/employees/[^/]+/finance.*$")) {
      return true;
    }
    return false;
  }
}
