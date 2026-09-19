import {motion} from 'motion/react';
import {Bell,CalendarDays,CheckSquare,Command,Home,MessageCircle,Plus,Search,Settings} from 'lucide-react';
import {NavLink,useLocation,useNavigate} from 'react-router-dom';
import {forwardRef,useEffect,useRef,type PropsWithChildren} from 'react';
import {useUiStore} from '../../app/store/ui';
import {SAIconButton,SearchField,Tooltip,appSpring} from '../ui/sa';
import {QuickCreate} from './QuickCreate';
import {SACommandPalette} from './SACommandPalette';
import {EmployeeForm} from '../../features/employees/EmployeeForm';

const domains=[{label:'Overview',to:'/'},{label:'People',to:'/people'},{label:'Productions',to:'/productions'},{label:'Calendar',to:'/calendar'},{label:'Finance',to:'/payroll'}];
const dock=[{label:'Command',to:'/',icon:Home},{label:'Attendance',to:'/attendance',icon:CalendarDays},{label:'Work',to:'/work',icon:CheckSquare},{label:'Meetings',to:'/meetings',icon:MessageCircle},{label:'Search',icon:Search,action:'search'},{label:'Settings',to:'/settings',icon:Settings}];
export function AppShell({children}:PropsWithChildren){const theme=useUiStore(s=>s.theme);const setPaletteOpen=useUiStore(s=>s.setPaletteOpen);const location=useLocation();const navigate=useNavigate();const workspace=useRef<HTMLElement>(null);
  useEffect(()=>{document.documentElement.dataset.theme=theme;document.querySelector('meta[name=theme-color]')?.setAttribute('content',theme==='pearl'?'#ecece8':'#1e1e1e')},[theme]);
  useEffect(()=>{const onKey=(e:KeyboardEvent)=>{if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){e.preventDefault();setPaletteOpen(true)}};window.addEventListener('keydown',onKey);return()=>window.removeEventListener('keydown',onKey)},[setPaletteOpen]);
  useEffect(()=>{workspace.current?.scrollTo({top:0,left:0})},[location.pathname]);
  return <div className="app-frame"><div className="app-shell">
    <FloatingDomainSwitcher/>
    <OperationalDock/>
    <WorkspaceShell ref={workspace}>{children}</WorkspaceShell>
    <QuickCreate/><SACommandPalette onNavigate={navigate}/><EmployeeForm/>
  </div></div>}

export function FloatingDomainSwitcher(){const location=useLocation();const setQuickCreateOpen=useUiStore(s=>s.setQuickCreateOpen);return <nav className="top-switcher" aria-label="Business domains">{domains.map(item=><NavLink key={item.label} to={item.to} className={({isActive})=>isActive&&(item.to!=='/'||location.pathname==='/')?'active':''}>{({isActive})=><>{isActive&&(item.to!=='/'||location.pathname==='/')&&<motion.span layoutId="domain-switcher" className="switcher-active" transition={appSpring}/>}<span>{item.label}</span></>}</NavLink>)}<button className="create" aria-label="Quick create" onClick={()=>setQuickCreateOpen(true)}><Plus size={17}/></button></nav>}

export function OperationalDock(){const setPaletteOpen=useUiStore(s=>s.setPaletteOpen);return <nav className="operational-dock" aria-label="Operational shortcuts"><div className="brand-mark"><Command size={18}/></div><div className="dock-divider"/>{dock.map(item=>{const Icon=item.icon;if(item.action==='search')return <Tooltip key={item.label} content={`${item.label} · Ctrl/Cmd K`}><button onClick={()=>setPaletteOpen(true)} aria-label={item.label}><Icon size={18}/></button></Tooltip>;return <Tooltip key={item.label} content={item.label}><NavLink to={item.to!} className={({isActive})=>isActive?'active':''} aria-label={item.label}><Icon size={18}/></NavLink></Tooltip>})}</nav>}

// Routes consume live router context. Retaining an exiting route tree can render
// the next route inside the old opacity animation. A keyed entrance-only wrapper
// keeps one live route, with a visible resting state independent of animation.
export const WorkspaceShell=forwardRef<HTMLElement,PropsWithChildren>(({children},ref)=>{const location=useLocation();return <main ref={ref} className="workspace-shell"><div key={location.pathname} className="workspace-page">{children}</div></main>});
WorkspaceShell.displayName='WorkspaceShell';

export function WorkspaceHeader({title,subtitle,owner='Owner'}:{title:string;subtitle?:string;owner?:string}){const setPaletteOpen=useUiStore(s=>s.setPaletteOpen);return <header className="workspace-header"><div><h1>{title}</h1>{subtitle&&<p>{subtitle}</p>}</div><div className="header-actions"><SearchField aria-label="Search SA Command" placeholder="Search" onFocus={()=>setPaletteOpen(true)} readOnly/><SAIconButton label="Notifications"><Bell size={17}/></SAIconButton><div className="owner-profile" aria-label={`${owner} profile`}><div className="owner-avatar">{owner.slice(0,2).toUpperCase()}</div><span><strong>{owner}</strong><small>SA Production</small></span></div></div></header>}
