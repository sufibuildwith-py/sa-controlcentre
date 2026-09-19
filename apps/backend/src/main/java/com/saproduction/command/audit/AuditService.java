package com.saproduction.command.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditRepository repository; private final ObjectMapper json;
  public AuditService(AuditRepository repository,ObjectMapper json){this.repository=repository;this.json=json;}
  public void record(String entityType,String action,String entityId,Object before,Object after){
    AuditLog log=new AuditLog(); log.actorType="OWNER"; var auth=SecurityContextHolder.getContext().getAuthentication(); log.actorId=auth==null?"system":auth.getName(); log.entityType=entityType; log.entityId=entityId; log.action=action; log.beforeJson=write(before); log.afterJson=write(after); repository.save(log);
  }
  private String write(Object value){ if(value==null)return null; try{return json.writeValueAsString(value);}catch(Exception ignored){return "{}";} }
}

