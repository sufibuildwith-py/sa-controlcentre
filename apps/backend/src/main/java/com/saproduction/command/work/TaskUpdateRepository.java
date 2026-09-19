package com.saproduction.command.work;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskUpdateRepository extends JpaRepository<TaskUpdate,UUID>{List<TaskUpdate> findAllByTaskIdOrderByCreatedAtDesc(UUID id);}
