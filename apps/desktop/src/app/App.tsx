import {useQuery} from '@tanstack/react-query';
import {Navigate,Route,Routes} from 'react-router-dom';
import {AppShell} from '../components/layout/AppShell';
import {SkeletonCard} from '../components/ui/sa';
import {api} from '../lib/api';
import type {Owner} from '../types/domain';
import {LoginPage} from '../features/auth/LoginPage';
import {CommandPage} from '../features/command/CommandPage';
import {PeoplePage} from '../features/employees/PeoplePage';
import {EmployeeDetailPage} from '../features/employees/EmployeeDetailPage';
import {AttendancePage} from '../features/attendance/AttendancePage';
import {SettingsPage} from '../features/settings/SettingsPage';
import {ProductionsPage} from '../features/productions/ProductionsPage';
import {ProductionDetailPage} from '../features/productions/ProductionDetailPage';
import {WorkPage} from '../features/work/WorkPage';
import {CalendarPage} from '../features/calendar/CalendarPage';
import {MeetingsPage,MeetingDetailPage} from '../features/meetings/MeetingsPage';
import {PayrollPage} from '../features/payroll/PayrollPage';
export function App(){const me=useQuery({queryKey:['auth','me'],queryFn:()=>api<Owner>('/auth/me'),retry:false});if(me.isPending)return <div className="auth-loading"><SkeletonCard/></div>;if(me.isError)return <LoginPage/>;return <AppShell><Routes><Route path="/" element={<CommandPage/>}/><Route path="/people" element={<PeoplePage/>}/><Route path="/people/:id" element={<EmployeeDetailPage/>}/><Route path="/attendance" element={<AttendancePage/>}/><Route path="/productions" element={<ProductionsPage/>}/><Route path="/productions/:id" element={<ProductionDetailPage/>}/><Route path="/work" element={<WorkPage/>}/><Route path="/calendar" element={<CalendarPage/>}/><Route path="/meetings" element={<MeetingsPage/>}/><Route path="/meetings/:id" element={<MeetingDetailPage/>}/><Route path="/payroll" element={<PayrollPage/>}/><Route path="/payroll/:id" element={<PayrollPage/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></AppShell>}
