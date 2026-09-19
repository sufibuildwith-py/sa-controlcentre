package com.saproduction.command.leave;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRepository extends JpaRepository<LeaveRequest, UUID> {
  List<LeaveRequest> findAllByOrderByCreatedAtDesc();
}

