package com.saproduction.command.finance;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
  private final FinancePostingService posting;
  private final FinanceReadService reads;

  public FinanceController(FinancePostingService posting, FinanceReadService reads) {
    this.posting = posting;
    this.reads = reads;
  }

  @GetMapping("/overview") public ApiEnvelope<Map<String,Object>> overview() { return ApiEnvelope.of(reads.overview()); }
  @GetMapping("/reconciliation") public ApiEnvelope<Map<String,Object>> reconciliation() { return ApiEnvelope.of(reads.reconciliation()); }
  @PostMapping("/reconciliation/rebuild") public ApiEnvelope<Map<String,Object>> rebuild() { return ApiEnvelope.of(reads.rebuildPositions()); }
  @GetMapping("/config") public ApiEnvelope<Map<String,Object>> config() { return ApiEnvelope.of(reads.config()); }
  @GetMapping("/accounts") public ApiEnvelope<?> accounts() { return ApiEnvelope.of(reads.accounts()); }
  @GetMapping("/accounts/{id}") public ApiEnvelope<?> account(@PathVariable UUID id) { return ApiEnvelope.of(reads.account(id)); }
  @GetMapping("/accounts/{id}/ledger") public ApiEnvelope<?> accountLedger(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return ApiEnvelope.of(reads.accountLedger(id,page,size)); }
  @GetMapping("/transactions") public ApiEnvelope<?> transactions(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String type,@RequestParam(required=false) UUID productionId,@RequestParam(required=false) String search) { return ApiEnvelope.of(reads.transactions(page,size,type,productionId,search)); }
  @GetMapping("/transactions/{id}") public ApiEnvelope<?> transaction(@PathVariable UUID id) { return ApiEnvelope.of(reads.transaction(id)); }
  @GetMapping("/productions") public ApiEnvelope<?> productions(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String search) { return ApiEnvelope.of(reads.productions(page,size,search)); }
  @GetMapping("/productions/{id}") public ApiEnvelope<?> production(@PathVariable UUID id) { return ApiEnvelope.of(reads.production(id)); }
  @GetMapping("/employees/{id}") public ApiEnvelope<?> employee(@PathVariable UUID id) { return ApiEnvelope.of(reads.employee(id)); }
  @GetMapping("/employee-payables") public ApiEnvelope<?> employeePayables(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return ApiEnvelope.of(reads.employeePayables(page,size)); }
  @GetMapping("/counterparties") public ApiEnvelope<?> counterparties(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String search) { return ApiEnvelope.of(reads.counterparties(page,size,search)); }
  @GetMapping("/counterparties/{id}") public ApiEnvelope<?> counterparty(@PathVariable UUID id) { return ApiEnvelope.of(reads.counterparty(id)); }
  @GetMapping("/invoices") public ApiEnvelope<?> invoices(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return ApiEnvelope.of(reads.invoices(page,size)); }
  @GetMapping("/invoices/{id}") public ApiEnvelope<?> invoice(@PathVariable UUID id) { return ApiEnvelope.of(reads.invoice(id)); }
  @GetMapping("/equipment-purchases") public ApiEnvelope<?> purchases(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) { return ApiEnvelope.of(reads.purchases(page,size)); }
  @GetMapping("/equipment-purchases/{id}") public ApiEnvelope<?> purchase(@PathVariable UUID id) { return ApiEnvelope.of(reads.purchase(id)); }

  @PostMapping("/contracts") public ApiEnvelope<?> contract(@Valid @RequestBody FinanceCommands.Contract in) { return ApiEnvelope.of(posting.contract(in)); }
  @PostMapping("/receipts") public ApiEnvelope<?> receipt(@Valid @RequestBody FinanceCommands.Receipt in) { return ApiEnvelope.of(posting.receipt(in)); }
  @PostMapping("/expenses") public ApiEnvelope<?> expense(@Valid @RequestBody FinanceCommands.Expense in) { return ApiEnvelope.of(posting.expense(in)); }
  @PostMapping("/earnings") public ApiEnvelope<?> earning(@Valid @RequestBody FinanceCommands.Earning in) { return ApiEnvelope.of(posting.earning(in)); }
  @PostMapping("/salaries") public ApiEnvelope<?> salary(@Valid @RequestBody FinanceCommands.Salary in) { return ApiEnvelope.of(posting.salary(in)); }
  @PostMapping("/employee-payments") public ApiEnvelope<?> employeePayment(@Valid @RequestBody FinanceCommands.EmployeePayment in) { return ApiEnvelope.of(posting.employeePayment(in)); }
  @PostMapping("/counterparties") public ApiEnvelope<?> counterparty(@Valid @RequestBody FinanceCommands.Counterparty in) { return ApiEnvelope.of(posting.createCounterparty(in)); }
  @PostMapping("/charges") public ApiEnvelope<?> charge(@Valid @RequestBody FinanceCommands.Charge in) { return ApiEnvelope.of(posting.charge(in)); }
  @PostMapping("/party-receipts") public ApiEnvelope<?> partyReceipt(@Valid @RequestBody FinanceCommands.PartyReceipt in) { return ApiEnvelope.of(posting.partyReceipt(in)); }
  @PostMapping("/invoices") public ApiEnvelope<?> invoice(@Valid @RequestBody FinanceCommands.Invoice in) { return ApiEnvelope.of(posting.invoice(in)); }
  @PostMapping("/invoice-payments") public ApiEnvelope<?> invoicePayment(@Valid @RequestBody FinanceCommands.InvoicePayment in) { return ApiEnvelope.of(posting.invoicePayment(in)); }
  @PostMapping("/equipment-purchases") public ApiEnvelope<?> purchase(@Valid @RequestBody FinanceCommands.Purchase in) { return ApiEnvelope.of(posting.purchase(in)); }
  @PostMapping("/equipment-payments") public ApiEnvelope<?> purchasePayment(@Valid @RequestBody FinanceCommands.PurchasePayment in) { return ApiEnvelope.of(posting.purchasePayment(in)); }
  @PostMapping("/owner-credits") public ApiEnvelope<?> ownerCredit(@Valid @RequestBody FinanceCommands.OwnerMovement in) { return ApiEnvelope.of(posting.ownerCredit(in)); }
  @PostMapping("/owner-debits") public ApiEnvelope<?> ownerDebit(@Valid @RequestBody FinanceCommands.OwnerMovement in) { return ApiEnvelope.of(posting.ownerDebit(in)); }
  @PostMapping("/transfers") public ApiEnvelope<?> transfer(@Valid @RequestBody FinanceCommands.OwnerMovement in) { return ApiEnvelope.of(posting.ownerTransfer(in)); }
  @PostMapping("/transactions/{id}/reverse") public ApiEnvelope<?> reverse(@PathVariable UUID id,@Valid @RequestBody FinanceCommands.Reversal in) { return ApiEnvelope.of(posting.reverse(id,in)); }
}

