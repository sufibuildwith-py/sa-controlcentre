import {expect,test,type Page,type Route} from '@playwright/test';
import {mkdir} from 'node:fs/promises';

const fixedNow=new Date('2026-09-18T16:08:00+05:30');
const fixedDate='2026-09-18';
const baseEmployee={id:'11111111-1111-1111-1111-111111111111',employeeCode:'SA-001',firstName:'Amaan',lastName:'Khan',displayName:'Amaan Khan',phone:'+91 90000 00001',whatsappPhone:'+91 90000 00001',email:'amaan@sa.local',roleTitle:'Senior Video Editor',department:'Post Production',employmentType:'FULL_TIME',joiningDate:'2023-01-10',baseSalaryMinor:4200000,salaryCurrency:'INR',status:'ACTIVE',profilePhotoUrl:null,notes:'Lead editor',createdAt:fixedNow.toISOString(),updatedAt:fixedNow.toISOString()};
type MockEmployee=typeof baseEmployee;
type MockLeave={id:string;employeeId:string;employeeName:string;startDate:string;endDate:string;leaveType:string;reason:string;status:'PENDING'|'APPROVED'|'REJECTED';ownerNote:string|null;createdAt:string;resolvedAt:string|null};
type MockState={session:boolean;employees:MockEmployee[];attendance:Record<string,string>;leaves:MockLeave[]};

function state(session=true):MockState{return {session,employees:[{...baseEmployee}],attendance:{[baseEmployee.id]:'PRESENT'},leaves:[]};}
async function fulfill(route:Route,data:unknown,status=200){await route.fulfill({status,contentType:'application/json',body:JSON.stringify(status>=400?{error:{code:'UNAUTHENTICATED',message:'Sign in required',fields:{},traceId:'e2e'}}:{data,meta:{traceId:'e2e'}})});}
async function mockApi(page:Page,mock:MockState){await page.route('**/api/v1/**',async route=>{
  const request=route.request();const method=request.method();const path=new URL(request.url()).pathname;
  if(path.endsWith('/auth/me'))return mock.session?fulfill(route,{id:'owner',email:'owner@sa.local',displayName:'Owner',role:'OWNER'}):fulfill(route,null,401);
  if(path.endsWith('/auth/login')&&method==='POST'){mock.session=true;return fulfill(route,{id:'owner',email:'owner@sa.local',displayName:'Owner',role:'OWNER'});}
  if(path.endsWith('/dashboard')&&method==='GET')return fulfill(route,{team:{employees:1,present:1,late:0,absent:0,leave:0,incomplete:0},today:[{id:'event-1',type:'INTERNAL',title:'Daily operations review',startsAt:fixedNow.toISOString(),endsAt:new Date(fixedNow.getTime()+3600000).toISOString()}],productions:[{id:'production-1',title:'Sharma Wedding',status:'PRODUCTION',progressPercent:78,eventDate:'2026-09-26'}],workload:[{employeeId:baseEmployee.id,employeeName:baseEmployee.displayName,active:2,overdue:0}],payroll:{id:'payroll-1',year:2026,month:9,status:'CALCULATED',totalMinor:4200000,paidMinor:0,pendingMinor:4200000},attention:[{code:'ATTENDANCE_COMPLETE',title:'Attendance is complete',detail:'All active employees have a status.',tone:'neutral',href:'/attendance'}],communicationsAvailable:false});
  if(path.endsWith('/productions')&&method==='GET')return fulfill(route,[]);
  if(path.endsWith('/tasks')&&method==='GET')return fulfill(route,[]);
  if(path.match(/\/employees\/[^/]+\/attendance$/)&&method==='GET'){
    const id=path.split('/').at(-2)!;const status=mock.attendance[id];const records=status?[{id:`attendance-${id}`,employeeId:id,date:fixedDate,status,checkInTime:status==='PRESENT'?'09:28':null,checkOutTime:null,minutesLate:0,notes:null,updatedAt:fixedNow.toISOString()}]:[];
    return fulfill(route,{employeeId:id,from:'2026-09-01',to:'2026-09-30',records,summary:{PRESENT:status==='PRESENT'?1:0,LATE:0,ABSENT:status==='ABSENT'?1:0,HALF_DAY:0,LEAVE:status==='LEAVE'?1:0,HOLIDAY:0}});
  }
  if(path.endsWith('/employees')&&method==='GET')return fulfill(route,mock.employees);
  if(path.endsWith('/employees')&&method==='POST'){
    const input=request.postDataJSON();const employee={...baseEmployee,...input,id:'22222222-2222-2222-2222-222222222222',createdAt:fixedNow.toISOString(),updatedAt:fixedNow.toISOString()} as MockEmployee;mock.employees.push(employee);return fulfill(route,employee,201);
  }
  const employeeMatch=path.match(/\/employees\/([^/]+)$/);
  if(employeeMatch){const index=mock.employees.findIndex(employee=>employee.id===employeeMatch[1]);if(method==='PATCH'){mock.employees[index]={...mock.employees[index],...request.postDataJSON(),updatedAt:fixedNow.toISOString()};return fulfill(route,mock.employees[index]);}return fulfill(route,mock.employees[index]);}
  if(path.endsWith('/attendance')&&method==='GET'){
    const rows=mock.employees.map(employee=>{const status=mock.attendance[employee.id];return {employee,record:status?{id:`attendance-${employee.id}`,employeeId:employee.id,date:fixedDate,status,checkInTime:status==='PRESENT'?'09:28':null,checkOutTime:null,minutesLate:0,notes:null,updatedAt:fixedNow.toISOString()}:null};});
    const summary={PRESENT:0,LATE:0,ABSENT:0,HALF_DAY:0,LEAVE:0,HOLIDAY:0} as Record<string,number>;Object.values(mock.attendance).forEach(status=>summary[status]++);return fulfill(route,{date:fixedDate,rows,summary});
  }
  const attendanceMatch=path.match(/\/attendance\/([^/]+)\/\d{4}-\d{2}-\d{2}$/);
  if(attendanceMatch&&method==='PUT'){const input=request.postDataJSON();mock.attendance[attendanceMatch[1]]=input.status;return fulfill(route,{id:`attendance-${attendanceMatch[1]}`,employeeId:attendanceMatch[1],date:fixedDate,...input,updatedAt:fixedNow.toISOString()});}
  if(path.endsWith('/leave-requests')&&method==='GET')return fulfill(route,mock.leaves);
  if(path.endsWith('/leave-requests')&&method==='POST'){
    const input=request.postDataJSON();const employee=mock.employees.find(item=>item.id===input.employeeId)!;const leave:MockLeave={...input,id:`leave-${mock.leaves.length+1}`,employeeName:employee.displayName,status:'PENDING',ownerNote:null,createdAt:fixedNow.toISOString(),resolvedAt:null};mock.leaves.unshift(leave);return fulfill(route,leave,201);
  }
  const leaveMatch=path.match(/\/leave-requests\/([^/]+)\/(approve|reject)$/);
  if(leaveMatch&&method==='POST'){const leave=mock.leaves.find(item=>item.id===leaveMatch[1])!;leave.status=leaveMatch[2]==='approve'?'APPROVED':'REJECTED';leave.ownerNote=request.postDataJSON().ownerNote;leave.resolvedAt=fixedNow.toISOString();if(leave.status==='APPROVED')mock.attendance[leave.employeeId]='LEAVE';return fulfill(route,leave);}
  return fulfill(route,true);
});}
async function fixedPage(page:Page){await page.clock.setFixedTime(fixedNow);}

