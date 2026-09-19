import {Moon,Sun} from 'lucide-react';
import {useUiStore,type Theme} from '../../app/store/ui';
import {SASegmentedControl} from '../ui/sa';
export function ThemeSwitch(){const theme=useUiStore(s=>s.theme),setTheme=useUiStore(s=>s.setTheme);return <div className="theme-switch"><div><strong>Appearance</strong><span>Geometry stays identical in both themes.</span></div><SASegmentedControl<Theme> value={theme} onChange={setTheme} label="Theme" items={[{value:'pearl',label:'Pearl'},{value:'charcoal',label:'Charcoal'}]}/><div className="theme-preview" aria-hidden="true"><Sun size={14}/><Moon size={14}/></div></div>}

