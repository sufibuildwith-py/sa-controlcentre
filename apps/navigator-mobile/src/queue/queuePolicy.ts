export const MAX_BATCH=25,MAX_POINTS=5000,MAX_AGE_MS=24*60*60*1000;
export function retryDelayMs(attempt:number,jitter=0){const seconds=[5,15,45,120,300][Math.min(Math.max(0,attempt),4)];return (seconds+jitter)*1000;}
export function oldestBatch<T extends {createdAt:number}>(points:T[],now=Date.now()){return points.filter(p=>now-p.createdAt<=MAX_AGE_MS).sort((a,b)=>a.createdAt-b.createdAt).slice(0,MAX_BATCH);}
