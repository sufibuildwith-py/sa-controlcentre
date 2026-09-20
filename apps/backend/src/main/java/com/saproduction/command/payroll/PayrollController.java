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
  @GetMapping("/{id}/items/{itemId}/payments") public ApiEnvelope<PayrollService.ItemView> payments(@PathVariable UUID id,@PathVariable UUID itemId){return ApiEnvelope.of(service.paymentHistory(id,itemId));}
  @PostMapping("/{id}/items/{itemId}/payments") public ApiEnvelope<PayrollService.View> payment(@PathVariable UUID id,@PathVariable UUID itemId,@Valid @RequestBody PayrollService.PaymentInput input){return ApiEnvelope.of(service.recordPayment(id,itemId,input));}
  @PostMapping("/{id}/lock") public ApiEnvelope<PayrollService.View> lock(@PathVariable UUID id){return ApiEnvelope.of(service.lock(id));}
}
