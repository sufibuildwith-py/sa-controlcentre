const API_URL=import.meta.env.VITE_API_URL??'http://localhost:8080/api/v1';
type ApiErrorBody={error:{code:string;message:string;traceId?:string;fields?:Record<string,string>}};
export class ApiError extends Error {constructor(public code:string,message:string,public fields:Record<string,string>={},public traceId?:string){super(message)}}
export async function api<T>(path:string,init?:RequestInit):Promise<T>{
  const response=await fetch(`${API_URL}${path}`,{...init,credentials:'include',headers:{'Content-Type':'application/json',Accept:'application/json',...init?.headers}});
  const body=await response.json().catch(()=>null) as {data:T}|ApiErrorBody|null;
  if(!response.ok){const e=(body as ApiErrorBody|null)?.error;throw new ApiError(e?.code??'NETWORK_ERROR',e?.message??'Unable to complete the request.',e?.fields??{},e?.traceId)}
  return (body as {data:T}).data;
}
export const json=(value:unknown):RequestInit=>({body:JSON.stringify(value)});

