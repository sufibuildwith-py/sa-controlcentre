#[cfg(feature = "production")]
mod runtime;
#[cfg(feature = "production")]
const SERVICE: &str = "com.saproduction.command.production";
#[cfg(not(feature = "production"))]
const SERVICE: &str = "com.saproduction.command";
const ACCOUNT: &str = "owner-session";

#[tauri::command]
fn store_session_token(token: String) -> Result<(), String> {
    keyring::Entry::new(SERVICE, ACCOUNT)
        .and_then(|entry| entry.set_password(&token))
        .map_err(|_| "Unable to store the session securely.".to_string())
}

#[tauri::command]
fn load_session_token() -> Result<Option<String>, String> {
    let entry = keyring::Entry::new(SERVICE, ACCOUNT)
        .map_err(|_| "Unable to access secure session storage.".to_string())?;
    match entry.get_password() {
        Ok(token) => Ok(Some(token)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(_) => Err("Unable to access secure session storage.".to_string()),
    }
}

#[tauri::command]
fn delete_session_token() -> Result<(), String> {
    let entry = keyring::Entry::new(SERVICE, ACCOUNT)
        .map_err(|_| "Unable to access secure session storage.".to_string())?;
    match entry.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(_) => Err("Unable to clear the secure session.".to_string()),
    }
}

#[tauri::command]
fn quit_app(app: tauri::AppHandle) {
    app.exit(0);
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let builder = tauri::Builder::default();
    #[cfg(feature = "production")]
    let builder = builder.manage(runtime::RuntimeState::default())
        .invoke_handler(tauri::generate_handler![store_session_token, load_session_token, delete_session_token, quit_app, runtime::desktop_request, runtime::configure_whatsapp, runtime::backup_database]);
    #[cfg(not(feature = "production"))]
    let builder = builder
        .invoke_handler(tauri::generate_handler![store_session_token, load_session_token, delete_session_token])
        ;
    let app = builder.build(tauri::generate_context!()).expect("Unable to open SA Command");
    app.run(|app, event| {
        #[cfg(feature = "production")]
        if matches!(event, tauri::RunEvent::Exit) {
            use tauri::Manager;
            if let Ok(mut state) = app.state::<runtime::RuntimeState>().0.lock() { state.take(); }
        }
    });
}
