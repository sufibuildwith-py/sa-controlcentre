SA Command 1.2.0
Built for SA Productions
Owner: Azeem Khan

WINDOWS
Installer: windows\SA-Command-Setup.exe
Alternative Windows installer: windows\SA-Command.msi
Run the installer, then open SA Command. Everything required is included.
Personal records stay in %LOCALAPPDATA%\SA Productions\SA Command\.
Backups are saved in %LOCALAPPDATA%\SA Productions\SA Command\backups\.
Updates and uninstall do not delete this folder.

MACOS
Installer: macos/SA-Command.dmg (Apple silicon).
Open the disk image and drag SA Command to Applications.
This build is unsigned and not notarized. On first launch, Control-click SA Command,
choose Open, then choose Open again when macOS asks for confirmation.
Personal records stay in ~/Library/Application Support/SA Command/.
Backups are saved in ~/Library/Application Support/SA Command/backups/.

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
Windows installed-app smoke passed, including uninstall/reinstall data retention.
The macOS installer was built and runtime-checked on GitHub's macos-14 runner.
Both installers are unsigned.
