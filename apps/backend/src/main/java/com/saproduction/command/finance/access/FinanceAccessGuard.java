package com.saproduction.command.finance.access;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FinanceAccessGuard {

  private final FinanceAccessService accessService;

  public FinanceAccessGuard(FinanceAccessService accessService) {
    this.accessService = accessService;
  }

  public boolean isFinanceUnlocked() {
    var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return false;
    }
    var request = attributes.getRequest();
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      return false;
    }
    String token = header.substring(7);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }
    boolean isOwner = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
    if (!isOwner) {
      return false;
    }

    return accessService.isUnlocked(token);
  }
}
