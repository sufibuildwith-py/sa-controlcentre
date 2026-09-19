package com.saproduction.command.production;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface ProductionRepository extends JpaRepository<Production,UUID>,JpaSpecificationExecutor<Production>{}
