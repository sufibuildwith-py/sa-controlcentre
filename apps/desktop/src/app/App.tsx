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
export function App(){const me=useQuery({queryKey:['auth','me'],queryFn:()=>api<Owner>('/auth/me'),retry:false});if(me.isPending)return <div className="auth-loading"><SkeletonCard/></div>;if(me.isError)return <LoginPage/>;return <AppShell><Routes><Route path="/" element={<CommandPage/>}/><Route path="/people" element={<PeoplePage/>}/><Route path="/people/:id" element={<EmployeeDetailPage/>}/><Route path="/attendance" element={<AttendancePage/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></AppShell>}

