import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { navigatorApi, type MobileHome } from "../api/navigatorApi";
import { ApiError } from "../api/client";
import { useConnection } from "../network/connection";
import { clearEmployeeCache, getMetadata, initializeCache, readHomeCache, writeHomeCache } from "../storage/cache";
import { credentials } from "../storage/credentials";

export type BootState="BOOTING"|"PAIRING"|"READY"|"OFFLINE_READY"|"RECOVERY"|"FATAL_ERROR";
type EmployeeContextValue={boot:BootState;home?:MobileHome;lastSync?:string;error?:string;refresh:()=>Promise<void>;paired:()=>Promise<void>;unpair:()=>Promise<void>};
const EmployeeContext=createContext<EmployeeContextValue>({boot:"BOOTING",refresh:async()=>undefined,paired:async()=>undefined,unpair:async()=>undefined});

export function EmployeeProvider({children}:PropsWithChildren){
  const [boot,setBoot]=useState<BootState>("BOOTING"),[home,setHome]=useState<MobileHome>(),[lastSync,setLastSync]=useState<string>(),[error,setError]=useState<string>();
  const {state:connection,setServiceState}=useConnection();
  const refresh=useCallback(async()=>{
    try{
      const value=await navigatorApi.home();
      setHome(value);await writeHomeCache(value);setLastSync(await getMetadata("last_sync"));setBoot("READY");setError(undefined);setServiceState("ONLINE");
    }catch(cause){
      const apiError=cause as ApiError;
      if(apiError.code==="DEVICE_REVOKED"||apiError.code==="AUTH_REQUIRED"){setBoot("PAIRING");setServiceState("AUTH_REQUIRED");return;}
      setError(apiError.message);setServiceState(apiError.code==="SERVER_UNAVAILABLE"?"SERVICE_UNAVAILABLE":"DEGRADED");setBoot(current=>home||current==="OFFLINE_READY"?"OFFLINE_READY":current);
    }
  },[home,setServiceState]);
  useEffect(()=>{(async()=>{try{await initializeCache();const [token,cached,synced]=await Promise.all([credentials.get(),readHomeCache(),getMetadata("last_sync")]);setHome(cached);setLastSync(synced);if(!token){setBoot("PAIRING");return;}setBoot(cached?"OFFLINE_READY":"READY");void refresh();}catch(cause){setError(cause instanceof Error?cause.message:"Local data could not be opened.");setBoot("RECOVERY");}})();},[]);
  useEffect(()=>{if((connection==="ONLINE"||connection==="CONNECTING")&&boot==="OFFLINE_READY")void refresh();},[connection,boot,refresh]);
  const paired=useCallback(async()=>{setBoot("READY");await refresh();},[refresh]);
  const unpair=useCallback(async()=>{await credentials.clear();await clearEmployeeCache();setHome(undefined);setBoot("PAIRING");},[]);
  const value=useMemo(()=>({boot,home,lastSync,error,refresh,paired,unpair}),[boot,home,lastSync,error,refresh,paired,unpair]);
  return <EmployeeContext.Provider value={value}>{children}</EmployeeContext.Provider>;
}
export const useEmployee=()=>useContext(EmployeeContext);
