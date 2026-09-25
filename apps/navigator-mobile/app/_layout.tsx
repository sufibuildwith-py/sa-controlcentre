import "../src/location/tracking";
import * as SplashScreen from "expo-splash-screen";
import { Stack, type ErrorBoundaryProps } from "expo-router";
import { useEffect } from "react";
import { StatusBar, Text } from "react-native";
import { ActionButton, BrandMark, Card, Page, styles } from "../src/components/ui";
import { ConnectionProvider } from "../src/network/connection";
import { EmployeeProvider, useEmployee } from "../src/state/employeeContext";
import { colors } from "../src/theme/tokens";

void SplashScreen.preventAutoHideAsync().catch(() => undefined);
function AppStack(){const {boot}=useEmployee();useEffect(()=>{if(boot!=="BOOTING")void SplashScreen.hideAsync().catch(()=>undefined);},[boot]);return <><StatusBar barStyle="light-content" backgroundColor={colors.background}/><Stack screenOptions={{headerShown:false,animation:"fade",contentStyle:{backgroundColor:colors.background}}}/></>}
export default function Layout(){return <ConnectionProvider><EmployeeProvider><AppStack/></EmployeeProvider></ConnectionProvider>}
export function ErrorBoundary({error,retry}:ErrorBoundaryProps){return <Page><BrandMark/><Card emphasis><Text style={styles.title}>Something went wrong</Text><Text style={styles.body}>SA Employee couldn't load this screen. Your pairing and saved updates are still protected.</Text><ActionButton onPress={retry}>Try again</ActionButton>{__DEV__&&<Text style={styles.meta}>{error.message}</Text>}</Card></Page>}
