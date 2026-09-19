package com.saproduction.command;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthFoundationTest {
  @Test void hashesOwnerPasswordWithAdaptiveBcrypt(){
    var encoder=new BCryptPasswordEncoder(12);String encoded=encoder.encode("SADemo!2026");
    assertThat(encoded).doesNotContain("SADemo!2026");
    assertThat(encoder.matches("SADemo!2026",encoded)).isTrue();
    assertThat(encoder.matches("wrong",encoded)).isFalse();
  }
}

