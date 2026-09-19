import {CalendarPlus,ClipboardPlus,MessageSquarePlus,Plus,UserPlus,Video,WalletCards} from 'lucide-react';
import {useUiStore} from '../../app/store/ui';
import {SAModal} from '../ui/sa';
const items=[{label:'Add employee',icon:UserPlus,enabled:true},{label:'Create production',icon:Video},{label:'Create task',icon:ClipboardPlus},{label:'Schedule event',icon:CalendarPlus},{label:'Payroll adjustment',icon:WalletCards},{label:'Send message',icon:MessageSquarePlus}];
export function QuickCreate(){const open=useUiStore(s=>s.quickCreateOpen),setOpen=useUiStore(s=>s.setQuickCreateOpen),openEmployee=useUiStore(s=>s.openEmployeeForm);return <SAModal open={open} onOpenChange={setOpen} title="Quick create" description="Start an operation without leaving your current view."><div className="quick-create-list">{items.map(item=>{const Icon=item.icon;return <button key={item.label} disabled={!item.enabled} onClick={()=>item.enabled&&openEmployee()}><span><Icon size={17}/></span><div><strong>{item.label}</strong>{!item.enabled&&<small>Later V1 phase</small>}</div><Plus size={15}/></button>})}</div></SAModal>}

