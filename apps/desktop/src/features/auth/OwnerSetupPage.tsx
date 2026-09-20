import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Command } from "lucide-react";
import { api, json, setSessionToken } from "../../lib/api";
import { FormField, SAButton } from "../../components/ui/sa";
import type { Owner } from "../../types/domain";

export function OwnerSetupPage() {
  const client = useQueryClient();
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const setup = useMutation({
    mutationFn: async () => {
      const result = await api<{ owner: Owner; token: string }>(
        "/owner-setup",
        { method: "POST", ...json({ password, confirmation }) },
      );
      await setSessionToken(result.token);
      return result.owner;
    },
    onSuccess: (owner) => {
      client.setQueryData(["auth", "me"], owner);
      client.invalidateQueries({ queryKey: ["owner-setup"] });
    },
  });
  return (
    <main className="login-page">
      <section className="login-brand">
        <div className="brand-seal">
          <Command size={26} />
        </div>
        <div>
          <span>SA Productions</span>
          <h1>Command</h1>
          <p>Welcome to your workspace, Azeem.</p>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-form-wrap">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              setup.mutate();
            }}
          >
            <h2>Welcome to SA Command</h2>
            <p>Owner · Azeem Khan</p>
            <FormField label="Create your password">
              <input
                aria-label="Create your password"
                type="password"
                autoComplete="new-password"
                minLength={12}
                maxLength={72}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </FormField>
            <FormField label="Confirm password">
              <input
                aria-label="Confirm password"
                type="password"
                autoComplete="new-password"
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
              />
            </FormField>
            <p>
              Use at least 12 characters. Keep your password somewhere safe.
            </p>
            {setup.error && (
              <p role="alert" className="form-error">
                {setup.error.message}
              </p>
            )}
            <SAButton
              type="submit"
              variant="primary"
              disabled={
                setup.isPending ||
                password.length < 12 ||
                password !== confirmation
              }
            >
              Set up SA Command
            </SAButton>
          </form>
        </div>
      </section>
    </main>
  );
}
