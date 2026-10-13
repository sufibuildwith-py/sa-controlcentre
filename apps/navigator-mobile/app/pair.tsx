import { useRef, useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { router } from "expo-router";
import { navigatorApi } from "../src/api/navigatorApi";
import { ApiError, type ApiErrorCode } from "../src/api/client";
import { credentials } from "../src/storage/credentials";
import { useEmployee } from "../src/state/employeeContext";
import {
  ActionButton,
  BrandMark,
  Card,
  Page,
  SectionLabel,
  styles as commonStyles,
} from "../src/components/ui";
import { colors, radius, space } from "../src/theme/tokens";

const pairingMessage = (code: ApiErrorCode) =>
  ({
    INVALID_CODE: "Invalid code. Please check the 6 digits and try again.",
    EXPIRED_CODE: "This pairing code has expired. Request a new code from SA Command.",
    ALREADY_USED: "This pairing code has already been redeemed. Ask for a new code.",
    RATE_LIMITED: "Too many pairing attempts. Please wait a moment before trying again.",
    NETWORK_UNAVAILABLE: "Cannot reach SA Productions. Please check your internet connection.",
    SERVER_UNAVAILABLE: "Production gateway is temporarily unavailable. Please retry shortly.",
    TIMEOUT: "The connection timed out. Please try again.",
    AUTH_REQUIRED: "A fresh pairing code is required to connect.",
    DEVICE_REVOKED: "This device registration has been revoked.",
    INVALID_REQUEST: "The pairing request could not be processed.",
    UNKNOWN: "Unable to connect device. Please verify your code and try again.",
  }[code]);

export default function Pair() {
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<TextInput>(null);
  const { paired } = useEmployee();

  async function submit() {
    if (code.length !== 6 || busy) return;
    setBusy(true);
    setError(null);
    try {
      const deviceLabel = `Android (${Platform.Version})`;
      const result = await navigatorApi.pair(code, deviceLabel);
      await credentials.set(result.deviceToken);
      await paired();
      router.replace("/(employee)");
    } catch (cause) {
      const apiCode = cause instanceof ApiError ? cause.code : "UNKNOWN";
      setError(pairingMessage(apiCode));
    } finally {
      setBusy(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <Page>
        <View style={localStyles.container}>
          <BrandMark />

          <View style={localStyles.header}>
            <Text style={commonStyles.greeting}>
              Connect Your Phone
            </Text>
            <Text style={[commonStyles.body, { marginTop: 6 }]}>
              Enter the 6-digit pairing code generated for you in SA Command.
            </Text>
          </View>

          <Card emphasis>
            <SectionLabel>PAIRING CODE</SectionLabel>

            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Enter 6-digit code"
              onPress={() => inputRef.current?.focus()}
              style={localStyles.pinRow}
            >
              {Array.from({ length: 6 }, (_, index) => {
                const char = code[index] ?? "";
                const isCurrent = index === code.length && !busy;
                const isFilled = Boolean(char);

                return (
                  <View
                    key={index}
                    style={[
                      localStyles.pinBox,
                      isCurrent && localStyles.pinBoxActive,
                      isFilled && localStyles.pinBoxFilled,
                    ]}
                  >
                    <Text style={localStyles.pinText}>{char}</Text>
                    {isCurrent && <View style={localStyles.cursorBar} />}
                  </View>
                );
              })}
            </Pressable>

            <TextInput
              ref={inputRef}
              value={code}
              onChangeText={(val) => {
                const cleaned = val.replace(/\D/g, "").slice(0, 6);
                setCode(cleaned);
                if (error) setError(null);
              }}
              keyboardType="number-pad"
              textContentType="oneTimeCode"
              autoFocus
              caretHidden
              style={localStyles.hiddenInput}
              onSubmitEditing={submit}
            />

            {error ? (
              <View style={localStyles.errorBox}>
                <Text style={localStyles.errorText}>{error}</Text>
              </View>
            ) : null}

            <ActionButton
              busy={busy}
              disabled={code.length !== 6}
              onPress={submit}
            >
              {busy ? "Authorizing device…" : "Connect Device"}
            </ActionButton>
          </Card>

          <Card>
            <Text style={localStyles.policyTitle}>Duty-Only Location Policy</Text>
            <Text style={commonStyles.body}>
              SA Employee is built exclusively for crew operations. Your location is shared
              <Text style={{ color: colors.text, fontWeight: "600" }}> only during explicitly started duty sessions</Text>
              . No tracking occurs when you are off duty.
            </Text>
            <View style={localStyles.policyRow}>
              <View style={localStyles.policyDot} />
              <Text style={commonStyles.meta}>
                Hardware-backed credential encrypted in Android Keystore
              </Text>
            </View>
          </Card>
        </View>
      </Page>
    </KeyboardAvoidingView>
  );
}

const localStyles = StyleSheet.create({
  container: {
    paddingTop: 24,
    gap: 22,
  },
  header: {
    gap: 2,
  },
  pinRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginVertical: 6,
  },
  pinBox: {
    width: 44,
    height: 56,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    backgroundColor: colors.surfaceSoft,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
  },
  pinBoxActive: {
    borderColor: colors.borderFocus,
    backgroundColor: colors.surfaceRaised,
  },
  pinBoxFilled: {
    borderColor: colors.borderStrong,
    backgroundColor: colors.surfaceRaised,
  },
  pinText: {
    fontSize: 24,
    fontWeight: "700",
    color: colors.text,
  },
  cursorBar: {
    position: "absolute",
    bottom: 8,
    width: 16,
    height: 2,
    borderRadius: 1,
    backgroundColor: colors.accent,
  },
  hiddenInput: {
    position: "absolute",
    opacity: 0,
    width: 1,
    height: 1,
  },
  errorBox: {
    backgroundColor: colors.dangerSoft,
    borderWidth: 1,
    borderColor: colors.dangerBorder,
    borderRadius: radius.xs,
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  errorText: {
    fontSize: 13,
    lineHeight: 18,
    color: colors.danger,
    fontWeight: "500",
  },
  policyTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.text,
    letterSpacing: 0.2,
  },
  policyRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginTop: 4,
  },
  policyDot: {
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: colors.success,
  },
});
