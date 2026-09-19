package com.saproduction.command.calendar;
import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/calendar-events")
public class CalendarController {
  private final CalendarService service;public CalendarController(CalendarService service){this.service=service;}
  @GetMapping public ApiEnvelope<List<CalendarService.View>> list(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ApiEnvelope.of(service.list(from,to));}
  @GetMapping("/{id}") public ApiEnvelope<CalendarService.View> get(@PathVariable UUID id){return ApiEnvelope.of(service.get(id));}
  @PostMapping public ApiEnvelope<CalendarService.View> create(@Valid @RequestBody CalendarService.Input input){return ApiEnvelope.of(service.create(input));}
  @PatchMapping("/{id}") public ApiEnvelope<CalendarService.View> update(@PathVariable UUID id,@Valid @RequestBody CalendarService.Input input){return ApiEnvelope.of(service.update(id,input));}
}
