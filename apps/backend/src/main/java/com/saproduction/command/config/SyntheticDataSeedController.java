package com.saproduction.command.config;

import com.saproduction.command.shared.ApiEnvelope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/seed")
public class SyntheticDataSeedController {
  private final SyntheticDatasetSeeder seeder;

  public SyntheticDataSeedController(SyntheticDatasetSeeder seeder) {
    this.seeder = seeder;
  }

  @PostMapping("/synthetic")
  public ApiEnvelope<SyntheticDatasetSeeder.SeedResult> seed() {
    return ApiEnvelope.of(seeder.seed());
  }
}
