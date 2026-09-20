import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { PayrollPeriod } from "../../types/domain";
import { PayrollPage } from "./PayrollPage";

const apiMock = vi.fn();
vi.mock("../../lib/api", () => ({ api: (...args: unknown[]) => apiMock(...args), json: (body: unknown) => ({ headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }) }));
afterEach(() => { cleanup(); apiMock.mockReset(); });

const fixture = (locked = false): PayrollPeriod => ({
  id:"period-1",year:2026,month:9,status:locked?"LOCKED":"APPROVED",policy:"PER_WORKING_DAY",totalMinor:4_000_000,paidMinor:1_500_000,pendingMinor:2_500_000,remainingMinor:2_500_000,paidCount:0,partiallyPaidCount:1,unpaidCount:0,calculatedAt:"2026-09-01T00:00:00Z",approvedAt:"2026-09-02T00:00:00Z",paidAt:null,lockedAt:locked?"2026-09-30T00:00:00Z":null,
  items:[{id:"item-1",employeeId:"employee-1",employeeName:"Amaan Khan",employeeRole:"Senior Video Editor",salaryCurrency:"INR",baseSalaryMinor:4_200_000,attendanceDeductionMinor:0,overtimeMinor:0,bonusMinor:300_000,advanceDeductionMinor:500_000,manualAdjustmentMinor:0,grossEarnings:4_500_000,deductions:500_000,netSalaryMinor:4_000_000,netPayable:4_000_000,totalPaid:1_500_000,remaining:2_500_000,paymentStatus:"PARTIALLY_PAID",paidAt:null,lastPayment:{id:"payment-1",amountMinor:1_500_000,paidAt:"2026-09-18T10:00:00Z",paymentMethod:"UPI",reference:"UPI-492821",note:"First part",createdAt:"2026-09-18T10:00:00Z"},payments:[{id:"payment-1",amountMinor:1_500_000,paidAt:"2026-09-18T10:00:00Z",paymentMethod:"UPI",reference:"UPI-492821",note:"First part",createdAt:"2026-09-18T10:00:00Z"}],adjustments:[{id:"adjustment-1",type:"ADVANCE",amountMinor:500_000,reason:"Salary advance already received",createdAt:"2026-09-05T10:00:00Z"}]}]
});

function renderPayroll(data:PayrollPeriod){apiMock.mockImplementation((path:string)=>Promise.resolve(path==="/payroll"?[data]:data));const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[`/payroll/${data.id}`]}><Routes><Route path="/payroll/:id" element={<PayrollPage/>}/></Routes></MemoryRouter></QueryClientProvider>);}

describe("payroll ledger",()=>{
  it("shows partial state, payment modal defaults, and ledger timelines",async()=>{renderPayroll(fixture());const user=userEvent.setup();expect(await screen.findByText("PARTIALLY PAID")).toBeInTheDocument();await user.click(screen.getAllByRole("button",{name:"Record payment"})[0]);expect(screen.getByLabelText("Payment amount")).toHaveValue(25000);await user.keyboard("{Escape}");await user.click(screen.getByRole("button",{name:"View details"}));expect(screen.getByText("UPI-492821",{exact:false})).toBeInTheDocument();expect(screen.getByText("Salary advance already received")).toBeInTheDocument();});
  it("keeps locked history readable and removes mutation actions",async()=>{renderPayroll(fixture(true));const user=userEvent.setup();expect(await screen.findByText("Immutable financial history")).toBeInTheDocument();expect(screen.queryByRole("button",{name:"Record payment"})).not.toBeInTheDocument();await user.click(screen.getByRole("button",{name:"View details"}));expect(screen.getByText("Payment history")).toBeInTheDocument();expect(screen.queryByRole("button",{name:"Add adjustment"})).not.toBeInTheDocument();});
});
