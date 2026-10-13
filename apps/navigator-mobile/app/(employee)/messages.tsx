import { useMemo, useState } from "react";
import {
  FlatList,
  RefreshControl,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import {
  Badge,
  BrandMark,
  Card,
  EmptyState,
  styles as commonStyles,
} from "../../src/components/ui";
import { useEmployee } from "../../src/state/employeeContext";
import { colors, radius, space } from "../../src/theme/tokens";

const formatMessageDate = (isoString: string) => {
  const d = new Date(isoString);
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();

  if (isToday) {
    return d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
  return d.toLocaleDateString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
};

export default function Messages() {
  const { home, refresh } = useEmployee();
  const [refreshing, setRefreshing] = useState(false);

  const messages = useMemo(() => home?.messages ?? [], [home?.messages]);

  async function reload() {
    setRefreshing(true);
    await refresh();
    setRefreshing(false);
  }

  return (
    <SafeAreaView style={localStyles.safeArea}>
      <FlatList
        data={messages}
        keyExtractor={(item) => item.id}
        contentContainerStyle={localStyles.listContent}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <View style={localStyles.headerBlock}>
            <BrandMark compact />
            <View style={localStyles.titleBlock}>
              <Text style={commonStyles.greeting}>Notices & Messages</Text>
              <Text style={commonStyles.date}>
                Direct operational communications from SA Command dispatch.
              </Text>
            </View>
          </View>
        }
        ListEmptyComponent={
          <View style={localStyles.emptyContainer}>
            <Card>
              <EmptyState
                title="No Operational Messages"
                body="You are up to date on all notices. Broadcast and team messages will appear here."
              />
            </Card>
          </View>
        }
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={reload}
            tintColor={colors.text}
          />
        }
        renderItem={({ item }) => (
          <View
            style={[
              localStyles.messageCard,
              !item.read && localStyles.messageCardUnread,
            ]}
          >
            <View style={localStyles.cardTop}>
              <View style={localStyles.titleRow}>
                {!item.read && <View style={localStyles.unreadPill} />}
                <Text style={commonStyles.rowTitle}>{item.title}</Text>
              </View>
              <Text style={commonStyles.meta}>{formatMessageDate(item.sentAt)}</Text>
            </View>
            <Text style={commonStyles.body}>{item.body}</Text>
          </View>
        )}
      />
    </SafeAreaView>
  );
}

const localStyles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background,
  },
  listContent: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 110,
    gap: 12,
    flexGrow: 1,
  },
  headerBlock: {
    gap: 16,
    marginBottom: 8,
  },
  titleBlock: {
    gap: 2,
    marginTop: 4,
  },
  emptyContainer: {
    marginTop: 20,
  },
  messageCard: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderSoft,
    borderRadius: radius.md,
    padding: 18,
    gap: 10,
  },
  messageCardUnread: {
    backgroundColor: colors.surfaceRaised,
    borderColor: colors.borderStrong,
  },
  cardTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flex: 1,
  },
  unreadPill: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.accent,
  },
});
