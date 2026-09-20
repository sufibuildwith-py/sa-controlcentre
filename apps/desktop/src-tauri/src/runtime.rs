use std::{fs::{self, File, OpenOptions}, path::{Path, PathBuf}, process::{Child, Command, Stdio}, sync::Mutex, time::Duration};
use fs2::FileExt;
use rand::{RngCore, rngs::OsRng};
use tauri::Manager;

#[derive(Default)]
pub struct RuntimeState(pub Mutex<Option<Runtime>>);
pub struct Runtime { java: Child, pg: PathBuf, data: PathBuf, secret: String, password: String, _lock: File }
#[derive(serde::Serialize)]
pub struct Reply { status: u16, body: String }

fn command(path: &Path) -> Command {
    let mut cmd = Command::new(path);
    #[cfg(windows)] { use std::os::windows::process::CommandExt; cmd.creation_flags(0x08000000); }
    cmd
}
fn executable(root: &Path, name: &str) -> PathBuf { root.join("bin").join(format!("{}{}", name, if cfg!(windows) { ".exe" } else { "" })) }
fn random() -> String { let mut bytes=[0u8;32]; OsRng.fill_bytes(&mut bytes); bytes.iter().map(|b| format!("{:02x}", b)).collect() }
fn credential(account: &str) -> Result<keyring::Entry,String> { keyring::Entry::new(super::SERVICE, account).map_err(|_| "Secure storage is unavailable.".into()) }
fn run(mut cmd: Command, log: &Path) -> Result<(),String> {
    let file=OpenOptions::new().create(true).append(true).open(log).map_err(|_| "Unable to open the startup log.")?;
    let status=cmd.stdout(file.try_clone().map_err(|_| "Unable to open log.")?).stderr(file).status().map_err(|_| "A required application component could not start.")?;
    if !status.success() { return Err("SA Command could not start. Please contact support with the startup log.".into()); } Ok(())
}
impl Runtime {
  fn start(app: &tauri::AppHandle) -> Result<Self,String> {
    let resources=app.path().resource_dir().map_err(|_| "Application files are unavailable.")?.join("runtime");
    let data=if cfg!(windows) {
      PathBuf::from(std::env::var_os("LOCALAPPDATA").ok_or("Your personal storage is unavailable.")?).join("SA Productions").join("SA Command")
    } else { app.path().home_dir().map_err(|_| "Your personal storage is unavailable.")?.join("Library/Application Support/SA Command") };
    fs::create_dir_all(data.join("logs")).map_err(|_| "Unable to create your workspace.")?;
    #[cfg(unix)] { use std::os::unix::fs::PermissionsExt; fs::set_permissions(&data,fs::Permissions::from_mode(0o700)).map_err(|_| "Unable to protect your workspace.")?; }
    let lock=OpenOptions::new().create(true).read(true).write(true).open(data.join("application.lock")).map_err(|_| "Unable to open your workspace.")?;
    lock.try_lock_exclusive().map_err(|_| "SA Command is already open. Please return to its window.")?;
    let password=match credential("database-password")?.get_password() {
      Ok(value)=>value,
      Err(keyring::Error::NoEntry) if !data.join("database/PG_VERSION").exists() => { let value=random(); credential("database-password")?.set_password(&value).map_err(|_| "Unable to secure your workspace.")?; value },
      _ => return Err("Your workspace key is unavailable. Please contact support; your data has not been changed.".into())
    };
    let pg=resources.join("postgres");
    let log=data.join("logs/startup.log");
    if !data.join("database/PG_VERSION").exists() {
      let pwfile=data.join("init-password");
      fs::write(&pwfile,&password).map_err(|_| "Unable to initialize secure storage.")?;
      let mut init=command(&executable(&pg,"initdb"));
      init.args(["-D"]).arg(data.join("database")).args(["-U","sa_command","--auth-host=scram-sha-256","--auth-local=scram-sha-256","--encoding=UTF8","--locale=C","--pwfile"]).arg(&pwfile);
      let result=run(init,&log); let _=fs::remove_file(pwfile); result?;
    }
    let mut start=command(&executable(&pg,"pg_ctl"));
    start.arg("-D").arg(data.join("database")).arg("-l").arg(data.join("logs/database.log")).args(["-o","-h 127.0.0.1 -p 17842 -c fsync=on -c full_page_writes=on -c synchronous_commit=on","-w","start"]);
    run(start,&log)?;
    // Always connect to the persistent postgres database; Flyway owns the application schema.
    let secret=random();
    let mut java=command(&executable(&resources.join("jre"),"java"));
    java.args(["-Xms64m","-Xmx512m","-jar"]).arg(resources.join("backend.jar"))
      .env("SPRING_PROFILES_ACTIVE","desktop").env("APP_MODE","production").env("DEMO_SEED","false")
      .env("DATABASE_URL","jdbc:postgresql://127.0.0.1:17842/postgres").env("DATABASE_USERNAME","sa_command").env("DATABASE_PASSWORD",&password)
      .env("DESKTOP_ORIGINS","tauri://localhost,http://tauri.localhost,https://tauri.localhost").env("SA_DESKTOP_SECRET",&secret).env("SA_DATA_DIR",&data)
      .env("MESSAGING_PROVIDER","unconfigured").env("MESSAGING_WORKER_ENABLED","false").stdin(Stdio::null());
    if let Ok(settings)=credential("whatsapp")?.get_password() {
      let values: std::collections::HashMap<String,String>=serde_json::from_str(&settings).map_err(|_| "Saved WhatsApp settings could not be opened.")?;
      for (key,value) in values { java.env(key,value); }
      java.env("MESSAGING_PROVIDER","meta").env("MESSAGING_WORKER_ENABLED","true");
    }
    let logfile=OpenOptions::new().create(true).append(true).open(&log).map_err(|_| "Unable to open log.")?;
    java.stdout(logfile.try_clone().map_err(|_| "Unable to open log.")?).stderr(logfile);
    let process=match java.spawn() { Ok(child)=>child, Err(_)=> { let mut stop=command(&executable(&pg,"pg_ctl")); stop.arg("-D").arg(data.join("database")).args(["-m","fast","stop"]); let _=run(stop,&log); return Err("SA Command could not start its workspace.".into()); } };
    let mut runtime=Self {java:process,pg,data,secret,password,_lock:lock};
    let client=reqwest::blocking::Client::builder().timeout(Duration::from_secs(2)).build().map_err(|_| "Unable to start.")?;
    for _ in 0..120 {
      if runtime.java.try_wait().map_err(|_| "Unable to check startup.")?.is_some() { return Err("SA Command could not open your workspace. Please contact support with the startup log.".into()); }
      if client.get("http://127.0.0.1:17841/api/v1/owner-setup").header("X-SA-Desktop-Key",&runtime.secret).send().map(|r|r.status().is_success()).unwrap_or(false) {return Ok(runtime);}
      std::thread::sleep(Duration::from_millis(500));
    }
    Err("Your workspace is taking too long to open. Please reopen SA Command.".into())
  }
}
impl Drop for Runtime { fn drop(&mut self) {
  let _=self.java.kill(); let _=self.java.wait();
  let mut stop=command(&executable(&self.pg,"pg_ctl")); stop.arg("-D").arg(self.data.join("database")).args(["-m","fast","-w","stop"]);
  let _=run(stop,&self.data.join("logs/startup.log"));
} }
#[tauri::command]
pub async fn desktop_request(app: tauri::AppHandle, path: String, method: String, body: Option<String>, token: Option<String>) -> Result<Reply,String> {
 tauri::async_runtime::spawn_blocking(move || {
    if !path.starts_with('/') || path.contains("..") || path.starts_with("//") { return Err("Invalid request.".into()); }
    let state=app.state::<RuntimeState>(); let mut guard=state.0.lock().map_err(|_| "Workspace is unavailable.")?;
    if guard.is_none() { *guard=Some(Runtime::start(&app)?); }
    let runtime=guard.as_ref().unwrap();
    let client=reqwest::blocking::Client::builder().timeout(Duration::from_secs(30)).redirect(reqwest::redirect::Policy::none()).build().map_err(|_| "Unable to connect to your workspace.")?;
    let mut request=client.request(method.parse().map_err(|_| "Invalid request.")?,format!("http://127.0.0.1:17841/api/v1{}",path)).header("X-SA-Desktop-Key",&runtime.secret).header("Content-Type","application/json");
    if let Some(token)=token { request=request.bearer_auth(token); }
    if let Some(body)=body {request=request.body(body);}
    let response=request.send().map_err(|_| "Your workspace is unavailable. Please reopen SA Command.")?;
    Ok(Reply {status:response.status().as_u16(),body:response.text().map_err(|_| "Unable to read the response.")?})
 }).await.map_err(|_| "Workspace request failed.".to_string())?
}
#[tauri::command]
pub async fn configure_whatsapp(app: tauri::AppHandle, access_token: String, phone_number_id: String, app_secret: String, verify_token: String) -> Result<(),String> {
 tauri::async_runtime::spawn_blocking(move || {
    if [&access_token,&phone_number_id,&app_secret,&verify_token].iter().any(|v|v.trim().is_empty()) {return Err("Complete all WhatsApp settings.".into());}
    let settings=serde_json::json!({"META_WHATSAPP_ACCESS_TOKEN":access_token,"META_WHATSAPP_PHONE_NUMBER_ID":phone_number_id,"META_WHATSAPP_APP_SECRET":app_secret,"META_WHATSAPP_VERIFY_TOKEN":verify_token});
    credential("whatsapp")?.set_password(&settings.to_string()).map_err(|_| "Unable to save WhatsApp settings securely.")?;
    app.state::<RuntimeState>().0.lock().map_err(|_| "Unable to restart your workspace.")?.take(); Ok(())
 }).await.map_err(|_| "Unable to save settings.".to_string())?
}
#[tauri::command]
pub async fn backup_database(app: tauri::AppHandle) -> Result<String,String> {
 tauri::async_runtime::spawn_blocking(move || {
    let state=app.state::<RuntimeState>(); let guard=state.0.lock().map_err(|_| "Unable to open your workspace.")?;
    let runtime=guard.as_ref().ok_or("Please open your workspace first.")?;
    let backups=runtime.data.join("backups"); fs::create_dir_all(&backups).map_err(|_| "Unable to create backup folder.")?;
    let stamp=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_secs();
    let output=backups.join(format!("SA-Command-{}.backup",stamp));
    let mut backup=command(&executable(&runtime.pg,"pg_dump")); backup.args(["-h","127.0.0.1","-p","17842","-U","sa_command","-d","postgres","-Fc","-f"]).arg(&output).env("PGPASSWORD",&runtime.password);
    run(backup,&runtime.data.join("logs/startup.log"))?; Ok(output.to_string_lossy().to_string())
 }).await.map_err(|_| "Backup failed.".to_string())?
}
