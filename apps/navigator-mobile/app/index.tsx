import { Redirect } from "expo-router";
import { ActivityIndicator, Text, View } from "react-native";
import { BrandMark, Card, Page, styles } from "../src/components/ui";
import { useEmployee } from "../src/state/employeeContext";
import { colors } from "../src/theme/tokens";
export default function Index(){const {boot,error}=useEmployee();if(boot==="PAIRING")return <Redirect href="/pair"/>;if(boot==="READY"||boot==="OFFLINE_READY")return <Redirect href="/(employee)"/>;if(boot==="RECOVERY"||boot==="FATAL_ERROR")return <Page><BrandMark/><Card emphasis><Text style={styles.title}>Recovery needed</Text><Text style={styles.body}>SA Employee couldn't open its saved information. Restart the app and try again.</Text>{error&&<Text style={styles.meta}>{error}</Text>}</Card></Page>;return <Page><View style={{flex:1,justifyContent:"center",alignItems:"center",gap:22}}><BrandMark/><ActivityIndicator color={colors.text}/><Text style={styles.meta}>Preparing your workday…</Text></View></Page>}
