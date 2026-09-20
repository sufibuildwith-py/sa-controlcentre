import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { invoke } from "@tauri-apps/api/core";
import { SAButton, SABentoCard, FormField } from "../../components/ui/sa";

export function ReleaseSettings() {
  const [open, setOpen] = useState(false);
  const [settings, setSettings] = useState({
    accessToken: "",
    phoneNumberId: "",
    appSecret: "",
    verifyToken: "",
  });
  const configure = useMutation({
    mutationFn: () => invoke("configure_whatsapp", settings),
    onSuccess: () => {
      setSettings({
        accessToken: "",
        phoneNumberId: "",
        appSecret: "",
        verifyToken: "",
      });
      setOpen(false);
    },
  });
  const backup = useMutation({
    mutationFn: () => invoke<string>("backup_database"),
  });
  return (
    <>
      <SABentoCard>
        <h3>Back up your workspace</h3>
        <p>
          Save a complete copy of your records. Keep a second copy somewhere
          safe.
        </p>
        <SAButton disabled={backup.isPending} onClick={() => backup.mutate()}>
          Create backup
        </SAButton>
        {backup.data && <p role="status">Backup saved: {backup.data}</p>}
        {backup.error && <p role="alert">{String(backup.error)}</p>}
      </SABentoCard>
      <SABentoCard>
        <h3>WhatsApp</h3>
        <p>Your credentials are saved in your computer’s secure storage.</p>
        <SAButton onClick={() => setOpen(!open)}>Configure WhatsApp</SAButton>
        {open && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              configure.mutate();
            }}
          >
            {(
              [
                ["accessToken", "Access token"],
                ["phoneNumberId", "Phone number ID"],
                ["appSecret", "App secret"],
                ["verifyToken", "Verification token"],
              ] as const
            ).map(([key, label]) => (
              <FormField key={key} label={label}>
                <input
                  aria-label={label}
                  type={key === "phoneNumberId" ? "text" : "password"}
                  autoComplete="off"
                  value={settings[key]}
                  onChange={(e) =>
                    setSettings({ ...settings, [key]: e.target.value })
                  }
                />
              </FormField>
            ))}
            <SAButton
              type="submit"
              disabled={
                configure.isPending ||
                Object.values(settings).some((v) => !v.trim())
              }
            >
              Save securely
            </SAButton>
          </form>
        )}
        {configure.isSuccess && (
          <p role="status">
            Saved. Reopen SA Command to use your updated settings.
          </p>
        )}
        {configure.error && <p role="alert">{String(configure.error)}</p>}
      </SABentoCard>
    </>
  );
}
