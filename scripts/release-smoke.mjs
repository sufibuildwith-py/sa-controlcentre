// Installed production-app smoke. Uses an isolated profile, never the demo database.
import { chromium } from '../apps/desktop/node_modules/playwright-core/index.mjs';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
const exe=process.argv[2];
if(!exe)throw new Error('Pass the installed SA Command executable path.');
const profile=path.resolve('.release-tools/installed-smoke');
await mkdir(profile,{recursive:true});
const env={...process.env,LOCALAPPDATA:profile,WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9223',WEBVIEW2_USER_DATA_FOLDER:path.join(profile,'webview')};
let app, browser, page;
async function launch() {
 app=spawn(exe,[],{env,windowsHide:true,stdio:'ignore'});
 let endpoint;
 for(let i=0;i<90;i++) {
   try {const result=await fetch('http://127.0.0.1:9223/json/version'); if(result.ok){endpoint=await result.json();break;}} catch{}
   await new Promise(r=>setTimeout(r,1000));
 }
 if(!endpoint)throw new Error('Installed application did not expose its smoke-test WebView.');
 browser=await chromium.connectOverCDP(endpoint.webSocketDebuggerUrl);
 for(let i=0;i<30;i++) {page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().includes('tauri.localhost'));if(page)break;await new Promise(r=>setTimeout(r,1000));}
 if(!page)throw new Error('Installed app window unavailable.');
 page.setDefaultTimeout(120000);
}
async function native(name,args={}) {return page.evaluate(({name,args})=>window.__TAURI_INTERNALS__.invoke(name,args),{name,args});}
async function api(p,method='GET',value) {
 const token=await native('load_session_token');
 const result=await native('desktop_request',{path:p,method,body:value?JSON.stringify(value):null,token});
 if(result.status!==200)throw new Error(`Smoke request ${p} failed (${result.status}).`);
 return JSON.parse(result.body).data;
}
async function close() {
 const done=new Promise(r=>app.once('exit',r));
 await native('plugin:window|close',{label:'main'}).catch(()=>{});
 await Promise.race([done,new Promise((_,reject)=>setTimeout(()=>reject(new Error('Application did not shut down cleanly.')),30000))]);
 await browser.close().catch(()=>{});
}
try {
 await launch();
 await page.getByRole('heading',{name:'Welcome to SA Command',exact:true}).waitFor();
 await page.screenshot({path:path.join(profile,'first-run.png')});
 const password=randomUUID()+'aB9!';
 await page.getByLabel('Create your password',{exact:true}).fill(password);
 await page.getByLabel('Confirm password',{exact:true}).fill(password);
 await page.getByRole('button',{name:'Set up SA Command',exact:true}).click();
 await page.getByRole('heading',{name:/Azeem/}).first().waitFor();
 const employees=await api('/employees'); if(employees.length)throw new Error('Production business data was not empty.');
 for(const route of ['/productions','/tasks','/meetings','/payroll'])if((await api(route)).length)throw new Error(`Seed data found: ${route}`);
 if((await api('/messages')).totalElements!==0)throw new Error('Seed communication history found.');
 const health=await api('/system/diagnostics'); if(health.appMode!=='production'||health.messagingProvider!=='unconfigured')throw new Error('Incorrect production defaults.');
 const employee=await api('/employees','POST',{employeeCode:'SMOKE-001',firstName:'Release',displayName:'Release Smoke Employee',phone:'+919000000999',roleTitle:'Editor',department:'Production',employmentType:'FULL_TIME',joiningDate:'2026-01-01',baseSalaryMinor:100000,salaryCurrency:'INR',status:'ACTIVE'});
 await page.getByRole('link',{name:'People',exact:true}).click();
 await page.getByText('Release Smoke Employee',{exact:true}).waitFor();
 await page.screenshot({path:path.join(profile,'employee.png')});
 await close();
 await launch();
 await page.getByRole('heading',{name:/Azeem/}).first().waitFor();
 const persisted=await api('/employees'); if(!persisted.some(e=>e.id===employee.id))throw new Error('Employee did not persist after application restart.');
 const backup=await native('backup_database');
 await page.getByRole('link',{name:'Settings',exact:true}).click();
 await page.getByRole('button',{name:'Charcoal',exact:true}).click();
 await page.screenshot({path:path.join(profile,'settings-charcoal.png')});
 await api('/auth/logout','POST',{});await native('delete_session_token');
 await close();
 const report={installedApp:exe,firstRun:'PASS',emptyProductionData:'PASS',ownerSetup:'PASS',dashboard:'PASS',employeeCreation:'PASS',restartPersistence:'PASS',backup:'PASS',profile,backupFile:backup};
 await writeFile(path.join(profile,'smoke-results.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
} catch(error) {if(page)await page.screenshot({path:path.join(profile,'failure.png')}).catch(()=>{});console.error(error.message);process.exitCode=1;}
