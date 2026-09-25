package com.saproduction.command.finance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Loads the private demo workbook by content hash; repeat startup is idempotent. */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookDemoLoader {
  private final FinanceMigrationService migration;
  private final String path;

  public FinanceWorkbookDemoLoader(FinanceMigrationService migration,
      @Value("${FINANCE_WORKBOOK_PATH:}") String path) {
    this.migration = migration;
    this.path = path;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() throws IOException {
    load();
  }

  public void load() throws IOException {
    if (path.isBlank()) return;
    Path workbook = Path.of(path);
    if (!Files.isRegularFile(workbook)) throw new IOException("Configured Finance workbook is missing: " + workbook);
    migration.preview(workbook.getFileName().toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(workbook)));
  }
}
