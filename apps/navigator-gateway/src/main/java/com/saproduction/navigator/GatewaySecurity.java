package com.saproduction.navigator;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class GatewaySecurityConfig {
  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(c->c.disable()).cors(c->{})
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a.requestMatchers("/api/v1/mobile/pairings/redeem","/actuator/health","/ws/navigator/**").permitAll().anyRequest().permitAll())
      .headers(h->h.httpStrictTransportSecurity(s->s.includeSubDomains(true).maxAgeInSeconds(31536000))).build();
  }
}

@Component
class GatewayAuth {
  private final String adminKey;
  private final UUID publicId;
  private final DeviceRepository devices;
  GatewayAuth(@Value("${navigator.admin-key}") String adminKey, @Value("${navigator.organization-public-id}") UUID publicId, DeviceRepository devices) {
    this.adminKey=adminKey; this.publicId=publicId; this.devices=devices;
  }
  void requireAdmin(String value) {
    if(value==null || !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8),value.getBytes(StandardCharsets.UTF_8))) throw new GatewayException(HttpStatus.UNAUTHORIZED,"ADMIN_AUTH_REQUIRED","Admin authentication failed.");
  }
  Device requireDevice(String authorization) {
    if(authorization==null || !authorization.startsWith("Bearer ")) throw new GatewayException(HttpStatus.UNAUTHORIZED,"DEVICE_AUTH_REQUIRED","Device authentication required.");
    var device=devices.findByTokenHash(Hashing.sha256(authorization.substring(7))).orElseThrow(()->new GatewayException(HttpStatus.UNAUTHORIZED,"INVALID_DEVICE","Device authentication failed."));
    if(device.revokedAt!=null) throw new GatewayException(HttpStatus.FORBIDDEN,"DEVICE_REVOKED","This device is no longer connected to SA Productions.");
    return device;
  }
  UUID publicId(){return publicId;}
}

final class Hashing {
  private Hashing() {}
  static String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
  static String token(){var bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
}

@Component
class RateLimits {
  private record Window(Instant start,int count) {}
  private final Map<String,Window> windows=new ConcurrentHashMap<>();
  synchronized void check(String scope,String key,int max,Duration duration) {
    var now=Instant.now(); var id=scope+":"+key; var current=windows.get(id);
    if(current==null || current.start.plus(duration).isBefore(now)){windows.put(id,new Window(now,1));return;}
    if(current.count>=max) throw new GatewayException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many requests. Please retry shortly.",Math.max(1,Duration.between(now,current.start.plus(duration)).toSeconds()));
    windows.put(id,new Window(current.start,current.count+1));
  }
}

class GatewayException extends RuntimeException {
  final HttpStatus status; final String code; final long retryAfter;
  GatewayException(HttpStatus status,String code,String message){this(status,code,message,0);}
  GatewayException(HttpStatus status,String code,String message,long retryAfter){super(message);this.status=status;this.code=code;this.retryAfter=retryAfter;}
}
