package com.saproduction.command.work;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface WorkTaskRepository extends JpaRepository<WorkTask,UUID>,JpaSpecificationExecutor<WorkTask>{
  long countByAssignedEmployeeIdAndStatusNotIn(UUID id,java.util.Collection<WorkTask.Status> statuses);
  long countByProductionIdAndStatusNotIn(UUID id,java.util.Collection<WorkTask.Status> statuses);
}
