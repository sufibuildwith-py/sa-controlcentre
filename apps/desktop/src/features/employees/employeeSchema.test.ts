import {describe,expect,it} from 'vitest';
import {employeeFormSchema} from './employeeSchema';
const valid={employeeCode:'SA-100',firstName:'Amaan',lastName:'Khan',displayName:'Amaan Khan',phone:'+91 90000 00000',whatsappPhone:'+91 90000 00000',email:'amaan@sa.local',roleTitle:'Editor',department:'Post Production',employmentType:'FULL_TIME',joiningDate:'2026-01-01',baseSalaryRupees:42000,salaryCurrency:'INR',status:'ACTIVE' as const,profilePhotoUrl:'',notes:''};
describe('employeeFormSchema',()=>{it('accepts production-safe employee values',()=>expect(employeeFormSchema.safeParse(valid).success).toBe(true));it('rejects a negative salary and malformed phone',()=>{const result=employeeFormSchema.safeParse({...valid,baseSalaryRupees:-1,phone:'x'});expect(result.success).toBe(false);if(!result.success)expect(result.error.issues.map(i=>i.path[0])).toEqual(expect.arrayContaining(['baseSalaryRupees','phone']))})});

