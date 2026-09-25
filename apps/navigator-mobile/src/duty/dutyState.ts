export type DutyState="OFF_DUTY"|"STARTING"|"ACQUIRING_LOCATION"|"ON_DUTY_TRACKING"|"ON_DUTY_DEGRADED"|"ENDING"|"END_PENDING_SYNC"|"ERROR";
export type LocationHealth="STARTING"|"ACQUIRING"|"LIVE"|"QUEUING_OFFLINE"|"STALE"|"PERMISSION_BLOCKED"|"SERVICE_DISABLED"|"TASK_STOPPED"|"ERROR";

export function locationHealth(active:boolean,taskRunning:boolean,lastLocationAt?:string,online=true,now=Date.now()):LocationHealth{
  if(!active)return "TASK_STOPPED";
  if(!taskRunning)return "TASK_STOPPED";
  if(!online)return "QUEUING_OFFLINE";
  if(!lastLocationAt)return "ACQUIRING";
  return now-new Date(lastLocationAt).getTime()<=90_000?"LIVE":"STALE";
}

export function dutyLabel(state:DutyState){
  return ({OFF_DUTY:"Off duty",STARTING:"Starting duty",ACQUIRING_LOCATION:"Acquiring location",ON_DUTY_TRACKING:"On duty · Live",ON_DUTY_DEGRADED:"On duty · Attention",ENDING:"Ending duty",END_PENDING_SYNC:"Off duty · Sync pending",ERROR:"Duty unavailable"} as const)[state];
}
