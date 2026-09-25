package com.saproduction.command.config;

import com.saproduction.command.shared.ApiEnvelope;
import com.saproduction.command.finance.FinanceWorkbookDemoLoader;
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
  private final CommandLineRunner phase1, phase2, headquarters;
  private final FinanceWorkbookDemoLoader financeWorkbook;

  public DemoResetController(
      JdbcTemplate jdbc,
      @Qualifier("demoData") CommandLineRunner phase1,
      @Qualifier("phase2DemoData") CommandLineRunner phase2,
      @Qualifier("headquartersDemo") CommandLineRunner headquarters,
      FinanceWorkbookDemoLoader financeWorkbook) {
    this.jdbc = jdbc;
    this.phase1 = phase1;
    this.phase2 = phase2;
    this.headquarters = headquarters;
    this.financeWorkbook = financeWorkbook;
  }

  @PostMapping("/reset")
  public synchronized ApiEnvelope<Map<String, Object>> reset() throws Exception {
    jdbc.execute("TRUNCATE TABLE finance_migration_batches,finance_reconciliation_snapshots,finance_projection_checkpoints,finance_audit_events,finance_counterparties,finance_production_profiles,finance_employee_profiles,finance_employee_obligations,finance_counterparty_charges,finance_invoices,finance_equipment_purchases,finance_transactions CASCADE");
    jdbc.update("UPDATE finance_account_positions SET position=0,version=version+1,updated_at=now()");
    jdbc.execute(
        "truncate table hq_attention,hq_maintenance,hq_issues,hq_return_lines,hq_returns,hq_transfer_lines,hq_transfers,hq_dispatch_lines,hq_dispatches,hq_reservation_lines,hq_reservations,hq_inventory_movements,hq_inventory_positions,hq_serialized_assets,hq_equipment,hq_locations,hq_units,hq_categories,attendance_records,leave_requests,production_members,event_attendees,calendar_events,task_updates,tasks,meeting_notes,meeting_attendees,meetings,payroll_payments,payroll_adjustments,payroll_items,payroll_periods,productions,outbound_message_events,outbound_messages,outbox_events,webhook_receipts,notification_rules,audit_logs,employees restart identity cascade");
    jdbc.update(
        "insert into notification_rules(event_type,delay_minutes,template_key) values ('PRODUCTION_ASSIGNED',0,'sa_production_assignment'),('PRODUCTION_UPDATED',0,'sa_schedule_changed'),('EVENT_CHANGED',0,'sa_schedule_changed'),('EVENT_REMINDER_24H',1440,'sa_event_reminder'),('EVENT_REMINDER_2H',120,'sa_event_reminder'),('TASK_ASSIGNED',0,'sa_task_assigned'),('TASK_DUE_24H',1440,'sa_task_due'),('MEETING_CREATED',0,'sa_meeting_invitation'),('MEETING_UPDATED',0,'sa_meeting_invitation'),('MEETING_REMINDER',120,'sa_meeting_reminder'),('ATTENDANCE_MISSING',0,'sa_attendance_missing'),('LEAVE_APPROVED',0,'sa_leave_status'),('LEAVE_REJECTED',0,'sa_leave_status'),('SALARY_PROCESSED',0,'sa_salary_processed'),('MANUAL_NOTICE',0,'sa_manual_notice')");
    phase1.run();
    phase2.run();
    headquarters.run();
    financeWorkbook.load();
    return ApiEnvelope.of(
        Map.of(
            "reset",
            true,
            "employees",
            jdbc.queryForObject("select count(*) from employees", Long.class),
            "productions",
            jdbc.queryForObject("select count(*) from productions", Long.class),
            "equipment",
            jdbc.queryForObject("select count(*) from hq_equipment", Long.class)));
  }
}
