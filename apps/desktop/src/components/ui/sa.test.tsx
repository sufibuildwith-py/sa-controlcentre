import {afterEach,describe,expect,it} from 'vitest';
import {cleanup,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {useState} from 'react';
import {SADrawer,SAModal} from './sa';

afterEach(cleanup);

describe('overlay lifecycle',()=>{
  for(const [label,Component] of [['modal',SAModal],['drawer',SADrawer]] as const){
    it(`${label} dismisses with Escape and removes its focus trap`,async()=>{
      function Harness(){const [open,setOpen]=useState(false);return <><button onClick={()=>setOpen(true)}>Open overlay</button><Component open={open} onOpenChange={setOpen} title="Employee action" description="Review the employee."><input aria-label="Owner note"/></Component><button>Next action</button></>}
      const user=userEvent.setup();render(<Harness/>);await user.click(screen.getByText('Open overlay'));
      expect(screen.getByRole('dialog')).toHaveAccessibleDescription('Review the employee.');
      await user.keyboard('{Escape}');await waitFor(()=>expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(document.querySelector('.modal-overlay')).toBeNull();await user.click(screen.getByText('Next action'));expect(screen.getByText('Next action')).toHaveFocus();
    });
  }
});