async function visual(page:Page,name:string){
  await page.mouse.move(1400,875);
  await expect(page.locator('main.workspace-shell>div')).toHaveCSS('opacity','1');
  await expect.poll(()=>page.locator('.sa-card').evaluateAll(cards=>cards.every(card=>getComputedStyle(card).opacity==='1'&&getComputedStyle(card).transform==='none'))).toBe(true);
  if(process.env.VISUAL_REVIEW==='1'){
    await mkdir('.visual-review',{recursive:true});
    await page.screenshot({path:`.visual-review/${name}`,fullPage:true,animations:'disabled'});
  }else await expect(page).toHaveScreenshot(name,{fullPage:true});
}

async function assertShellAndCardFit(page:Page){
  const fit=await page.evaluate(()=>{
    const rect=(selector:string)=>document.querySelector(selector)!.getBoundingClientRect();
    const shell=rect('.app-shell'),dock=rect('.operational-dock'),nav=rect('.top-switcher'),workspace=rect('.workspace-shell');
    const rings=[...document.querySelectorAll('.command-grid .radial')].every(ring=>{const r=ring.getBoundingClientRect(),c=ring.closest('.sa-card')!.getBoundingClientRect();return r.top>=c.top&&r.bottom<=c.bottom&&r.left>=c.left&&r.right<=c.right});
    return {navStraddles:nav.top<shell.top&&nav.bottom>shell.top,dockStraddles:dock.left<shell.left&&dock.right>shell.left,workspaceInset:workspace.left>shell.left&&workspace.right<shell.right,rings};
  });
  expect(fit).toEqual({navStraddles:true,dockStraddles:true,workspaceInset:true,rings:true});
}

