import { z } from "zod";
export const employeeFormSchema = z.object({
  employeeCode: z.string().min(2, "Employee code is required").max(32),
  firstName: z.string().min(1, "First name is required").max(80),
  lastName: z.string().max(80),
  displayName: z.string().min(1, "Display name is required").max(160),
  phone: z.string().regex(/^[+0-9 ()-]{7,24}$/, "Enter a valid phone number"),
  whatsappPhone: z
    .string()
    .refine(
      (v) => !v || /^[+0-9 ()-]{7,24}$/.test(v),
      "Enter a valid WhatsApp number",
    ),
  email: z
    .string()
    .refine(
      (v) => !v || z.string().email().safeParse(v).success,
      "Enter a valid email",
    ),
  roleTitle: z.string().min(1, "Role is required"),
  department: z.string().min(1, "Department is required"),
  employmentType: z.string().min(1),
  joiningDate: z.string().min(1),
  baseSalaryRupees: z.coerce
    .number()
    .min(0, "Salary cannot be negative")
    .max(10_000_000),
  salaryCurrency: z.literal("INR"),
  status: z.enum(["ACTIVE", "ON_LEAVE", "INACTIVE"]),
  profilePhotoUrl: z.string(),
  notes: z.string().max(4000),
});
export type EmployeeFormValues = z.infer<typeof employeeFormSchema>;
