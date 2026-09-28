package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Finite Command Registry for EVE Phase 3 Governed Execution.
 * Guarantees that only explicitly declared and implemented domain commands can ever be
 * proposed or dispatched through the EVE Command Gateway.
 * Any unmapped or dynamic command is unconditionally rejected.
 */
@Component
public class EveCommandRegistry {

  private final Map<String, EveCommandDefinition> definitions = new HashMap<>();

  @Autowired
  public EveCommandRegistry(List<EveCommandDefinition> commandDefs) {
    if (commandDefs != null) {
      for (EveCommandDefinition def : commandDefs) {
        register(def);
      }
    }
  }

  public void register(EveCommandDefinition def) {
    if (def != null && def.commandType() != null) {
      definitions.put(def.commandType().toUpperCase(Locale.ROOT), def);
      // Convenience alias without RECORD_ prefix if applicable
      if (def.commandType().startsWith("RECORD_")) {
        definitions.put(def.commandType().substring("RECORD_".length()).toUpperCase(Locale.ROOT), def);
      }
    }
  }

  public Optional<EveCommandDefinition> get(String commandType) {
    if (commandType == null || commandType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(definitions.get(commandType.trim().toUpperCase(Locale.ROOT)));
  }

  public EveCommandDefinition getRequired(String commandType) {
    return get(commandType).orElseThrow(() -> ApiException.badRequest(
        "UNSUPPORTED_COMMAND",
        "Command '" + commandType + "' is not registered in the finite EVE Command Registry."));
  }

  public boolean isSupported(String commandType) {
    return get(commandType).isPresent();
  }

  public List<EveDtos.EveCommand> listCommands() {
    List<EveDtos.EveCommand> result = new ArrayList<>();
    Set<EveCommandDefinition> seen = new HashSet<>();
    for (EveCommandDefinition def : definitions.values()) {
      if (seen.add(def)) {
        result.add(new EveDtos.EveCommand(
            def.commandType(),
            def.domain(),
            def.description(),
            def.riskTier(),
            def.confirmationPolicy(),
            List.of(),
            true,
            "CANONICAL_POSTGRESQL_VERIFICATION"));
      }
    }
    return result;
  }
}
