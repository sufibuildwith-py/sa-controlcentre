import * as Location from "expo-location";
import * as TaskManager from "expo-task-manager";
import { LOCATION_TASK } from "../config";
import { gatewayUrl } from "../config";
import { enqueue } from "../queue/locationQueue";
import { flushQueue } from "../api/navigatorApi";
import { activeSession } from "../storage/credentials";
import { getMetadata, setMetadata } from "../storage/cache";
import { locationPolicy } from "./policy";
let sequence=0;
const demoDiagnostics=gatewayUrl.startsWith("http://127.0.0.1");
TaskManager.defineTask(LOCATION_TASK,async({data,error})=>{const sessionId=await activeSession.get();if(error||!data||!sessionId)return;const locations=(data as {locations:Location.LocationObject[]}).locations;for(const point of locations){sequence=Math.max(sequence+1,Date.now());const recordedAt=new Date(point.timestamp).toISOString();await enqueue({sessionId,sequenceNo:sequence,latitude:point.coords.latitude,longitude:point.coords.longitude,accuracyMeters:point.coords.accuracy,recordedAt});await setMetadata("last_local_location",recordedAt);}await flushQueue().catch(()=>{});});
export async function requestForeground(){return (await Location.requestForegroundPermissionsAsync()).status==="granted";}
export async function requestBackground(){return (await Location.requestBackgroundPermissionsAsync()).status==="granted";}
export async function locationServicesEnabled(){return Location.hasServicesEnabledAsync();}
export async function isTracking(){return Location.hasStartedLocationUpdatesAsync(LOCATION_TASK).catch(()=>false);}
export async function lastLocalLocation(){return getMetadata("last_local_location");}
export async function startTracking(id:string){await activeSession.set(id);sequence=Date.now();if(!await locationServicesEnabled())throw new Error("LOCATION_SERVICES_DISABLED");if(await isTracking())return;try{await Location.startLocationUpdatesAsync(LOCATION_TASK,{accuracy:Location.Accuracy.High,timeInterval:locationPolicy.timeInterval,distanceInterval:locationPolicy.distanceInterval,deferredUpdatesInterval:locationPolicy.deferredUpdatesInterval,pausesUpdatesAutomatically:true,showsBackgroundLocationIndicator:true,foregroundService:{notificationTitle:"SA Employee",notificationBody:"On duty · Location sharing active",killServiceOnDestroy:false}});}catch(cause){if(demoDiagnostics)console.warn("[SA Employee duty] location task start failed",{name:cause instanceof Error?cause.name:"Unknown",message:cause instanceof Error?cause.message:"Unknown"});throw new Error("LOCATION_TRACKING_START_FAILED");}if(!await isTracking())throw new Error("LOCATION_TASK_NOT_STARTED");}
export async function stopTracking(){if(await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK))await Location.stopLocationUpdatesAsync(LOCATION_TASK);await activeSession.clear();}
