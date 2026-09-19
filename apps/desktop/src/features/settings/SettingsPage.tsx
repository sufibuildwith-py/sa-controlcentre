import {ShieldCheck,Wifi} from 'lucide-react';
import {WorkspaceHeader} from '../../components/layout/AppShell';
import {SABentoCard,StatusBadge} from '../../components/ui/sa';
import {ThemeSwitch} from '../../components/layout/ThemeSwitch';
export function SettingsPage(){return <><WorkspaceHeader title="Settings" subtitle="Desktop appearance and application status."/><div className="settings-stack"><SABentoCard><ThemeSwitch/></SABentoCard><SABentoCard><div className="settings-row"><span><Wifi size={17}/><div><strong>Local API</strong><small>Spring Boot · configured desktop origin</small></div></span><StatusBadge tone="success">Connected when signed in</StatusBadge></div></SABentoCard><SABentoCard><div className="settings-row"><span><ShieldCheck size={17}/><div><strong>Session protection</strong><small>HttpOnly server-side session; no credential in localStorage</small></div></span><StatusBadge tone="success">Enabled</StatusBadge></div></SABentoCard></div></>}

