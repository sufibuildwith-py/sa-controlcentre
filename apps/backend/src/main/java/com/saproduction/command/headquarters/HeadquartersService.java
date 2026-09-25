package com.saproduction.command.headquarters;

import static com.saproduction.command.headquarters.HeadquartersController.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeadquartersService {
  static final UUID ORGANIZATION = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  HeadquartersService(JdbcTemplate jdbc, AuditService audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> overview() {
    var totals = jdbc.queryForMap("""
      SELECT COALESCE(sum(p.physical_quantity-p.unavailable_quantity),0) controlled,
             COALESCE(sum(CASE WHEN p.condition='GOOD' THEN p.physical_quantity-p.unavailable_quantity ELSE 0 END),0) usable,
             COALESCE(sum(CASE WHEN l.location_type='PRODUCTION_LOCATION' THEN p.physical_quantity ELSE 0 END),0) deployed
      FROM hq_inventory_positions p JOIN hq_locations l ON l.id=p.location_id
      WHERE p.organization_id=?
      """, ORGANIZATION);
    var reserved = scalar("""
      SELECT COALESCE(sum(rl.quantity),0) FROM hq_reservation_lines rl JOIN hq_reservations r ON r.id=rl.reservation_id
      WHERE r.organization_id=? AND r.status IN ('CONFIRMED','PARTIALLY_FULFILLED') AND r.ends_at>now()
      """, ORGANIZATION);
    var attentionCount = jdbc.queryForObject("SELECT count(*) FROM hq_attention WHERE organization_id=? AND status='OPEN'", Long.class, ORGANIZATION);
    var today = jdbc.queryForList("""
      SELECT id, reference, status, scheduled_at "scheduledAt", 'DISPATCH' type FROM hq_dispatches
      WHERE organization_id=? AND scheduled_at::date=current_date AND status<>'CANCELLED' ORDER BY scheduled_at LIMIT 8
      """, ORGANIZATION);
    var activeProductions = jdbc.queryForList("""
      SELECT p.id,p.title,COALESCE(sum(pos.physical_quantity),0) quantity
      FROM productions p JOIN hq_locations l ON l.production_id=p.id
      LEFT JOIN hq_inventory_positions pos ON pos.location_id=l.id
      WHERE p.status NOT IN ('DELIVERED','CANCELLED') GROUP BY p.id,p.title ORDER BY quantity DESC LIMIT 8
      """);
    return Map.of("controlled", totals.get("controlled"), "available", ((BigDecimal) totals.get("usable")).subtract(reserved).max(BigDecimal.ZERO), "reserved", reserved, "deployed", totals.get("deployed"), "attention", attentionCount, "today", today, "activeProductions", activeProductions);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> equipment(int page, int size, String query, String tracking, UUID category) {
    page = Math.max(0, page); size = Math.max(1, Math.min(size, 100));
    var filters = new StringBuilder(" WHERE e.organization_id=? ");
    var args = new ArrayList<Object>(); args.add(ORGANIZATION);
    if (query != null && !query.isBlank()) { filters.append(" AND (lower(e.name) LIKE ? OR lower(COALESCE(e.internal_code,'')) LIKE ?) "); var q="%"+query.trim().toLowerCase()+"%";args.add(q);args.add(q); }
    if (tracking != null && !tracking.isBlank()) { filters.append(" AND e.tracking_mode=? "); args.add(tracking); }
    if (category != null) { filters.append(" AND e.category_id=? "); args.add(category); }
    var total = jdbc.queryForObject("SELECT count(*) FROM hq_equipment e"+filters, Long.class, args.toArray());
    var pageArgs = new ArrayList<>(args); pageArgs.add(size); pageArgs.add(page*size);
    var rows = jdbc.query("""
      SELECT e.id,e.name,e.internal_code,c.name category,e.tracking_mode,u.symbol,e.ownership_default,e.active,e.updated_at,
        COALESCE(sum(pos.physical_quantity),0) controlled,
        COALESCE(sum(CASE WHEN pos.condition='GOOD' THEN pos.physical_quantity-pos.unavailable_quantity ELSE 0 END),0) usable,
        COALESCE((SELECT sum(rl.quantity) FROM hq_reservation_lines rl JOIN hq_reservations r ON r.id=rl.reservation_id WHERE rl.equipment_id=e.id AND r.status IN ('CONFIRMED','PARTIALLY_FULFILLED') AND r.ends_at>now()),0) reserved
      FROM hq_equipment e JOIN hq_units u ON u.id=e.unit_id LEFT JOIN hq_categories c ON c.id=e.category_id
      LEFT JOIN hq_inventory_positions pos ON pos.equipment_id=e.id
      """+filters+" GROUP BY e.id,c.name,u.symbol ORDER BY e.name LIMIT ? OFFSET ?", (rs,n)->equipmentRow(rs), pageArgs.toArray());
    return Map.of("items", rows, "page", page, "size", size, "total", total == null ? 0 : total);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> equipment(UUID id) {
    requireEquipment(id, false);
    var item = jdbc.queryForMap("""
      SELECT e.id,e.name,e.internal_code "internalCode",e.description,e.tracking_mode "trackingMode",e.minimum_reserve "minimumReserve",
             e.ownership_default ownership,e.active,c.name category,u.name unit,u.symbol
      FROM hq_equipment e JOIN hq_units u ON u.id=e.unit_id LEFT JOIN hq_categories c ON c.id=e.category_id
      WHERE e.organization_id=? AND e.id=?
      """, ORGANIZATION,id);
    var positions = jdbc.queryForList("""
      SELECT p.location_id "locationId",l.name location,p.ownership,p.condition,p.physical_quantity "physicalQuantity",p.unavailable_quantity "unavailableQuantity"
      FROM hq_inventory_positions p JOIN hq_locations l ON l.id=p.location_id WHERE p.organization_id=? AND p.equipment_id=? ORDER BY l.name
      """, ORGANIZATION,id);
    var movements = movements(0,25,id).get("items");
    var assets=jdbc.queryForList("SELECT id,asset_code \"assetCode\",serial_number \"serialNumber\",location_id \"locationId\",condition,availability,ownership,retired_at \"retiredAt\" FROM hq_serialized_assets WHERE organization_id=? AND equipment_id=? ORDER BY asset_code LIMIT 200",ORGANIZATION,id);
    return Map.of("item",item,"positions",positions,"movements",movements,"assets",assets);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> movements(int page,int size,UUID equipmentId) {
    page=Math.max(0,page);size=Math.max(1,Math.min(size,100));
    String extra=equipmentId==null?"":" AND m.equipment_id=?";
    Object[] base=equipmentId==null?new Object[]{ORGANIZATION}:new Object[]{ORGANIZATION,equipmentId};
    var total=jdbc.queryForObject("SELECT count(*) FROM hq_inventory_movements m WHERE m.organization_id=?"+extra,Long.class,base);
    var args=new ArrayList<>(Arrays.asList(base));args.add(size);args.add(page*size);
    var rows=jdbc.queryForList("""
      SELECT m.id,m.movement_type "movementType",e.name equipment,m.quantity,u.symbol,
             sl.name "sourceLocation",dl.name "destinationLocation",p.title production,m.reason,m.recorded_by "recordedBy",m.recorded_at "recordedAt"
      FROM hq_inventory_movements m JOIN hq_equipment e ON e.id=m.equipment_id JOIN hq_units u ON u.id=e.unit_id
      LEFT JOIN hq_locations sl ON sl.id=m.source_location_id LEFT JOIN hq_locations dl ON dl.id=m.destination_location_id
      LEFT JOIN productions p ON p.id=m.production_id WHERE m.organization_id=?
      """+extra+" ORDER BY m.recorded_at DESC LIMIT ? OFFSET ?",args.toArray());
    return Map.of("items",rows,"page",page,"size",size,"total",total==null?0:total);
  }

  @Transactional(readOnly = true)
  public List<Map<String,Object>> attention(){return jdbc.queryForList("""
    SELECT id,attention_type "type",severity,title,detail,entity_type "entityType",entity_id "entityId",created_at "createdAt"
    FROM hq_attention WHERE organization_id=? AND status='OPEN' ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END,created_at DESC LIMIT 100
    """,ORGANIZATION);}

  @Transactional(readOnly = true)
  public Map<String,Object> config(){return Map.of(
      "categories",jdbc.queryForList("SELECT id,name,parent_id \"parentId\",description FROM hq_categories WHERE organization_id=? AND active ORDER BY sort_order,name",ORGANIZATION),
      "units",jdbc.queryForList("SELECT id,name,symbol,decimal_allowed \"decimalAllowed\" FROM hq_units WHERE organization_id=? AND active ORDER BY name",ORGANIZATION),
      "locations",jdbc.queryForList("SELECT id,name,location_type \"locationType\",parent_id \"parentId\",production_id \"productionId\" FROM hq_locations WHERE organization_id=? AND active ORDER BY name",ORGANIZATION));}

  @Transactional(readOnly = true)
  public Map<String,Object> availability(UUID equipmentId,Instant startsAt,Instant endsAt){
    validateWindow(startsAt,endsAt);requireEquipment(equipmentId,false);
    var controlled=usable(equipmentId);var committed=committed(equipmentId,startsAt,endsAt,null);var available=controlled.subtract(committed).max(BigDecimal.ZERO);
    var conflicts=jdbc.queryForList("""
      SELECT r.id,p.title,rl.quantity,r.starts_at "startsAt",r.ends_at "endsAt" FROM hq_reservation_lines rl
      JOIN hq_reservations r ON r.id=rl.reservation_id JOIN productions p ON p.id=r.production_id
      WHERE r.organization_id=? AND rl.equipment_id=? AND r.status IN ('CONFIRMED','PARTIALLY_FULFILLED') AND r.starts_at<? AND r.ends_at>?
      ORDER BY r.starts_at
      """,ORGANIZATION,equipmentId,ts(endsAt),ts(startsAt));
    return Map.of("controlled",controlled,"committed",committed,"available",available,"conflicts",conflicts);
  }

  @Transactional public Map<String,Object> category(CategoryInput in){var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_categories(id,organization_id,parent_id,name,description) VALUES(?,?,?,?,?)",id,ORGANIZATION,in.parentId(),in.name().trim(),in.description());audit.record("HQ_CATEGORY","HEADQUARTERS_CATEGORY_CREATED",id.toString(),null,Map.of("name",in.name()));return Map.of("id",id,"name",in.name());}
  @Transactional public Map<String,Object> unit(UnitInput in){var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_units(id,organization_id,name,symbol,decimal_allowed) VALUES(?,?,?,?,?)",id,ORGANIZATION,in.name().trim(),in.symbol().trim(),in.decimalAllowed());audit.record("HQ_UNIT","HEADQUARTERS_UNIT_CREATED",id.toString(),null,Map.of("name",in.name(),"symbol",in.symbol()));return Map.of("id",id,"name",in.name(),"symbol",in.symbol());}
  @Transactional public Map<String,Object> location(LocationInput in){var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_locations(id,organization_id,parent_id,name,location_type,production_id) VALUES(?,?,?,?,?,?)",id,ORGANIZATION,in.parentId(),in.name().trim(),in.locationType(),in.productionId());audit.record("HQ_LOCATION","HEADQUARTERS_LOCATION_CREATED",id.toString(),null,Map.of("name",in.name(),"type",in.locationType()));return Map.of("id",id,"name",in.name(),"locationType",in.locationType());}

  @Transactional public Map<String,Object> createEquipment(EquipmentInput in){
    validateEnum(in.trackingMode(),Set.of("QUANTITY","SERIALIZED","CONSUMABLE"),"trackingMode");validateEnum(in.ownership(),Set.of("SA_OWNED","RENTED","VENDOR_SUPPLIED","CLIENT_SUPPLIED","OTHER"),"ownership");
    var id=UUID.randomUUID();jdbc.update("""
      INSERT INTO hq_equipment(id,organization_id,name,internal_code,description,category_id,unit_id,tracking_mode,minimum_reserve,default_location_id,ownership_default)
      VALUES(?,?,?,?,?,?,?,?,?,?,?)
      """,id,ORGANIZATION,in.name().trim(),blank(in.internalCode()),in.description(),in.categoryId(),in.unitId(),in.trackingMode(),nz(in.minimumReserve()),in.defaultLocationId(),in.ownership());
    audit.record("HQ_EQUIPMENT","HEADQUARTERS_EQUIPMENT_CREATED",id.toString(),null,Map.of("name",in.name(),"trackingMode",in.trackingMode(),"ownership",in.ownership()));return equipment(id);
  }

  @Transactional public Map<String,Object> stock(UUID equipmentId,StockInput in){
    var e=requireEquipment(equipmentId,true);if(existing(in.idempotencyKey(),equipmentId,"STOCK_IN"))return equipment(equipmentId);
    positionDelta(equipmentId,in.locationId(),(String)e.get("ownership_default"),"GOOD",in.quantity());
    movement("STOCK_IN",equipmentId,in.quantity(),null,in.locationId(),null,null,null,null,null,null,in.idempotencyKey(),in.reason());
    audit.record("HQ_EQUIPMENT","HEADQUARTERS_STOCK_IN",equipmentId.toString(),null,Map.of("quantity",in.quantity(),"locationId",in.locationId(),"reason",in.reason()));return equipment(equipmentId);
  }

  @Transactional public Map<String,Object> adjust(UUID equipmentId,AdjustmentInput in){
    if(in.difference().signum()==0)throw ApiException.badRequest("ZERO_ADJUSTMENT","Adjustment difference cannot be zero.");var e=requireEquipment(equipmentId,true);if(existing(in.idempotencyKey(),equipmentId,"ADJUSTMENT"))return equipment(equipmentId);String ownership=(String)e.get("ownership_default");positionDelta(equipmentId,in.locationId(),ownership,"GOOD",in.difference());UUID from=in.difference().signum()<0?in.locationId():null,to=in.difference().signum()>0?in.locationId():null;movement("ADJUSTMENT",equipmentId,in.difference().abs(),from,to,null,null,null,null,null,null,in.idempotencyKey(),in.reason());audit.record("HQ_EQUIPMENT","HEADQUARTERS_INVENTORY_ADJUSTED",equipmentId.toString(),null,Map.of("difference",in.difference(),"locationId",in.locationId(),"reason",in.reason()));return equipment(equipmentId);
  }

  @Transactional public Map<String,Object> asset(UUID equipmentId,AssetInput in){
    var e=requireEquipment(equipmentId,true);if(!"SERIALIZED".equals(e.get("tracking_mode")))throw ApiException.badRequest("TRACKING_MODE_MISMATCH","Individual assets can only be created for serialized equipment.");var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_serialized_assets(id,organization_id,equipment_id,asset_code,serial_number,location_id,ownership,notes) VALUES(?,?,?,?,?,?,?,?)",id,ORGANIZATION,equipmentId,in.assetCode().trim(),blank(in.serialNumber()),in.locationId(),e.get("ownership_default"),in.notes());positionDelta(equipmentId,in.locationId(),(String)e.get("ownership_default"),"GOOD",BigDecimal.ONE);movement("STOCK_IN",equipmentId,BigDecimal.ONE,null,in.locationId(),null,null,null,null,null,null,in.idempotencyKey(),"Serialized asset "+in.assetCode());audit.record("HQ_ASSET","HEADQUARTERS_ASSET_CREATED",id.toString(),null,Map.of("equipmentId",equipmentId,"assetCode",in.assetCode()));return Map.of("id",id,"assetCode",in.assetCode(),"condition","GOOD","availability","AVAILABLE");
  }

  @Transactional public Map<String,Object> reserve(ReservationInput in){
    validateWindow(in.startsAt(),in.endsAt());requireProduction(in.productionId());
    var lines=aggregate(in.lines());for(var id:lines.keySet())requireEquipment(id,true);
    for(var entry:lines.entrySet()){
      var available=usable(entry.getKey()).subtract(committed(entry.getKey(),in.startsAt(),in.endsAt(),null));
      if(available.compareTo(entry.getValue())<0)throw availabilityChanged(entry.getValue(),available);
    }
    var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_reservations(id,organization_id,production_id,starts_at,ends_at,created_by) VALUES(?,?,?,?,?,?)",id,ORGANIZATION,in.productionId(),ts(in.startsAt()),ts(in.endsAt()),actor());
    for(var line:in.lines())jdbc.update("INSERT INTO hq_reservation_lines(id,reservation_id,equipment_id,production_location_id,quantity) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,line.equipmentId(),line.productionLocationId(),line.quantity());
    audit.record("HQ_RESERVATION","HEADQUARTERS_RESERVATION_CREATED",id.toString(),null,Map.of("productionId",in.productionId(),"startsAt",in.startsAt(),"endsAt",in.endsAt(),"lines",in.lines().size()));return Map.of("id",id,"status","CONFIRMED");
  }

  @Transactional public Map<String,Object> createDispatch(DispatchInput in){
    requireProduction(in.productionId());validateLocations(in.sourceLocationId(),in.destinationLocationId());UUID id=UUID.randomUUID();String ref=reference("DSP");
    jdbc.update("INSERT INTO hq_dispatches(id,organization_id,reference,production_id,source_location_id,destination_location_id,scheduled_at,notes,created_by) VALUES(?,?,?,?,?,?,?,?,?)",id,ORGANIZATION,ref,in.productionId(),in.sourceLocationId(),in.destinationLocationId(),in.scheduledAt()==null?null:ts(in.scheduledAt()),in.notes(),actor());
    for(var line:in.lines()){validateTrackingLine(line.equipmentId(),line.assetId(),line.quantity());jdbc.update("INSERT INTO hq_dispatch_lines(id,dispatch_id,equipment_id,asset_id,quantity) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,line.equipmentId(),line.assetId(),line.quantity());}
    audit.record("HQ_DISPATCH","HEADQUARTERS_DISPATCH_CREATED",id.toString(),null,Map.of("reference",ref,"lineCount",in.lines().size()));return Map.of("id",id,"reference",ref,"status","DRAFT");
  }

  @Transactional public Map<String,Object> confirmDispatch(UUID id,UUID key){
    var d=one("SELECT * FROM hq_dispatches WHERE id=? AND organization_id=? FOR UPDATE",id,ORGANIZATION,"DISPATCH_NOT_FOUND");
    if("DISPATCHED".equals(d.get("status")))return operationView("hq_dispatches",id);
    if(jdbc.queryForObject("SELECT count(*) FROM hq_inventory_movements WHERE organization_id=? AND dispatch_id=? AND idempotency_key=?",Long.class,ORGANIZATION,id,key)>0)return operationView("hq_dispatches",id);
    var lines=jdbc.queryForList("SELECT * FROM hq_dispatch_lines WHERE dispatch_id=? ORDER BY equipment_id",id);
    for(var l:lines){var equipment=(UUID)l.get("equipment_id");var e=requireEquipment(equipment,true);var q=(BigDecimal)l.get("quantity");UUID asset=(UUID)l.get("asset_id");if(asset!=null)lockAsset(asset,equipment,(UUID)d.get("source_location_id"));move(equipment,(UUID)d.get("source_location_id"),(UUID)d.get("destination_location_id"),(String)e.get("ownership_default"),"GOOD",q);if(asset!=null)jdbc.update("UPDATE hq_serialized_assets SET location_id=?,availability='DEPLOYED' WHERE id=?",d.get("destination_location_id"),asset);movement("DISPATCHED",equipment,q,(UUID)d.get("source_location_id"),(UUID)d.get("destination_location_id"),(UUID)d.get("production_id"),null,id,null,null,null,lineKey(key,(UUID)l.get("id"),"DISPATCHED"),"Dispatch "+d.get("reference"));}
    jdbc.update("UPDATE hq_dispatches SET status='DISPATCHED',confirmed_at=now(),version=version+1 WHERE id=?",id);audit.record("HQ_DISPATCH","HEADQUARTERS_DISPATCH_CONFIRMED",id.toString(),null,Map.of("reference",d.get("reference"),"idempotencyKey",key));return operationView("hq_dispatches",id);
  }

  @Transactional public Map<String,Object> createTransfer(TransferInput in){
    validateLocations(in.sourceLocationId(),in.destinationLocationId());if(in.sourceProductionId()!=null)requireProduction(in.sourceProductionId());if(in.destinationProductionId()!=null)requireProduction(in.destinationProductionId());UUID id=UUID.randomUUID();String ref=reference("TRF");
    jdbc.update("INSERT INTO hq_transfers(id,organization_id,reference,source_production_id,destination_production_id,source_location_id,destination_location_id,notes,created_by) VALUES(?,?,?,?,?,?,?,?,?)",id,ORGANIZATION,ref,in.sourceProductionId(),in.destinationProductionId(),in.sourceLocationId(),in.destinationLocationId(),in.notes(),actor());
    for(var line:in.lines()){validateTrackingLine(line.equipmentId(),line.assetId(),line.quantity());jdbc.update("INSERT INTO hq_transfer_lines(id,transfer_id,equipment_id,asset_id,quantity) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,line.equipmentId(),line.assetId(),line.quantity());}return Map.of("id",id,"reference",ref,"status","DRAFT");
  }

  @Transactional public Map<String,Object> confirmTransfer(UUID id,UUID key){
    var t=one("SELECT * FROM hq_transfers WHERE id=? AND organization_id=? FOR UPDATE",id,ORGANIZATION,"TRANSFER_NOT_FOUND");if("CONFIRMED".equals(t.get("status")))return operationView("hq_transfers",id);
    var lines=jdbc.queryForList("SELECT * FROM hq_transfer_lines WHERE transfer_id=? ORDER BY equipment_id",id);
    for(var l:lines){var equipment=(UUID)l.get("equipment_id");var e=requireEquipment(equipment,true);var q=(BigDecimal)l.get("quantity");UUID asset=(UUID)l.get("asset_id");if(asset!=null)lockAsset(asset,equipment,(UUID)t.get("source_location_id"));move(equipment,(UUID)t.get("source_location_id"),(UUID)t.get("destination_location_id"),(String)e.get("ownership_default"),"GOOD",q);if(asset!=null)jdbc.update("UPDATE hq_serialized_assets SET location_id=?,availability='IN_TRANSIT' WHERE id=?",t.get("destination_location_id"),asset);movement("TRANSFERRED",equipment,q,(UUID)t.get("source_location_id"),(UUID)t.get("destination_location_id"),(UUID)t.get("destination_production_id"),null,null,id,null,null,lineKey(key,(UUID)l.get("id"),"TRANSFERRED"),"Transfer "+t.get("reference"));}
    jdbc.update("UPDATE hq_transfers SET status='CONFIRMED',confirmed_at=now() WHERE id=?",id);audit.record("HQ_TRANSFER","HEADQUARTERS_TRANSFER_CONFIRMED",id.toString(),null,Map.of("reference",t.get("reference"),"idempotencyKey",key));return operationView("hq_transfers",id);
  }

  @Transactional public Map<String,Object> createReturn(ReturnInput in){
    requireProduction(in.productionId());validateLocations(in.sourceLocationId(),in.destinationLocationId());UUID id=UUID.randomUUID();String ref=reference("RET");
    jdbc.update("INSERT INTO hq_returns(id,organization_id,reference,production_id,source_location_id,destination_location_id,notes,created_by) VALUES(?,?,?,?,?,?,?,?)",id,ORGANIZATION,ref,in.productionId(),in.sourceLocationId(),in.destinationLocationId(),in.notes(),actor());
    for(var l:in.lines()){validateTrackingLine(l.equipmentId(),l.assetId(),l.expected());var accounted=nz(l.returned()).add(nz(l.transferred())).add(nz(l.consumed())).add(nz(l.damaged())).add(nz(l.missing()));if(accounted.compareTo(l.expected())>0)throw ApiException.badRequest("RETURN_OVER_ACCOUNTED","Accounted quantity cannot exceed expected quantity.");jdbc.update("INSERT INTO hq_return_lines(id,return_id,equipment_id,asset_id,expected_quantity,returned_quantity,transferred_quantity,consumed_quantity,damaged_quantity,missing_quantity) VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,l.equipmentId(),l.assetId(),l.expected(),nz(l.returned()),nz(l.transferred()),nz(l.consumed()),nz(l.damaged()),nz(l.missing()));}
    return Map.of("id",id,"reference",ref,"status","DRAFT");
  }

  @Transactional public Map<String,Object> confirmReturn(UUID id,UUID key){
    var r=one("SELECT * FROM hq_returns WHERE id=? AND organization_id=? FOR UPDATE",id,ORGANIZATION,"RETURN_NOT_FOUND");if(Set.of("PARTIAL","RECONCILED").contains(r.get("status")))return operationView("hq_returns",id);
    var lines=jdbc.queryForList("SELECT * FROM hq_return_lines WHERE return_id=? ORDER BY equipment_id",id);BigDecimal expected=BigDecimal.ZERO,accounted=BigDecimal.ZERO;
    for(var l:lines){var equipment=(UUID)l.get("equipment_id");var e=requireEquipment(equipment,true);var own=(String)e.get("ownership_default");UUID asset=(UUID)l.get("asset_id");if(asset!=null)lockAsset(asset,equipment,(UUID)r.get("source_location_id"));BigDecimal returned=(BigDecimal)l.get("returned_quantity");BigDecimal damaged=(BigDecimal)l.get("damaged_quantity");BigDecimal missing=(BigDecimal)l.get("missing_quantity");BigDecimal consumed=(BigDecimal)l.get("consumed_quantity");BigDecimal transferred=(BigDecimal)l.get("transferred_quantity");expected=expected.add((BigDecimal)l.get("expected_quantity"));accounted=accounted.add(returned).add(damaged).add(missing).add(consumed).add(transferred);
      UUID lineId=(UUID)l.get("id");
      if(returned.signum()>0){move(equipment,(UUID)r.get("source_location_id"),(UUID)r.get("destination_location_id"),own,"GOOD",returned);movement("RETURNED",equipment,returned,(UUID)r.get("source_location_id"),(UUID)r.get("destination_location_id"),(UUID)r.get("production_id"),null,null,null,id,null,lineKey(key,lineId,"RETURNED"),"Return "+r.get("reference"));}
      if(damaged.signum()>0){conditionMove(equipment,(UUID)r.get("source_location_id"),(UUID)r.get("destination_location_id"),own,"DAMAGED",damaged);createIssue("DAMAGE",equipment,damaged,(UUID)r.get("destination_location_id"),(UUID)r.get("production_id"),"REPAIR_REQUIRED","Reported during "+r.get("reference"));movement("RETURNED",equipment,damaged,(UUID)r.get("source_location_id"),(UUID)r.get("destination_location_id"),(UUID)r.get("production_id"),null,null,null,id,null,lineKey(key,lineId,"DAMAGED"),"Damaged return "+r.get("reference"));}
      if(missing.signum()>0){conditionMove(equipment,(UUID)r.get("source_location_id"),(UUID)r.get("source_location_id"),own,"MISSING",missing);createIssue("MISSING",equipment,missing,(UUID)r.get("source_location_id"),(UUID)r.get("production_id"),null,"Unresolved during "+r.get("reference"));movement("MARKED_MISSING",equipment,missing,(UUID)r.get("source_location_id"),null,(UUID)r.get("production_id"),null,null,null,id,null,lineKey(key,lineId,"MISSING"),"Missing from "+r.get("reference"));}
      if(consumed.signum()>0){positionDelta(equipment,(UUID)r.get("source_location_id"),own,"GOOD",consumed.negate());movement("CONSUMED",equipment,consumed,(UUID)r.get("source_location_id"),null,(UUID)r.get("production_id"),null,null,null,id,null,lineKey(key,lineId,"CONSUMED"),"Consumed during "+r.get("reference"));}
      if(asset!=null){String condition=missing.signum()>0?"MISSING":damaged.signum()>0?"DAMAGED":"GOOD";String availability=missing.signum()>0||damaged.signum()>0?"UNAVAILABLE":"AVAILABLE";UUID assetLocation=returned.signum()>0||damaged.signum()>0?(UUID)r.get("destination_location_id"):(UUID)r.get("source_location_id");jdbc.update("UPDATE hq_serialized_assets SET location_id=?,condition=?,availability=? WHERE id=?",assetLocation,condition,availability,asset);}
    }
    var unresolved=expected.subtract(accounted);var status=unresolved.signum()==0?"RECONCILED":"PARTIAL";jdbc.update("UPDATE hq_returns SET status=?,confirmed_at=now() WHERE id=?",status,id);
    if(unresolved.signum()>0)attention("RETURN:"+id,"UNACCOUNTED_RETURN","CRITICAL","Return is not fully accounted","Return "+r.get("reference")+" has "+unresolved.stripTrailingZeros().toPlainString()+" units unresolved","RETURN",id);else resolveAttention("RETURN:"+id);
    audit.record("HQ_RETURN","HEADQUARTERS_RETURN_CONFIRMED",id.toString(),null,Map.of("reference",r.get("reference"),"status",status,"unresolved",unresolved,"idempotencyKey",key));return operationView("hq_returns",id);
  }

  @Transactional public Map<String,Object> issue(IssueInput in){validateEnum(in.issueType(),Set.of("DAMAGE","MISSING"),"issueType");var e=requireEquipment(in.equipmentId(),true);if(in.locationId()!=null)conditionTransition(in.equipmentId(),in.locationId(),(String)e.get("ownership_default"),"GOOD","MISSING".equals(in.issueType())?"MISSING":"DAMAGED",in.quantity());var id=createIssue(in.issueType(),in.equipmentId(),in.quantity(),in.locationId(),in.productionId(),in.severity(),in.notes());if("MISSING".equals(in.issueType()))movement("MARKED_MISSING",in.equipmentId(),in.quantity(),in.locationId(),null,in.productionId(),null,null,null,null,id,id,"Missing equipment case opened");audit.record("HQ_ISSUE","HEADQUARTERS_ISSUE_RECORDED",id.toString(),null,Map.of("type",in.issueType(),"equipmentId",in.equipmentId(),"quantity",in.quantity()));return Map.of("id",id,"status","OPEN");}
  @Transactional public Map<String,Object> resolveIssue(UUID id,ResolveInput in){var issue=one("SELECT * FROM hq_issues WHERE id=? AND organization_id=? FOR UPDATE",id,ORGANIZATION,"ISSUE_NOT_FOUND");if("RESOLVED".equals(issue.get("status")))return Map.of("id",id,"status","RESOLVED");UUID equipment=(UUID)issue.get("equipment_id"),location=(UUID)issue.get("location_id");BigDecimal quantity=(BigDecimal)issue.get("quantity");var e=requireEquipment(equipment,true);String ownership=(String)e.get("ownership_default"),sourceCondition="MISSING".equals(issue.get("issue_type"))?"MISSING":"DAMAGED";if(location!=null){if(Set.of("FOUND","RETURNED","RETURN_TO_SERVICE").contains(in.resolution())){conditionTransition(equipment,location,ownership,sourceCondition,"GOOD",quantity);movement("FOUND",equipment,quantity,location,location,(UUID)issue.get("production_id"),null,null,null,null,id,in.idempotencyKey(),in.notes());}else if("SEND_TO_REPAIR".equals(in.resolution())){conditionTransition(equipment,location,ownership,sourceCondition,"UNDER_REPAIR",quantity);movement("SENT_TO_REPAIR",equipment,quantity,location,location,(UUID)issue.get("production_id"),null,null,null,null,id,in.idempotencyKey(),in.notes());}else if("CONFIRMED_LOST".equals(in.resolution())){positionDelta(equipment,location,ownership,"MISSING",quantity.negate());movement("RETIRED",equipment,quantity,location,null,(UUID)issue.get("production_id"),null,null,null,null,id,in.idempotencyKey(),in.notes());}}
    jdbc.update("UPDATE hq_issues SET status='RESOLVED',resolution=?,notes=concat(notes,E'\\n',?),resolved_at=now() WHERE id=?",in.resolution(),in.notes(),id);resolveAttention("ISSUE:"+id);audit.record("HQ_ISSUE","HEADQUARTERS_ISSUE_RESOLVED",id.toString(),null,Map.of("resolution",in.resolution(),"idempotencyKey",in.idempotencyKey()));return Map.of("id",id,"status","RESOLVED","resolution",in.resolution());}

  @Transactional public Map<String,Object> maintenance(MaintenanceInput in){var e=requireEquipment(in.equipmentId(),true);String ownership=(String)e.get("ownership_default");conditionTransition(in.equipmentId(),in.locationId(),ownership,"GOOD","UNDER_REPAIR",in.quantity());var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_maintenance(id,organization_id,equipment_id,asset_id,maintenance_type,quantity,status,due_at,notes,created_by) VALUES(?,?,?,?,?,?,'IN_PROGRESS',?,?,?)",id,ORGANIZATION,in.equipmentId(),in.assetId(),in.maintenanceType(),in.quantity(),in.dueAt()==null?null:ts(in.dueAt()),in.notes(),actor());movement("SENT_TO_REPAIR",in.equipmentId(),in.quantity(),in.locationId(),in.locationId(),null,null,null,null,null,null,in.idempotencyKey(),in.notes());audit.record("HQ_MAINTENANCE","HEADQUARTERS_MAINTENANCE_STARTED",id.toString(),null,Map.of("equipmentId",in.equipmentId(),"quantity",in.quantity()));return Map.of("id",id,"status","IN_PROGRESS");}
  @Transactional public Map<String,Object> completeMaintenance(UUID id,UUID key){var m=one("SELECT * FROM hq_maintenance WHERE id=? AND organization_id=? FOR UPDATE",id,ORGANIZATION,"MAINTENANCE_NOT_FOUND");if("COMPLETED".equals(m.get("status")))return Map.of("id",id,"status","COMPLETED");UUID equipment=(UUID)m.get("equipment_id");var e=requireEquipment(equipment,true);var locations=jdbc.query("SELECT location_id FROM hq_inventory_positions WHERE organization_id=? AND equipment_id=? AND condition='UNDER_REPAIR' AND physical_quantity>=? ORDER BY updated_at DESC LIMIT 1",(rs,row)->rs.getObject("location_id",UUID.class),ORGANIZATION,equipment,m.get("quantity"));if(locations.isEmpty())throw ApiException.conflict("MAINTENANCE_POSITION_MISSING","Repair custody could not be reconciled.");UUID location=locations.getFirst();conditionTransition(equipment,location,(String)e.get("ownership_default"),"UNDER_REPAIR","GOOD",(BigDecimal)m.get("quantity"));movement("RETURNED_FROM_REPAIR",equipment,(BigDecimal)m.get("quantity"),location,location,null,null,null,null,null,null,key,"Maintenance completed");jdbc.update("UPDATE hq_maintenance SET status='COMPLETED',completed_at=now() WHERE id=?",id);audit.record("HQ_MAINTENANCE","HEADQUARTERS_MAINTENANCE_COMPLETED",id.toString(),null,Map.of("idempotencyKey",key));return Map.of("id",id,"status","COMPLETED");}

  @Transactional public Map<String,Object> reconcile(){
    var mismatches=jdbc.queryForList("""
      WITH entries AS (
        SELECT equipment_id,destination_location_id location_id,quantity delta FROM hq_inventory_movements
          WHERE organization_id=? AND destination_location_id IS NOT NULL
        UNION ALL
        SELECT equipment_id,source_location_id,-quantity delta FROM hq_inventory_movements
          WHERE organization_id=? AND source_location_id IS NOT NULL AND movement_type IN ('DISPATCHED','TRANSFERRED','RETURNED','CONSUMED','RETIRED')
      ), ledger AS (
        SELECT equipment_id,location_id,sum(delta) qty FROM entries GROUP BY equipment_id,location_id
      ) SELECT l.equipment_id "equipmentId",l.location_id "locationId",l.qty,COALESCE(sum(p.physical_quantity),0) projected
        FROM ledger l LEFT JOIN hq_inventory_positions p ON p.organization_id=? AND p.equipment_id=l.equipment_id AND p.location_id=l.location_id
        GROUP BY l.equipment_id,l.location_id,l.qty HAVING l.qty<>COALESCE(sum(p.physical_quantity),0)
      """,ORGANIZATION,ORGANIZATION,ORGANIZATION);
    if(mismatches.isEmpty())resolveAttention("PROJECTION:GLOBAL");else attention("PROJECTION:GLOBAL","PROJECTION_RECONCILIATION_FAILURE","CRITICAL","Inventory projection needs reconciliation",mismatches.size()+" position records disagree with ledger evidence","SYSTEM",null);
    return Map.of("consistent",mismatches.isEmpty(),"mismatches",mismatches.size());
  }

  private Map<String,Object> requireEquipment(UUID id,boolean lock){return one("SELECT * FROM hq_equipment WHERE id=? AND organization_id=?"+(lock?" FOR UPDATE":""),id,ORGANIZATION,"EQUIPMENT_NOT_FOUND");}
  private void validateTrackingLine(UUID equipment,UUID asset,BigDecimal quantity){var e=requireEquipment(equipment,false);boolean serialized="SERIALIZED".equals(e.get("tracking_mode"));if(serialized&&(asset==null||quantity.compareTo(BigDecimal.ONE)!=0))throw ApiException.badRequest("SERIALIZED_ASSET_REQUIRED","Serialized equipment requires one specific asset per line.");if(!serialized&&asset!=null)throw ApiException.badRequest("ASSET_NOT_ALLOWED","An asset identifier is only valid for serialized equipment.");}
  private void lockAsset(UUID asset,UUID equipment,UUID location){var rows=jdbc.queryForList("SELECT id FROM hq_serialized_assets WHERE id=? AND organization_id=? AND equipment_id=? AND location_id=? AND retired_at IS NULL FOR UPDATE",asset,ORGANIZATION,equipment,location);if(rows.isEmpty())throw ApiException.conflict("ASSET_CUSTODY_CHANGED","The serialized asset is no longer at the selected source location.");}
  private void requireProduction(UUID id){Long n=jdbc.queryForObject("SELECT count(*) FROM productions WHERE id=?",Long.class,id);if(n==null||n==0)throw ApiException.notFound("PRODUCTION_NOT_FOUND","Production was not found.");}
  private void validateLocations(UUID source,UUID destination){if(source.equals(destination))throw ApiException.badRequest("LOCATION_UNCHANGED","Source and destination must be different.");Long n=jdbc.queryForObject("SELECT count(*) FROM hq_locations WHERE organization_id=? AND id IN (?,?)",Long.class,ORGANIZATION,source,destination);if(n==null||n!=2)throw ApiException.notFound("LOCATION_NOT_FOUND","Inventory location was not found.");}
  private Map<String,Object> one(String sql,Object a,Object b,String code){var rows=jdbc.queryForList(sql,a,b);if(rows.isEmpty())throw ApiException.notFound(code,"Headquarters record was not found.");return rows.getFirst();}
  private BigDecimal usable(UUID equipment){return scalar("SELECT COALESCE(sum(physical_quantity-unavailable_quantity),0) FROM hq_inventory_positions WHERE organization_id=? AND equipment_id=? AND condition='GOOD'",ORGANIZATION,equipment);}
  private BigDecimal committed(UUID equipment,Instant start,Instant end,UUID exclude){return scalar("""
    SELECT COALESCE(sum(rl.quantity),0) FROM hq_reservation_lines rl JOIN hq_reservations r ON r.id=rl.reservation_id
    WHERE r.organization_id=? AND rl.equipment_id=? AND r.status IN ('CONFIRMED','PARTIALLY_FULFILLED') AND r.starts_at<? AND r.ends_at>? AND (?::uuid IS NULL OR r.id<>?::uuid)
    """,ORGANIZATION,equipment,ts(end),ts(start),exclude,exclude);}
  private BigDecimal scalar(String sql,Object... args){var n=jdbc.queryForObject(sql,BigDecimal.class,args);return n==null?BigDecimal.ZERO:n;}
  private void positionDelta(UUID equipment,UUID location,String ownership,String condition,BigDecimal delta){
    jdbc.update("INSERT INTO hq_inventory_positions(organization_id,equipment_id,location_id,ownership,condition,physical_quantity) VALUES(?,?,?,?,?,0) ON CONFLICT DO NOTHING",ORGANIZATION,equipment,location,ownership,condition);
    int updated=jdbc.update("UPDATE hq_inventory_positions SET physical_quantity=physical_quantity+?,version=version+1,updated_at=now() WHERE organization_id=? AND equipment_id=? AND location_id=? AND ownership=? AND condition=? AND physical_quantity+?>=0",delta,ORGANIZATION,equipment,location,ownership,condition,delta);
    if(updated!=1)throw ApiException.conflict("INSUFFICIENT_STOCK","The source location no longer has enough usable stock.",Map.of("equipmentId",equipment.toString(),"locationId",location.toString()));
  }
  private void move(UUID equipment,UUID from,UUID to,String ownership,String condition,BigDecimal q){positionDelta(equipment,from,ownership,condition,q.negate());positionDelta(equipment,to,ownership,condition,q);}
  private void conditionMove(UUID equipment,UUID from,UUID to,String ownership,String condition,BigDecimal q){positionDelta(equipment,from,ownership,"GOOD",q.negate());positionDelta(equipment,to,ownership,condition,q);}
  private void conditionTransition(UUID equipment,UUID location,String ownership,String fromCondition,String toCondition,BigDecimal q){positionDelta(equipment,location,ownership,fromCondition,q.negate());positionDelta(equipment,location,ownership,toCondition,q);}
  private void movement(String type,UUID equipment,BigDecimal q,UUID from,UUID to,UUID production,UUID reservation,UUID dispatch,UUID transfer,UUID returns,UUID issue,UUID key,String reason){jdbc.update("""
    INSERT INTO hq_inventory_movements(organization_id,movement_type,equipment_id,quantity,source_location_id,destination_location_id,production_id,reservation_id,dispatch_id,transfer_id,return_id,issue_id,idempotency_key,recorded_by,reason)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """,ORGANIZATION,type,equipment,q,from,to,production,reservation,dispatch,transfer,returns,issue,key,actor(),reason);}
  private boolean existing(UUID key,UUID equipment,String type){Long n=jdbc.queryForObject("SELECT count(*) FROM hq_inventory_movements WHERE organization_id=? AND idempotency_key=? AND equipment_id=? AND movement_type=?",Long.class,ORGANIZATION,key,equipment,type);return n!=null&&n>0;}
  private Map<UUID,BigDecimal> aggregate(List<ReservationLine> lines){var result=new TreeMap<UUID,BigDecimal>();for(var l:lines)result.merge(l.equipmentId(),l.quantity(),BigDecimal::add);return result;}
  private Map<String,Object> operationView(String table,UUID id){return jdbc.queryForMap("SELECT id,reference,status,confirmed_at \"confirmedAt\" FROM "+table+" WHERE id=? AND organization_id=?",id,ORGANIZATION);}
  private UUID createIssue(String type,UUID equipment,BigDecimal quantity,UUID location,UUID production,String severity,String notes){var id=UUID.randomUUID();jdbc.update("INSERT INTO hq_issues(id,organization_id,issue_type,equipment_id,quantity,location_id,production_id,severity,notes,reported_by) VALUES(?,?,?,?,?,?,?,?,?,?)",id,ORGANIZATION,type,equipment,quantity,location,production,severity,notes,actor());attention("ISSUE:"+id,type.equals("MISSING")?"MISSING_EQUIPMENT":"DAMAGED_AWAITING_DECISION",type.equals("MISSING")?"CRITICAL":"WARNING",type.equals("MISSING")?"Equipment is missing":"Damaged equipment needs a decision",quantity.stripTrailingZeros().toPlainString()+" units require owner action","ISSUE",id);return id;}
  private void attention(String key,String type,String severity,String title,String detail,String entityType,UUID entityId){jdbc.update("""
    INSERT INTO hq_attention(organization_id,attention_key,attention_type,severity,title,detail,entity_type,entity_id)
    VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(organization_id,attention_key) DO UPDATE SET attention_type=excluded.attention_type,severity=excluded.severity,title=excluded.title,detail=excluded.detail,status='OPEN',resolved_at=NULL
    """,ORGANIZATION,key,type,severity,title,detail,entityType,entityId);}
  private void resolveAttention(String key){jdbc.update("UPDATE hq_attention SET status='RESOLVED',resolved_at=now() WHERE organization_id=? AND attention_key=? AND status='OPEN'",ORGANIZATION,key);}
  private static void validateWindow(Instant start,Instant end){if(!end.isAfter(start))throw ApiException.badRequest("INVALID_TIME_WINDOW","End time must be after start time.");}
  private static void validateEnum(String value,Set<String> values,String field){if(!values.contains(value))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Please review the highlighted fields.",Map.of(field,"Unsupported value."));}
  private static BigDecimal nz(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
  private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
  private static String reference(String prefix){return prefix+"-"+Instant.now().toEpochMilli()+"-"+UUID.randomUUID().toString().substring(0,4).toUpperCase();}
  private static UUID lineKey(UUID operationKey,UUID lineId,String effect){return UUID.nameUUIDFromBytes((operationKey+":"+lineId+":"+effect).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  private static java.sql.Timestamp ts(Instant value){return java.sql.Timestamp.from(value);}
  private static String actor(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null||a.getName()==null?"system":a.getName();}
  private static ApiException availabilityChanged(BigDecimal requested,BigDecimal available){return ApiException.conflict("AVAILABILITY_CHANGED","Availability changed. "+available.stripTrailingZeros().toPlainString()+" units are available now.",Map.of("requested",requested.toPlainString(),"available",available.max(BigDecimal.ZERO).toPlainString()));}
  private static Map<String,Object> equipmentRow(ResultSet rs) throws java.sql.SQLException {BigDecimal usable=rs.getBigDecimal("usable");BigDecimal reserved=rs.getBigDecimal("reserved");var m=new LinkedHashMap<String,Object>();m.put("id",rs.getObject("id",UUID.class));m.put("name",rs.getString("name"));m.put("internalCode",rs.getString("internal_code"));m.put("category",rs.getString("category"));m.put("trackingMode",rs.getString("tracking_mode"));m.put("symbol",rs.getString("symbol"));m.put("ownership",rs.getString("ownership_default"));m.put("active",rs.getBoolean("active"));m.put("controlled",rs.getBigDecimal("controlled"));m.put("reserved",reserved);m.put("available",usable.subtract(reserved).max(BigDecimal.ZERO));m.put("updatedAt",rs.getObject("updated_at"));return m;}
}
