package com.saproduction.command.config;

import com.saproduction.command.shared.ApiEnvelope;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo", matchIfMissing = true)
public class DemoResetController {
  private final JdbcTemplate jdbc;
  private final CommandLineRunner phase1, phase2;

  public DemoResetController(
      JdbcTemplate jdbc,
      @Qualifier("demoData") CommandLineRunner phase1,
      @Qualifier("phase2DemoData") CommandLineRunner phase2) {
    this.jdbc = jdbc;
    this.phase1 = phase1;
    this.phase2 = phase2;
  }

  @PostMapping("/reset")
  public synchronized ApiEnvelope<Map<String, Object>> reset() throws Exception {
    jdbc.execute(
        "truncate table attendance_records,leave_requests,production_members,event_attendees,calendar_events,task_updates,tasks,meeting_notes,meeting_attendees,meetings,payroll_payments,payroll_adjustments,payroll_items,payroll_periods,productions,outbound_message_events,outbound_messages,outbox_events,webhook_receipts,notification_rules,audit_logs,employees restart identity cascade");
    jdbc.update(
        "insert into notification_rules(event_type,delay_minutes,template_key) values ('PRODUCTION_ASSIGNED',0,'sa_production_assignment'),('PRODUCTION_UPDATED',0,'sa_schedule_changed'),('EVENT_CHANGED',0,'sa_schedule_changed'),('EVENT_REMINDER_24H',1440,'sa_event_reminder'),('EVENT_REMINDER_2H',120,'sa_event_reminder'),('TASK_ASSIGNED',0,'sa_task_assigned'),('TASK_DUE_24H',1440,'sa_task_due'),('MEETING_CREATED',0,'sa_meeting_invitation'),('MEETING_UPDATED',0,'sa_meeting_invitation'),('MEETING_REMINDER',120,'sa_meeting_reminder'),('ATTENDANCE_MISSING',0,'sa_attendance_missing'),('LEAVE_APPROVED',0,'sa_leave_status'),('LEAVE_REJECTED',0,'sa_leave_status'),('SALARY_PROCESSED',0,'sa_salary_processed'),('MANUAL_NOTICE',0,'sa_manual_notice')");
    phase1.run();
    phase2.run();
    return ApiEnvelope.of(
        Map.of(
            "reset",
            true,
            "employees",
            jdbc.queryForObject("select count(*) from employees", Long.class),
            "productions",
            jdbc.queryForObject("select count(*) from productions", Long.class)));
  }
}
