package com.saproduction.navigator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NavigatorGatewayApplication {
  public static void main(String[] args) {
    SpringApplication.run(NavigatorGatewayApplication.class, args);
  }
}
