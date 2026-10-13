import { Tabs } from "expo-router";
import { StyleSheet, Text, View } from "react-native";
import { colors, radius, space } from "../../src/theme/tokens";

interface TabGlyphProps {
  label: string;
  glyph: string;
  focused: boolean;
}

function TabGlyph({ label, glyph, focused }: TabGlyphProps) {
  return (
    <View style={tabStyles.container}>
      <View style={[tabStyles.iconBox, focused && tabStyles.iconBoxFocused]}>
        <Text style={[tabStyles.glyph, focused && tabStyles.glyphFocused]}>
          {glyph}
        </Text>
      </View>
      <Text style={[tabStyles.label, focused && tabStyles.labelFocused]}>
        {label}
      </Text>
    </View>
  );
}

export default function EmployeeTabs() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        sceneStyle: { backgroundColor: colors.background },
        tabBarShowLabel: false,
        tabBarStyle: tabStyles.bar,
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          tabBarIcon: ({ focused }) => (
            <TabGlyph glyph="⌂" label="Duty" focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="work"
        options={{
          tabBarIcon: ({ focused }) => (
            <TabGlyph glyph="▣" label="Work" focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="messages"
        options={{
          tabBarIcon: ({ focused }) => (
            <TabGlyph glyph="✉" label="Notices" focused={focused} />
          ),
        }}
      />
      <Tabs.Screen
        name="device"
        options={{
          href: null,
        }}
      />
    </Tabs>
  );
}

const tabStyles = StyleSheet.create({
  bar: {
    position: "absolute",
    height: 70,
    marginHorizontal: 18,
    marginBottom: 16,
    borderRadius: radius.nav,
    backgroundColor: colors.surfaceRaised,
    borderTopWidth: 1,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.35,
    shadowRadius: 16,
    elevation: 8,
    paddingHorizontal: 8,
  },
  container: {
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
    paddingTop: 8,
  },
  iconBox: {
    width: 38,
    height: 28,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  iconBoxFocused: {
    backgroundColor: "rgba(255, 255, 255, 0.08)",
  },
  glyph: {
    fontSize: 18,
    color: colors.textTertiary,
  },
  glyphFocused: {
    color: colors.text,
    fontWeight: "700",
  },
  label: {
    fontSize: 10,
    fontWeight: "600",
    color: colors.textTertiary,
    letterSpacing: 0.3,
  },
  labelFocused: {
    color: colors.text,
    fontWeight: "700",
  },
});
