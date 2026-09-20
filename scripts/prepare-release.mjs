// Runtime packaging only. No business data is read or copied into a release.
import { mkdir, cp, copyFile, readFile, writeFile, readdir, access } from 'node:fs/promises';
import { createWriteStream, createReadStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import { Readable } from 'node:stream';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const cache=path.join(root,'.release-tools','downloads');
const runtime=path.join(root,'apps/desktop/src-tauri/runtime');
await mkdir(cache,{recursive:true}); await mkdir(runtime,{recursive:true});
const exists=async p=>access(p).then(()=>true,()=>false);
const records=[];
async function download(url,name,expected) {
 const file=path.join(cache,name);
 if(!await exists(file)) {
   const response=await fetch(url); if(!response.ok) throw new Error(`Download failed: ${response.status}`);
   await pipeline(Readable.fromWeb(response.body),createWriteStream(file));
 }
 const hash=createHash('sha256'); for await (const chunk of createReadStream(file)) hash.update(chunk);
 const sha256=hash.digest('hex'); if(expected && sha256!==expected) throw new Error(`Checksum mismatch: ${name}`);
 records.push({name,url,sha256}); return file;
}
function extractZip(file,dest) { execFileSync('tar',['-xf',file,'-C',dest],{stdio:'inherit'}); }
const win=process.platform==='win32', arch=process.arch==='arm64'?'aarch64':'x64', os=win?'windows':'mac';
if(!win && process.platform!=='darwin') throw new Error('Run release preparation on Windows or macOS.');
const releases=await (await fetch(`https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=${arch}&image_type=jre&os=${os}&vendor=eclipse`)).json();
const pkg=releases[0].binary.package;
const jreArchive=await download(pkg.link,pkg.name,pkg.checksum);
const jreExtract=path.join(cache,`jre-${os}-${arch}`); await mkdir(jreExtract,{recursive:true});
extractZip(jreArchive,jreExtract);
const jreFolder=(await readdir(jreExtract)).find(n=>n.startsWith('jdk-'));
await cp(path.join(jreExtract,jreFolder,...(win?[]:['Contents','Home'])),path.join(runtime,'jre'),{recursive:true});
if(win) {
 const archive=await download('https://get.enterprisedb.com/postgresql/postgresql-17.11-4-windows-x64-binaries.zip','postgresql-17.11-4-windows-x64-binaries.zip');
 const pgExtract=path.join(cache,'postgres-windows'); await mkdir(pgExtract,{recursive:true});
 if(!await exists(path.join(pgExtract,'pgsql/bin/postgres.exe'))) extractZip(archive,pgExtract);
 for(const dir of ['bin','lib','share']) await cp(path.join(pgExtract,'pgsql',dir),path.join(runtime,'postgres',dir),{recursive:true});
 // Temurin includes the redistributable MSVC libraries; keep PostgreSQL independent of a system installation.
 for(const name of ['msvcp140.dll','vcruntime140.dll','vcruntime140_1.dll'])
   await copyFile(path.join(runtime,'jre/bin',name),path.join(runtime,'postgres/bin',name));
} else {
 const dmg=await download('https://github.com/PostgresApp/PostgresApp/releases/download/v2.9.6/Postgres-2.9.6-17.dmg','Postgres-2.9.6-17.dmg');
 const mount=path.join(cache,'postgres-mount'); await mkdir(mount,{recursive:true});
 execFileSync('hdiutil',['attach',dmg,'-nobrowse','-mountpoint',mount],{stdio:'inherit'});
 try {
   const source=path.join(mount,'Postgres.app/Contents/Versions/17');
   for(const dir of ['bin','lib','share'])
     await cp(path.join(source,dir),path.join(runtime,'postgres',dir),{recursive:true,dereference:true});
 }
 finally {execFileSync('hdiutil',['detach',mount],{stdio:'inherit'});}
}
await copyFile(path.join(root,'apps/backend/target/sa-command-backend-1.2.0.jar'),path.join(runtime,'backend.jar'));
await writeFile(path.join(runtime,'RUNTIME-SOURCES.json'),JSON.stringify(records,null,2));
await copyFile(path.join(root,'docs/release-runtime-notices.txt'),path.join(runtime,'NOTICES.txt'));
console.log('Production runtime prepared. No data directory is included.');
