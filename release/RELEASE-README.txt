SA Command 1.2.0
Built for SA Productions
Owner: Azeem Khan

WINDOWS
Expected installer: windows\SA-Command-Setup.exe
Run the installer, then open SA Command. Everything required is included.
Personal records stay in %LOCALAPPDATA%\SA Productions\SA Command\.
Updates and uninstall do not delete this folder.

MACOS
Expected installer: macos/SA-Command.dmg (Apple silicon).
Open the disk image and drag SA Command to Applications.
The build is unsigned and not notarized unless a later release says otherwise.
Personal records stay in ~/Library/Application Support/SA Command/.
If BUILD-REQUIRED.txt is present, the Mac installer has not been produced yet.

FIRST RUN
Azeem creates his own password. The workspace begins empty.
The owner account is azeem@sa-command.local. No password is supplied.
WhatsApp starts as Not configured; it can be connected later in Settings.

BACKUP
Open Settings and choose Create backup.
Copy the resulting .backup file from the backups folder inside the personal
records folder above to a secure external drive. Backups contain private records.
For a complete same-computer archive, fully close SA Command and copy the entire
personal records folder. Keep the original computer's secure credential store.
Do not copy a live database folder or delete original records after a backup.
Contact support to restore a .backup file on another computer.

INSTALLER STATUS
See the accompanying build-status files for actual build and smoke-test results.
An expected filename in this document is not evidence of a completed build.
