import Constants from "expo-constants";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Alert, Linking, Platform, Pressable, Text, View } from "react-native";
import { ActionButton, Badge, BrandMark, Card, Page, Row, SectionLabel, styles } from "../../src/components/ui";
import { isTracking, lastLocalLocation } from "../../src/location/tracking";
import { useConnection } from "../../src/network/connection";
import { pendingPointCount } from "../../src/queue/locationQueue";
import { useEmployee } from "../../src/state/employeeContext";
import { colors } from "../../src/theme/tokens";

export default function Device(){
  const {home,lastSync,refresh,unpair}=useEmployee(),{state:connection}=useConnection();
  const [pending,setPending]=useState(0),[task,setTask]=useState(false),[lastLocation,setLastLocation]=useState<string>();
  async function inspect(){setPending(await pendingPointCount());setTask(await isTracking());setLastLocation(await lastLocalLocation())}
  useEffect(()=>{void inspect()},[]);
  function confirmUnpair(){Alert.alert("Unpair this phone?","SA Employee will remove the device credential and return to pairing. Any active duty should be ended first.",[{text:"Cancel",style:"cancel"},{text:"Unpair",style:"destructive",onPress:async()=>{await unpair();router.replace("/pair")}}])}
  return <Page><View style={{flexDirection:"row",alignItems:"center",gap:12}}><Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={()=>router.back()} style={{width:42,height:42,borderRadius:14,backgroundColor:colors.surface,alignItems:"center",justifyContent:"center"}}><Text style={{fontSize:22,color:colors.text}}>‹</Text></Pressable><BrandMark compact/></View><View><Text style={styles.greeting}>Device</Text><Text style={styles.date}>Connection, permissions, and sync health.</Text></View><SectionLabel>EMPLOYEE</SectionLabel><Card><Row title={home?.displayName??"Employee"} detail="SA Productions"/><Row title={home?.dutyActive?"On duty":"Off duty"} detail="Duty" action={<Badge label={home?.dutyActive?"Active":"Idle"} tone={home?.dutyActive?"success":"neutral"}/>}/></Card><SectionLabel>HEALTH</SectionLabel><Card><Row title={connection.replaceAll("_"," ")} detail="Connection"/><Row title={task?"Running":"Stopped"} detail="Location task" action={<Badge label={task?"Active":"Inactive"} tone={task?"success":"neutral"}/>}/><Row title={lastLocation?new Date(lastLocation).toLocaleString():"No local point yet"} detail="Last local location"/><Row title={String(pending)} detail="Pending location updates"/><Row title={lastSync?new Date(lastSync).toLocaleString():"Not synced yet"} detail="Last successful sync"/></Card><SectionLabel>APP</SectionLabel><Card><Row title="SA Employee" detail={`Version ${Constants.expoConfig?.version??"2.0.0"}`}/><Row title="Android device" detail={`Android ${String(Platform.Version)}`}/><ActionButton variant="secondary" onPress={async()=>{await refresh();await inspect()}}>Retry connection</ActionButton><ActionButton variant="secondary" onPress={()=>Linking.openSettings()}>Open Android settings</ActionButton><ActionButton variant="danger" onPress={confirmUnpair}>Unpair device</ActionButton></Card></Page>
}
