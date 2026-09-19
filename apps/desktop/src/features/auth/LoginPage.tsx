import {zodResolver} from '@hookform/resolvers/zod';
import {useMutation,useQueryClient} from '@tanstack/react-query';
import {Command,LockKeyhole} from 'lucide-react';
import {useForm} from 'react-hook-form';
import {z} from 'zod';
import {api,json,ApiError} from '../../lib/api';
import type {Owner} from '../../types/domain';
import {FormField,SAStatefulButton} from '../../components/ui/sa';
const schema=z.object({email:z.string().email(),password:z.string().min(8)});type Values=z.infer<typeof schema>;
export function LoginPage(){const client=useQueryClient();const form=useForm<Values>({resolver:zodResolver(schema),defaultValues:{email:'owner@saproduction.local',password:'SADemo!2026'}});const login=useMutation({mutationFn:(values:Values)=>api<Owner>('/auth/login',{method:'POST',...json(values)}),onSuccess:(owner)=>client.setQueryData(['auth','me'],owner)});return <main className="login-page"><section className="login-brand"><div className="brand-seal"><Command size={26}/></div><div><span>SA Production</span><h1>Command</h1><p>A calm control surface for people, attendance and daily operations.</p></div><div className="login-bento" aria-hidden="true"><span/><span/><span/><span/></div></section><section className="login-panel"><div className="login-form-wrap"><div className="login-kicker"><LockKeyhole size={15}/>Owner access</div><h2>Welcome back.</h2><p>Sign in to open today’s operating picture.</p><form onSubmit={form.handleSubmit(v=>login.mutate(v))}><FormField label="Email" error={form.formState.errors.email?.message}><input autoComplete="username" {...form.register('email')}/></FormField><FormField label="Password" error={form.formState.errors.password?.message}><input type="password" autoComplete="current-password" {...form.register('password')}/></FormField>{login.error&&<p className="form-error">{login.error instanceof ApiError?login.error.message:'Unable to sign in.'}</p>}<SAStatefulButton variant="primary" pending={login.isPending} type="submit">Enter SA Command</SAStatefulButton></form><small>Demo access is prefilled for this development build.</small></div></section></main>}

