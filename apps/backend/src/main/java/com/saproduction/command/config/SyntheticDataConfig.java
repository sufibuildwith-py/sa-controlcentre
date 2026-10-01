package com.saproduction.command.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class SyntheticDataConfig {
  @Bean
  @Order(30)
  CommandLineRunner syntheticDataCommandLineRunner(
      @Value("${app.seed-synthetic:false}") boolean enabled,
      SyntheticDatasetSeeder seeder) {
    return args -> {
      if (enabled) {
        seeder.seed();
      }
    };
  }
}
