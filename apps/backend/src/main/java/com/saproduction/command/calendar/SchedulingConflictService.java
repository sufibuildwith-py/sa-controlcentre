package com.saproduction.command.calendar;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SchedulingConflictService {
  public record Conflict(UUID eventId, String title, Instant startsAt, Instant endsAt) {}

  private final JdbcTemplate jdbc;

  public SchedulingConflictService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Conflict> find(UUID employeeId, Instant start, Instant end, UUID excludedEvent) {
    String sql =
        excludedEvent == null
            ? """
      select e.id,e.title,e.starts_at,e.ends_at from calendar_events e join event_attendees a on a.event_id=e.id
      where a.employee_id=? and e.status='SCHEDULED' and e.starts_at < ? and e.ends_at > ? order by e.starts_at
      """
            : """
      select e.id,e.title,e.starts_at,e.ends_at from calendar_events e join event_attendees a on a.event_id=e.id
      where a.employee_id=? and e.status='SCHEDULED' and e.starts_at < ? and e.ends_at > ? and e.id<>? order by e.starts_at
      """;
    Object[] args =
        excludedEvent == null
            ? new Object[] {
              employeeId, java.sql.Timestamp.from(end), java.sql.Timestamp.from(start)
            }
            : new Object[] {
              employeeId,
              java.sql.Timestamp.from(end),
              java.sql.Timestamp.from(start),
              excludedEvent
            };
    return jdbc.query(
        sql,
        (ResultSet r, int n) ->
            new Conflict(
                r.getObject(1, UUID.class),
                r.getString(2),
                r.getTimestamp(3).toInstant(),
                r.getTimestamp(4).toInstant()),
        args);
  }
}
