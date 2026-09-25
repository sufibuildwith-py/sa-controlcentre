package com.saproduction.command.finance;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/workbook/commercial")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookCommercialController {
  private final FinanceWorkbookCommercialService workbook;
  public FinanceWorkbookCommercialController(FinanceWorkbookCommercialService workbook) { this.workbook = workbook; }

  @GetMapping("/gst/summary") public Map<String,Object> gstSummary() { return workbook.gstSummary(); }
  @GetMapping("/gst/invoices") public Map<String,Object> invoices(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return workbook.invoices(search,status,page,size); }
  @GetMapping("/gst/invoices/{id}") public Map<String,Object> invoice(@PathVariable UUID id) { return workbook.invoice(id); }
  @GetMapping("/purchases/summary") public Map<String,Object> purchaseSummary() { return workbook.purchaseSummary(); }
  @GetMapping("/purchases") public Map<String,Object> purchases(@RequestParam(defaultValue="ALL") String book,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return workbook.purchases(book,search,page,size); }
  @GetMapping("/purchases/{id}") public Map<String,Object> purchase(@PathVariable UUID id) { return workbook.purchase(id); }
  @GetMapping("/accessories") public Map<String,Object> accessories(@RequestParam(defaultValue="ALL") String kind,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return workbook.accessories(kind,search,page,size); }
}