test('Phase 1 shell, navigation, theme and command palette',async({page})=>{
  await fixedPage(page);await mockApi(page,state());await page.goto('/');
  await expect(page.getByText('Good afternoon, Owner.')).toBeVisible();
  await visual(page,'command-pearl.png');await assertShellAndCardFit(page);
  await page.getByLabel('Settings').click();await page.getByRole('button',{name:'Charcoal'}).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme','charcoal');await visual(page,'settings-charcoal.png');
  await page.reload();await expect(page.locator('html')).toHaveAttribute('data-theme','charcoal');
  await page.getByRole('link',{name:'Command',exact:true}).click();await expect(page.getByText('Good afternoon, Owner.')).toBeVisible();await visual(page,'command-charcoal.png');await assertShellAndCardFit(page);
  await page.goto('/people');await expect(page.getByText('Amaan Khan')).toBeVisible();await visual(page,'people-charcoal.png');
  await page.getByText('Amaan Khan').click();await expect(page.getByRole('heading',{name:'Amaan Khan'})).toBeVisible();await visual(page,'employee-detail.png');
  await page.getByRole('link',{name:'Attendance'}).click();await expect(page.getByText('Mark remaining present')).toBeVisible();await visual(page,'attendance.png');
  await page.getByLabel('Quick create').click();await expect(page.getByRole('heading',{name:'Quick create'})).toBeVisible();await visual(page,'quick-create.png');
  await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.locator('.modal-overlay')).toHaveCount(0);
  await page.keyboard.press('Control+K');await expect(page.getByPlaceholder('Search people, productions, tasks or actions…')).toBeVisible();await visual(page,'command-palette.png');
  await page.getByPlaceholder('Search people, productions, tasks or actions…').fill('Open People');await page.keyboard.press('Enter');await expect(page.getByRole('heading',{name:'People',exact:true})).toBeVisible();await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('reduced motion preserves navigation, hover and overlay dismissal',async({page})=>{
  await page.emulateMedia({reducedMotion:'reduce'});await fixedPage(page);await mockApi(page,state());await page.goto('/people');
  const card=page.locator('.employee-card').first();await expect(card).toBeVisible();await card.hover();await expect(card).toHaveCSS('transform','none');
  const dock=page.getByRole('link',{name:'Attendance'});await dock.hover();await expect(dock).toHaveCSS('transform','none');
  await page.getByLabel('Quick create').click();await expect(page.getByRole('dialog')).toBeVisible();
  const duration=await page.getByRole('dialog').evaluate(element=>parseFloat(getComputedStyle(element).animationDuration));expect(duration).toBeLessThan(.001);
  await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);
  await card.click();await expect(page.getByRole('heading',{name:'Amaan Khan'})).toBeVisible();await expect(page.locator('.workspace-shell>div')).toHaveCSS('transform','none');
  await page.keyboard.press('Control+K');await expect(page.getByRole('dialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);
});

for(const reducedMotion of ['no-preference','reduce'] as const){
  test(`routes settle visibly and overlays restore focus (${reducedMotion})`,async({page})=>{
    await page.emulateMedia({reducedMotion});await fixedPage(page);await mockApi(page,state());await page.goto('/');
    const resting=async()=>{const content=page.locator('.workspace-page');await expect(content).toHaveCount(1);await expect(content).toHaveCSS('opacity','1');await expect(content).toHaveCSS('transform','none');await expect(content).toHaveCSS('pointer-events','auto');};
    for(let repeat=0;repeat<2;repeat++){
      await resting();await page.getByRole('link',{name:'People',exact:true}).click();await expect(page.getByRole('heading',{name:'People',exact:true})).toBeVisible();await resting();
      await page.getByText('Amaan Khan',{exact:true}).click();await expect(page.getByRole('heading',{name:'Amaan Khan'})).toBeVisible();await resting();
      await page.getByRole('link',{name:'Attendance',exact:true}).click();await expect(page.getByRole('heading',{name:'Attendance',exact:true})).toBeVisible();await resting();
      await page.getByRole('link',{name:'Settings',exact:true}).click();await expect(page.getByRole('heading',{name:'Settings',exact:true})).toBeVisible();await resting();
      await page.getByRole('link',{name:'Command',exact:true}).click();await expect(page.getByRole('heading',{name:'Good afternoon, Owner.'})).toBeVisible();await resting();
    }
    const quick=page.getByRole('button',{name:'Quick create'});await quick.click();await expect(page.getByRole('dialog',{name:'Quick create'})).toBeVisible();
    await page.keyboard.press('Control+K');await expect(page.getByRole('dialog',{name:'Command palette'})).toBeVisible();
    await page.keyboard.press('Escape');await expect(page.locator('.command-dialog')).toHaveCount(0);await expect(page.getByRole('dialog',{name:'Quick create'})).toBeVisible();
    await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.locator('.modal-overlay')).toHaveCount(0);await expect(quick).toBeFocused();
    await expect(page.locator('body')).toHaveCSS('pointer-events','auto');
  });
}

for(const deviceScaleFactor of [1,1.5]){
  test(`radials fit both themes at minimum size and ${deviceScaleFactor*100}% pixel density`,async({browser})=>{
    const context=await browser.newContext({viewport:{width:1180,height:720},deviceScaleFactor,timezoneId:'Asia/Kolkata',locale:'en-IN'});const page=await context.newPage();
    try{await fixedPage(page);await mockApi(page,state());await page.goto('/');
      for(const theme of ['pearl','charcoal']){
        if(theme==='charcoal'){await page.getByRole('link',{name:'Settings'}).click();await page.getByRole('button',{name:'Charcoal'}).click();await page.getByRole('link',{name:'Command',exact:true}).click();}
        await expect(page.locator('html')).toHaveAttribute('data-theme',theme);await expect(page.locator('.workspace-page')).toHaveCSS('opacity','1');await assertShellAndCardFit(page);
        expect(await page.locator('.radial').evaluateAll(rings=>rings.every(ring=>{const r=ring.getBoundingClientRect();return [...ring.querySelectorAll('strong,span')].every(label=>{const l=label.getBoundingClientRect();return l.left>=r.left&&l.right<=r.right&&l.top>=r.top&&l.bottom<=r.bottom})}))).toBe(true);
        expect(await page.locator('.workspace-shell').evaluate(element=>element.scrollWidth<=element.clientWidth+1)).toBe(true);
      }
    }finally{await context.close();}
  });
}

test('owner session restores after sign-in and reload',async({page})=>{
  const mock=state(false);await fixedPage(page);await mockApi(page,mock);await page.goto('/');
  await page.getByLabel('Email').fill('owner@sa.local');await page.getByLabel('Password').fill('SADemo!2026');await page.getByRole('button',{name:'Enter SA Command'}).click();
  await expect(page.getByText('Good afternoon, Owner.')).toBeVisible();await page.reload();await expect(page.getByText('Good afternoon, Owner.')).toBeVisible();
});

test('employee, attendance, history and leave workflows persist across reloads',async({page})=>{
  const mock=state();await fixedPage(page);await mockApi(page,mock);await page.goto('/people');
  await page.getByRole('button',{name:'Employee'}).click();
  await page.getByLabel('Employee code').fill('SA-002');await page.getByLabel('First name').fill('Leena');await page.getByLabel('Last name').fill('Rao');await page.getByLabel('Display name').fill('Leena Rao');await page.getByLabel('Role title').fill('Production Coordinator');await page.getByLabel('Phone',{exact:true}).fill('+91 90000 00002');await page.getByLabel('Base salary (₹)').fill('38000');
  await page.getByRole('button',{name:'Add employee'}).click();await expect(page.getByText('Employee added',{exact:true})).toBeVisible();await page.reload();await expect(page.getByText('Leena Rao')).toBeVisible();
  await page.getByText('Leena Rao').click();await page.getByRole('button',{name:'Edit'}).click();await page.getByLabel('Role title').fill('Senior Production Coordinator');await page.getByRole('button',{name:'Save changes'}).click();await expect(page.getByText('Senior Production Coordinator')).toBeVisible();await page.reload();await expect(page.getByText('Senior Production Coordinator')).toBeVisible();
  await page.getByRole('link',{name:'Attendance'}).click();const leenaRow=page.locator('.attendance-row').filter({hasText:'Leena Rao'});await leenaRow.getByRole('button',{name:'Absent'}).click();await expect(leenaRow.getByRole('button',{name:'Absent'})).toHaveClass(/active/);await page.reload();await expect(page.locator('.attendance-row').filter({hasText:'Leena Rao'}).getByRole('button',{name:'Absent'})).toHaveClass(/active/);
  await page.goto('/people/22222222-2222-2222-2222-222222222222');await page.getByRole('tab',{name:'Attendance'}).click();await expect(page.getByText('ABSENT')).toBeVisible();
  await page.getByRole('link',{name:'Attendance'}).click();await page.getByRole('button',{name:'Record leave'}).click();await page.getByLabel('Employee').selectOption('22222222-2222-2222-2222-222222222222');await page.getByLabel('Reason').fill('Family ceremony');await page.getByRole('button',{name:'Create request'}).click();const leaveCard=page.locator('.leave-card').filter({hasText:'Leena Rao'});await expect(leaveCard).toBeVisible();await leaveCard.getByRole('button',{name:'Approve'}).click();await expect(page.getByText('No pending leave')).toBeVisible();await page.reload();await expect(page.getByText('No pending leave')).toBeVisible();
  await page.getByRole('button',{name:'Record leave'}).click();await page.getByLabel('Employee').selectOption('22222222-2222-2222-2222-222222222222');await page.getByLabel('Reason').fill('Personal appointment');await page.getByRole('button',{name:'Create request'}).click();await page.locator('.leave-card').filter({hasText:'Leena Rao'}).getByRole('button',{name:'Reject'}).click();await expect(page.getByText('No pending leave')).toBeVisible();
});
