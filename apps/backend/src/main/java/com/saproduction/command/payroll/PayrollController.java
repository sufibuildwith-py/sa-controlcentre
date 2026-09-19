package com.saproduction.command.payroll;
import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/payroll")
public class PayrollController {
  private final PayrollService service;public PayrollController(PayrollService service){this.service=service;}
  @GetMapping public ApiEnvelope<List<PayrollService.View>> list(){return ApiEnvelope.of(service.list());}
  @GetMapping("/{id}") public ApiEnvelope<PayrollService.View> get(@PathVariable UUID id){return ApiEnvelope.of(service.get(id));}
  @PostMapping("/{year}/{month}/calculate") public ApiEnvelope<PayrollService.View> calculate(@PathVariable int year,@PathVariable int month){return ApiEnvelope.of(service.calculate(year,month));}
  @PostMapping("/{id}/adjustments") public ApiEnvelope<PayrollService.View> adjust(@PathVariable UUID id,@Valid @RequestBody PayrollService.AdjustmentInput input){return ApiEnvelope.of(service.adjust(id,input));}
  @PostMapping("/{id}/approve") public ApiEnvelope<PayrollService.View> approve(@PathVariable UUID id){return ApiEnvelope.of(service.approve(id));}
  @PostMapping("/{id}/items/{itemId}/mark-paid") public ApiEnvelope<PayrollService.View> paid(@PathVariable UUID id,@PathVariable UUID itemId){return ApiEnvelope.of(service.markPaid(id,itemId));}
  @PostMapping("/{id}/mark-all-paid") public ApiEnvelope<PayrollService.View> allPaid(@PathVariable UUID id){return ApiEnvelope.of(service.markAllPaid(id));}
  @PostMapping("/{id}/lock") public ApiEnvelope<PayrollService.View> lock(@PathVariable UUID id){return ApiEnvelope.of(service.lock(id));}
}
