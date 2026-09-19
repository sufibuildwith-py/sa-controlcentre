package com.saproduction.command.auth;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record OwnerView(String id,String email,String displayName,String role) {}
  private final AuthenticationManager authenticationManager; private final SecurityContextRepository contexts; private final UserRepository users; private final AuditService audit;
  public AuthController(AuthenticationManager authenticationManager,SecurityContextRepository contexts,UserRepository users,AuditService audit){this.authenticationManager=authenticationManager;this.contexts=contexts;this.users=users;this.audit=audit;}
  @PostMapping("/login") public ApiEnvelope<OwnerView> login(@Valid @RequestBody LoginRequest input,HttpServletRequest request,HttpServletResponse response){
    Authentication auth=authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.email(),input.password()));
    SecurityContext context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth); SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response); request.getSession().setMaxInactiveInterval(12*60*60);
    User user=require(auth.getName()); audit.record("AUTH","OWNER_LOGIN",user.id.toString(),null,user.email); return ApiEnvelope.of(view(user));
  }
  @PostMapping("/refresh") public ApiEnvelope<OwnerView> refresh(Authentication auth,HttpServletRequest request){ request.changeSessionId(); return ApiEnvelope.of(view(require(auth.getName()))); }
  @PostMapping("/logout") public ApiEnvelope<Boolean> logout(HttpServletRequest request){ SecurityContextHolder.clearContext(); var session=request.getSession(false); if(session!=null) session.invalidate(); return ApiEnvelope.of(true); }
  @GetMapping("/me") public ApiEnvelope<OwnerView> me(Authentication auth){ return ApiEnvelope.of(view(require(auth.getName()))); }
  private User require(String email){ return users.findByEmailIgnoreCase(email).orElseThrow(()->ApiException.notFound("OWNER_NOT_FOUND","Owner account was not found.")); }
  private OwnerView view(User u){ return new OwnerView(u.id.toString(),u.email,u.displayName,u.role); }
}

