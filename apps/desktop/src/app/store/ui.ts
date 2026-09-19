import {create} from 'zustand';
import {persist} from 'zustand/middleware';
export type Theme='pearl'|'charcoal';
interface UiState{theme:Theme;paletteOpen:boolean;quickCreateOpen:boolean;employeeFormOpen:boolean;editingEmployeeId:string|null;setTheme:(theme:Theme)=>void;setPaletteOpen:(open:boolean)=>void;setQuickCreateOpen:(open:boolean)=>void;openEmployeeForm:(id?:string)=>void;closeEmployeeForm:()=>void}
export const useUiStore=create<UiState>()(persist((set)=>({theme:'pearl',paletteOpen:false,quickCreateOpen:false,employeeFormOpen:false,editingEmployeeId:null,setTheme:(theme)=>set({theme}),setPaletteOpen:(paletteOpen)=>set({paletteOpen}),setQuickCreateOpen:(quickCreateOpen)=>set({quickCreateOpen}),openEmployeeForm:(id)=>set({employeeFormOpen:true,editingEmployeeId:id??null,quickCreateOpen:false}),closeEmployeeForm:()=>set({employeeFormOpen:false,editingEmployeeId:null})}),{name:'sa-command-ui',partialize:(s)=>({theme:s.theme})}));
