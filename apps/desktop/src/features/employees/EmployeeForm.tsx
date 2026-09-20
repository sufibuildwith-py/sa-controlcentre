import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useUiStore } from "../../app/store/ui";
import {
  FormField,
  SAButton,
  SAModal,
  SAStatefulButton,
} from "../../components/ui/sa";
import { useSAToast } from "../../components/ui/toast";
import { api, json, ApiError } from "../../lib/api";
import type { Employee, EmployeeInput } from "../../types/domain";
import { employeeFormSchema, type EmployeeFormValues } from "./employeeSchema";
const defaults: EmployeeFormValues = {
  employeeCode: "",
  firstName: "",
  lastName: "",
  displayName: "",
  phone: "",
  whatsappPhone: "",
  email: "",
  roleTitle: "",
  department: "Production",
  employmentType: "FULL_TIME",
  joiningDate: new Date().toISOString().slice(0, 10),
  baseSalaryRupees: 0,
  salaryCurrency: "INR",
  status: "ACTIVE",
  profilePhotoUrl: "",
  notes: "",
};
export function EmployeeForm() {
  const open = useUiStore((s) => s.employeeFormOpen),
    id = useUiStore((s) => s.editingEmployeeId),
    close = useUiStore((s) => s.closeEmployeeForm);
  const client = useQueryClient();
  const notify = useSAToast();
  const employee = useQuery({
    queryKey: ["employee", id],
    queryFn: () => api<Employee>(`/employees/${id}`),
    enabled: !!id,
  });
  const form = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeFormSchema),
    defaultValues: defaults,
  });
  useEffect(() => {
    if (!open) return;
    if (employee.data) form.reset(fromEmployee(employee.data));
    else if (!id) form.reset(defaults);
  }, [open, id, employee.data, form]);
  const save = useMutation({
    mutationFn: (values: EmployeeFormValues) => {
      const input: EmployeeInput = {
        employeeCode: values.employeeCode,
        firstName: values.firstName,
        lastName: values.lastName || null,
        displayName: values.displayName,
        phone: values.phone,
        whatsappPhone: values.whatsappPhone || null,
        email: values.email || null,
        roleTitle: values.roleTitle,
        department: values.department,
        employmentType: values.employmentType,
        joiningDate: values.joiningDate,
        baseSalaryMinor: Math.round(values.baseSalaryRupees * 100),
        salaryCurrency: values.salaryCurrency,
        status: values.status,
        profilePhotoUrl: values.profilePhotoUrl || null,
        notes: values.notes || null,
      };
      return api<Employee>(id ? `/employees/${id}` : "/employees", {
        method: id ? "PATCH" : "POST",
        ...json(input),
      });
    },
    onSuccess: (saved) => {
      client.invalidateQueries({ queryKey: ["employees"] });
      client.setQueryData(["employee", saved.id], saved);
      notify({
        title: id ? "Employee updated" : "Employee added",
        description: `${saved.displayName} is ready in People and Attendance.`,
      });
      close();
    },
  });
  const fields = save.error instanceof ApiError ? save.error.fields : {};
  return (
    <SAModal
      open={open}
      onOpenChange={(v) => !v && close()}
      title={id ? "Edit employee" : "Add employee"}
      description="People data is persisted to PostgreSQL and becomes available across attendance and search."
    >
      <form
        className="form-grid"
        onSubmit={form.handleSubmit((v) => save.mutate(v))}
      >
        <FormField
          label="Employee code"
          error={
            form.formState.errors.employeeCode?.message ?? fields.employeeCode
          }
        >
          <input {...form.register("employeeCode")} />
        </FormField>
        <FormField
          label="Joining date"
          error={form.formState.errors.joiningDate?.message}
        >
          <input type="date" {...form.register("joiningDate")} />
        </FormField>
        <FormField
          label="First name"
          error={form.formState.errors.firstName?.message}
        >
          <input {...form.register("firstName")} />
        </FormField>
        <FormField
          label="Last name"
          error={form.formState.errors.lastName?.message}
        >
          <input {...form.register("lastName")} />
        </FormField>
        <FormField
          label="Display name"
          error={form.formState.errors.displayName?.message}
        >
          <input {...form.register("displayName")} />
        </FormField>
        <FormField
          label="Role title"
          error={form.formState.errors.roleTitle?.message}
        >
          <input {...form.register("roleTitle")} />
        </FormField>
        <FormField
          label="Department"
          error={form.formState.errors.department?.message}
        >
          <input {...form.register("department")} />
        </FormField>
        <FormField label="Employment type">
          <select {...form.register("employmentType")}>
            <option value="FULL_TIME">Full time</option>
            <option value="PART_TIME">Part time</option>
            <option value="CONTRACT">Contract</option>
          </select>
        </FormField>
        <FormField label="Phone" error={form.formState.errors.phone?.message}>
          <input {...form.register("phone")} />
        </FormField>
        <FormField
          label="WhatsApp phone"
          error={form.formState.errors.whatsappPhone?.message}
        >
          <input {...form.register("whatsappPhone")} />
        </FormField>
        <FormField label="Email" error={form.formState.errors.email?.message}>
          <input type="email" {...form.register("email")} />
        </FormField>
        <FormField label="Status">
          <select {...form.register("status")}>
            <option value="ACTIVE">Active</option>
            <option value="ON_LEAVE">On leave</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </FormField>
        <FormField
          label="Base salary (₹)"
          error={form.formState.errors.baseSalaryRupees?.message}
        >
          <input
            type="number"
            step="1"
            {...form.register("baseSalaryRupees")}
          />
        </FormField>
        <FormField label="Currency">
          <input maxLength={3} {...form.register("salaryCurrency")} />
        </FormField>
        <FormField label="Notes" error={form.formState.errors.notes?.message}>
          <textarea {...form.register("notes")} />
        </FormField>
        <FormField
          label="Profile photo URL"
          error={form.formState.errors.profilePhotoUrl?.message}
        >
          <textarea {...form.register("profilePhotoUrl")} />
        </FormField>
        {save.error && (
          <p className="form-error span-2">{save.error.message}</p>
        )}
        <div className="modal-actions span-2">
          <SAButton type="button" onClick={close}>
            Cancel
          </SAButton>
          <SAStatefulButton
            type="submit"
            variant="primary"
            pending={save.isPending}
          >
            {id ? "Save changes" : "Add employee"}
          </SAStatefulButton>
        </div>
      </form>
    </SAModal>
  );
}
function fromEmployee(e: Employee): EmployeeFormValues {
  return {
    employeeCode: e.employeeCode,
    firstName: e.firstName,
    lastName: e.lastName ?? "",
    displayName: e.displayName,
    phone: e.phone,
    whatsappPhone: e.whatsappPhone ?? "",
    email: e.email ?? "",
    roleTitle: e.roleTitle,
    department: e.department,
    employmentType: e.employmentType,
    joiningDate: e.joiningDate,
    baseSalaryRupees: e.baseSalaryMinor / 100,
    salaryCurrency: "INR",
    status: e.status,
    profilePhotoUrl: e.profilePhotoUrl ?? "",
    notes: e.notes ?? "",
  };
}
