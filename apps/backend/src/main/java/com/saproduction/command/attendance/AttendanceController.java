package com.saproduction.command.attendance;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AttendanceController {
  private final AttendanceService service; public AttendanceController(AttendanceService service){this.service=service;}
  @GetMapping("/attendance") public ApiEnvelope<AttendanceService.DayView> day(@RequestParam LocalDate date){return ApiEnvelope.of(service.day(date));}
  @PutMapping("/attendance/{employeeId}/{date}") public ApiEnvelope<AttendanceService.RecordView> put(@PathVariable UUID employeeId,@PathVariable LocalDate date,@Valid @RequestBody AttendanceService.Input input){return ApiEnvelope.of(service.put(employeeId,date,input));}
  @PostMapping("/attendance/{date}/mark-remaining-present") public ApiEnvelope<Map<String,Integer>> remaining(@PathVariable LocalDate date){return ApiEnvelope.of(service.markRemaining(date));}
  @GetMapping("/employees/{employeeId}/attendance") public ApiEnvelope<AttendanceService.MonthView> history(@PathVariable UUID employeeId,@RequestParam(required=false) Integer year,@RequestParam(required=false) Integer month){LocalDate anchor=LocalDate.of(year==null?LocalDate.now().getYear():year,month==null?LocalDate.now().getMonthValue():month,1);return ApiEnvelope.of(service.history(employeeId,anchor,anchor.with(TemporalAdjusters.lastDayOfMonth())));}
}

