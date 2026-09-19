package com.saproduction.command.attendance;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {
  Optional<AttendanceRecord> findByEmployeeIdAndDate(UUID employeeId, LocalDate date);
  List<AttendanceRecord> findAllByDate(LocalDate date);
  List<AttendanceRecord> findAllByEmployeeIdAndDateBetweenOrderByDateDesc(UUID employeeId, LocalDate from, LocalDate to);
  List<AttendanceRecord> findAllByEmployeeIdAndDateBetween(UUID employeeId,LocalDate from,LocalDate to);
}
