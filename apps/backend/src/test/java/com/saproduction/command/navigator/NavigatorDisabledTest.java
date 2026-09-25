package com.saproduction.command.navigator;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NavigatorDisabledTest {
  @Test void productionDefaultDoesNotExposeNavigatorController(){
    new ApplicationContextRunner().withUserConfiguration(NavigatorController.class).withPropertyValues("app.navigator.enabled=false").run(context->assertThat(context).doesNotHaveBean(NavigatorController.class));
  }
}
