import * as SecureStore from "expo-secure-store";
const DEVICE_TOKEN="sa-navigator-device-token";
const ACTIVE_SESSION="sa-navigator-active-session";
export const credentials={get:()=>SecureStore.getItemAsync(DEVICE_TOKEN),set:(token:string)=>SecureStore.setItemAsync(DEVICE_TOKEN,token,{keychainAccessible:SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY}),clear:()=>SecureStore.deleteItemAsync(DEVICE_TOKEN)};
export const activeSession={get:()=>SecureStore.getItemAsync(ACTIVE_SESSION),set:(id:string)=>SecureStore.setItemAsync(ACTIVE_SESSION,id,{keychainAccessible:SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY}),clear:()=>SecureStore.deleteItemAsync(ACTIVE_SESSION)};
