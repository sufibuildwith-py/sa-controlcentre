import {describe,expect,it} from 'vitest';
import {messageTone} from './CommunicationsPage';
describe('communications status treatment',()=>{it('makes delivered evidence positive',()=>{expect(messageTone('DELIVERED')).toBe('success');expect(messageTone('READ')).toBe('success')});it('keeps failures and queued work visually distinct',()=>{expect(messageTone('FAILED')).toBe('danger');expect(messageTone('QUEUED')).toBe('warning');expect(messageTone('SENT')).toBe('neutral')})});
