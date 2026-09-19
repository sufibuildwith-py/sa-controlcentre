package com.saproduction.command.calendar;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CalendarEventRepository extends JpaRepository<CalendarEvent,UUID>{
  List<CalendarEvent> findAllByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(Instant to,Instant from);
  Optional<CalendarEvent> findByProductionId(UUID id);
  Optional<CalendarEvent> findByMeetingId(UUID id);
  Optional<CalendarEvent> findByTaskId(UUID id);
}
