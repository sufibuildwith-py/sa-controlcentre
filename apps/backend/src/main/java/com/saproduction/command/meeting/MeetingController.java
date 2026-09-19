package com.saproduction.command.meeting;
import com.saproduction.command.shared.ApiEnvelope;
import com.saproduction.command.work.WorkTaskService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/meetings")
public class MeetingController {
  private final MeetingService service;public MeetingController(MeetingService service){this.service=service;}
  @GetMapping public ApiEnvelope<List<MeetingService.View>> list(){return ApiEnvelope.of(service.list());}
  @GetMapping("/{id}") public ApiEnvelope<MeetingService.View> get(@PathVariable UUID id){return ApiEnvelope.of(service.get(id));}
  @PostMapping public ApiEnvelope<MeetingService.View> create(@Valid @RequestBody MeetingService.Input input){return ApiEnvelope.of(service.create(input));}
  @PatchMapping("/{id}") public ApiEnvelope<MeetingService.View> update(@PathVariable UUID id,@Valid @RequestBody MeetingService.Input input){return ApiEnvelope.of(service.update(id,input));}
  @PostMapping("/{id}/cancel") public ApiEnvelope<MeetingService.View> cancel(@PathVariable UUID id){return ApiEnvelope.of(service.cancel(id));}
  @PostMapping("/{id}/notes") public ApiEnvelope<MeetingService.View> note(@PathVariable UUID id,@Valid @RequestBody MeetingService.NoteInput input){return ApiEnvelope.of(service.addNote(id,input));}
  @PostMapping("/{id}/actions") public ApiEnvelope<WorkTaskService.View> action(@PathVariable UUID id,@Valid @RequestBody MeetingService.ActionInput input){return ApiEnvelope.of(service.action(id,input));}
  @PatchMapping("/{id}/attendees/{employeeId}") public ApiEnvelope<MeetingService.View> attendeeResponse(@PathVariable UUID id,@PathVariable UUID employeeId,@Valid @RequestBody MeetingService.ResponseInput input){return ApiEnvelope.of(service.updateAttendeeResponse(id,employeeId,input));}
}
