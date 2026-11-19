package com.company.erp.operations;

import static com.company.erp.operations.OperationsModels.*;

import com.company.erp.audit.AuditService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperationsService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditService audit;
    private final OperationsAccess access;

    public OperationsService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, AuditService audit, OperationsAccess access) {
        this.jdbc=jdbc; this.namedJdbc=namedJdbc; this.audit=audit; this.access=access;
    }

    public OperationsSummary summary(UUID companyId, Authentication authentication) {
        long suppliers=count("suppliers",companyId,"status='ACTIVE'");
        long pending=purchaseRequests(companyId,null,authentication).stream().filter(r->Set.of("DRAFT","SUBMITTED").contains(text(r.get("status")))).count();
        long orders=count("purchase_orders",companyId,"status in ('DRAFT','SUBMITTED','APPROVED','PARTIALLY_RECEIVED')");
        long lowStock=jdbc.sql("select count(*) from ops_stock_levels s join ops_inventory_items i on i.id=s.inventory_item_id where s.company_id=:companyId and i.active=true and s.on_hand-s.committed<=i.min_stock")
            .param("companyId",companyId.toString()).query(Long.class).single();
        long assets=assets(companyId,null,authentication).stream().filter(r->"ASSIGNED".equals(text(r.get("status")))).count();
        long maintenance=jdbc.sql("select count(*) from ops_maintenance_orders where company_id=:companyId and status not in ('COMPLETED','CANCELLED') and scheduled_on<=current_date+7")
            .param("companyId",companyId.toString()).query(Long.class).single();
        long licenses=jdbc.sql("select count(*) from ops_software_licenses where company_id=:companyId and status='ACTIVE' and renews_on<=current_date+30")
            .param("companyId",companyId.toString()).query(Long.class).single();
        long shipments=count("ops_shipments",companyId,"status='IN_TRANSIT'");
        return new OperationsSummary(suppliers,pending,orders,lowStock,assets,maintenance,licenses,shipments);
    }

    public List<Map<String,Object>> references(UUID companyId,String kind) {
        return switch(kind) {
            case "departments" -> rows("select id,code,name from departments where company_id=:companyId and status='ACTIVE' order by name",params(companyId));
            case "projects" -> rows("select id,code,name from pm_projects where company_id=:companyId order by name",params(companyId));
            case "budgets" -> rows("select id,code,name,currency,planned_amount,committed_amount,actual_amount from finance_budgets where company_id=:companyId and status='ACTIVE' order by period_end",params(companyId));
            case "employees" -> rows("select id,employee_code code,full_name name,department_id from employees where company_id=:companyId and employment_status='ACTIVE' order by full_name",params(companyId));
            case "suppliers" -> rows("select id,code,name from suppliers where company_id=:companyId and status='ACTIVE' order by name",params(companyId));
            case "warehouses" -> rows("select id,code,name from warehouses where company_id=:companyId and status='ACTIVE' order by name",params(companyId));
            case "items" -> rows("select id,sku code,name,unit,standard_cost,currency from ops_inventory_items where company_id=:companyId and active=true order by name",params(companyId));
            case "purchase-requests" -> rows("select id,request_number code,purpose name,currency,estimated_total from purchase_requests where company_id=:companyId and status='APPROVED' order by created_at desc",params(companyId));
            case "purchase-orders" -> rows("select id,po_number code,po_number name,total,currency from purchase_orders where company_id=:companyId and status in ('APPROVED','PARTIALLY_RECEIVED') order by created_at desc",params(companyId));
            case "sales-orders" -> rows("select id,order_number code,order_number name,total,currency from crm_sales_orders where company_id=:companyId order by created_at desc",params(companyId));
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reference not found");
        };
    }

    public List<Map<String,Object>> suppliers(UUID companyId,String status) {
        Map<String,Object> p=params(companyId); String filter=" where s.company_id=:companyId";
        if(notBlank(status)){filter+=" and s.status=:status";p.put("status",status);}
        return rows("""
            select s.id,s.code,s.name,s.tax_code,s.email,s.phone,s.address,s.category,s.payment_terms,s.lead_time_days,
              s.status,s.notes,s.created_at,coalesce(avg((coalesce(r.quality_score,0)+coalesce(r.delivery_score,0)+coalesce(r.cost_score,0)+coalesce(r.service_score,0))/4),0) rating
            from suppliers s left join supplier_performance_reviews r on r.supplier_id=s.id
            """+filter+" group by s.id order by s.name",p);
    }

    @Transactional
    public CreatedResource createSupplier(UUID companyId,SupplierRequest request,String actor) {
        UUID id=UUID.randomUUID(); String code=notBlank(request.code())?request.code().trim():nextCode("suppliers","code","SUP",companyId);
        jdbc.sql("""
            insert into suppliers(id,company_id,code,name,tax_code,email,phone,address,category,payment_terms,lead_time_days,status,bank_details,notes)
            values(:id,:companyId,:code,:name,:tax,:email,:phone,:address,:category,:terms,:lead,:status,:bank,:notes)
            """).param("id",id.toString()).param("companyId",companyId.toString()).param("code",code).param("name",request.name().trim())
            .param("tax",blank(request.taxCode())).param("email",blank(request.email())).param("phone",blank(request.phone())).param("address",blank(request.address()))
            .param("category",blank(request.category())).param("terms",blank(request.paymentTerms())).param("lead",request.leadTimeDays())
            .param("status",blank(request.status())==null?"ACTIVE":request.status()).param("bank",blank(request.bankDetails())).param("notes",blank(request.notes())).update();
        audit.write(companyId,actor,"SUPPLIER_CREATED","SUPPLIER",id.toString(),"{}"); return new CreatedResource(id,"ACTIVE");
    }

    public List<Map<String,Object>> supplierReviews(UUID companyId,UUID supplierId) {
        ensure(companyId,"suppliers",supplierId,"Supplier");
        return rows("select id,period_start,period_end,quality_score,delivery_score,cost_score,service_score,notes,reviewed_by_subject,created_at from supplier_performance_reviews where company_id=:companyId and supplier_id=:supplier order by period_end desc",params(companyId,"supplier",supplierId));
    }

    @Transactional
    public CreatedResource addSupplierReview(UUID companyId,UUID supplierId,SupplierReviewRequest request,String actor) {
        ensure(companyId,"suppliers",supplierId,"Supplier"); if(request.periodEnd().isBefore(request.periodStart()))bad("Review period end must be on or after start"); UUID id=UUID.randomUUID();
        jdbc.sql("insert into supplier_performance_reviews(id,company_id,supplier_id,period_start,period_end,quality_score,delivery_score,cost_score,service_score,notes,reviewed_by_subject) values(:id,:companyId,:supplier,:start,:end,:quality,:delivery,:cost,:service,:notes,:actor)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("supplier",supplierId.toString()).param("start",request.periodStart()).param("end",request.periodEnd())
            .param("quality",request.qualityScore()).param("delivery",request.deliveryScore()).param("cost",request.costScore()).param("service",request.serviceScore()).param("notes",blank(request.notes())).param("actor",actor).update();
        audit.write(companyId,actor,"SUPPLIER_REVIEWED","SUPPLIER",supplierId.toString(),"{}"); return new CreatedResource(id,"RECORDED");
    }

    public List<Map<String,Object>> warehouses(UUID companyId) {
        return rows("select w.id,w.code,w.name,w.address,w.status,w.created_at,count(distinct s.inventory_item_id) item_count,coalesce(sum(s.on_hand),0) total_units from warehouses w left join ops_stock_levels s on s.warehouse_id=w.id where w.company_id=:companyId group by w.id order by w.name",params(companyId));
    }

    @Transactional
    public CreatedResource createWarehouse(UUID companyId,WarehouseRequest request,String actor) {
        UUID id=UUID.randomUUID();jdbc.sql("insert into warehouses(id,company_id,code,name,address,status) values(:id,:companyId,:code,:name,:address,'ACTIVE')")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("code",request.code()).param("name",request.name()).param("address",blank(request.address())).update();
        audit.write(companyId,actor,"WAREHOUSE_CREATED","WAREHOUSE",id.toString(),"{}");return new CreatedResource(id,"ACTIVE");
    }

    public List<Map<String,Object>> inventoryItems(UUID companyId) {
        return rows("""
            select i.id,i.sku,i.name,i.item_type,i.unit,i.min_stock,i.standard_cost,i.currency,i.active,
              coalesce(sum(s.on_hand),0) on_hand,coalesce(sum(s.committed),0) committed,
              coalesce(sum(s.on_hand-s.committed),0) available,
              case when coalesce(sum(s.on_hand-s.committed),0)<=i.min_stock then true else false end low_stock
            from ops_inventory_items i left join ops_stock_levels s on s.inventory_item_id=i.id
            where i.company_id=:companyId group by i.id order by i.name
            """,params(companyId));
    }

    @Transactional
    public CreatedResource createInventoryItem(UUID companyId,InventoryItemRequest request,String actor) {
        UUID id=UUID.randomUUID();jdbc.sql("insert into ops_inventory_items(id,company_id,sku,name,item_type,unit,min_stock,standard_cost,currency) values(:id,:companyId,:sku,:name,:type,:unit,:min,:cost,:currency)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("sku",request.sku()).param("name",request.name()).param("type",request.itemType())
            .param("unit",request.unit()).param("min",zero(request.minStock())).param("cost",zero(request.standardCost())).param("currency",request.currency()).update();
        audit.write(companyId,actor,"INVENTORY_ITEM_CREATED","INVENTORY_ITEM",id.toString(),"{}");return new CreatedResource(id,"ACTIVE");
    }

    public List<Map<String,Object>> purchaseRequests(UUID companyId,String status,Authentication authentication) {
        Map<String,Object>p=params(companyId);String filter=" where r.company_id=:companyId";if(notBlank(status)){filter+=" and r.status=:status";p.put("status",status);}
        return rows("""
            select r.id,r.request_number,r.requester_subject,r.status,r.required_on,r.purpose,r.estimated_total,r.currency,
              r.department_id,d.name department_name,r.project_id,p.name project_name,r.budget_id,b.name budget_name,
              r.approved_by_subject,r.approved_at,r.decision_note,r.created_at,count(i.id) item_count
            from purchase_requests r left join departments d on d.id=r.department_id left join pm_projects p on p.id=r.project_id
              left join finance_budgets b on b.id=r.budget_id left join ops_purchase_request_items i on i.purchase_request_id=r.id
            """+filter+" group by r.id,d.name,p.name,b.name order by r.created_at desc",p).stream()
            .filter(r->access.canAccessPurchaseRequest(authentication,companyId,id(r.get("id")),"procurement.read")).toList();
    }

    public List<Map<String,Object>> purchaseRequestItems(UUID companyId,UUID requestId) {
        ensure(companyId,"purchase_requests",requestId,"Purchase request");return rows("select i.id,i.inventory_item_id,c.sku,c.name item_name,i.description,i.quantity,i.unit,i.estimated_unit_price,i.quantity*i.estimated_unit_price line_total from ops_purchase_request_items i left join ops_inventory_items c on c.id=i.inventory_item_id where i.company_id=:companyId and i.purchase_request_id=:request order by i.id",params(companyId,"request",requestId));
    }

    @Transactional
    public CreatedResource createPurchaseRequest(UUID companyId,PurchaseRequestCreate request,String actor) {
        ensureOptional(companyId,"departments",request.departmentId(),"Department");ensureOptional(companyId,"pm_projects",request.projectId(),"Project");ensureOptional(companyId,"finance_budgets",request.budgetId(),"Budget");
        BigDecimal total=request.items().stream().map(i->i.quantity().multiply(i.estimatedUnitPrice())).reduce(BigDecimal.ZERO,BigDecimal::add); UUID id=UUID.randomUUID();String number=nextCode("purchase_requests","request_number","PR",companyId);
        jdbc.sql("insert into purchase_requests(id,company_id,request_number,requester_subject,status,required_on,purpose,items,estimated_total,currency,department_id,project_id,budget_id) values(:id,:companyId,:number,:actor,'DRAFT',:required,:purpose,'[]',:total,:currency,:department,:project,:budget)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("actor",actor).param("required",request.requiredOn()).param("purpose",request.purpose())
            .param("total",total).param("currency",request.currency()).param("department",uuid(request.departmentId())).param("project",uuid(request.projectId())).param("budget",uuid(request.budgetId())).update();
        for(PurchaseRequestItemRequest item:request.items()){ensureOptional(companyId,"ops_inventory_items",item.inventoryItemId(),"Inventory item");jdbc.sql("insert into ops_purchase_request_items(id,company_id,purchase_request_id,inventory_item_id,description,quantity,unit,estimated_unit_price) values(:id,:companyId,:request,:item,:description,:quantity,:unit,:price)")
            .param("id",UUID.randomUUID().toString()).param("companyId",companyId.toString()).param("request",id.toString()).param("item",uuid(item.inventoryItemId())).param("description",item.description()).param("quantity",item.quantity()).param("unit",item.unit()).param("price",item.estimatedUnitPrice()).update();}
        audit.write(companyId,actor,"PURCHASE_REQUEST_CREATED","PURCHASE_REQUEST",id.toString(),"{\"total\":"+total+"}");return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource decidePurchaseRequest(UUID companyId,UUID requestId,DecisionRequest request,String actor) {
        ensureStatus(request.status(),Set.of("SUBMITTED","APPROVED","REJECTED","CANCELLED"));Map<String,Object>r=one("select status,budget_id,estimated_total,currency from purchase_requests where company_id=:companyId and id=:id for update",params(companyId,"id",requestId));String current=text(r.get("status"));
        boolean allowed=("DRAFT".equals(current)&&Set.of("SUBMITTED","CANCELLED").contains(request.status()))||("SUBMITTED".equals(current)&&Set.of("APPROVED","REJECTED").contains(request.status()));if(!allowed)bad("Unsupported purchase request status transition");
        if("APPROVED".equals(request.status())&&r.get("budget_id")!=null)reserveBudget(companyId,id(r.get("budget_id")),decimal(r.get("estimated_total")),text(r.get("currency")));
        jdbc.sql("update purchase_requests set status=:status,approved_by_subject=case when :status='APPROVED' then :actor else approved_by_subject end,approved_at=case when :status='APPROVED' then current_timestamp else approved_at end,budget_reserved_amount=case when :status='APPROVED' and budget_id is not null then estimated_total else budget_reserved_amount end,decision_note=:note,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("status",request.status()).param("actor",actor).param("note",blank(request.note())).param("companyId",companyId.toString()).param("id",requestId.toString()).update();
        audit.write(companyId,actor,"PURCHASE_REQUEST_STATUS_CHANGED","PURCHASE_REQUEST",requestId.toString(),meta(request.status(),request.note()));return new CreatedResource(requestId,request.status());
    }

    public List<Map<String,Object>> purchaseOrders(UUID companyId,String status) {
        Map<String,Object>p=params(companyId);String filter=" where o.company_id=:companyId";if(notBlank(status)){filter+=" and o.status=:status";p.put("status",status);}
        return rows("""
            select o.id,o.po_number,o.status,o.currency,o.total,o.expected_on,o.ordered_on,o.received_at,o.supplier_id,s.name supplier_name,
              o.purchase_request_id,r.request_number,o.warehouse_id,w.name warehouse_name,o.approved_by_subject,o.approved_at,o.created_at,
              count(i.id) item_count,coalesce(sum(i.received_quantity),0) received_quantity
            from purchase_orders o join suppliers s on s.id=o.supplier_id left join purchase_requests r on r.id=o.purchase_request_id
              left join warehouses w on w.id=o.warehouse_id left join ops_purchase_order_items i on i.purchase_order_id=o.id
            """+filter+" group by o.id,s.name,r.request_number,w.name order by o.created_at desc",p);
    }

    public List<Map<String,Object>> purchaseOrderItems(UUID companyId,UUID orderId) {
        ensure(companyId,"purchase_orders",orderId,"Purchase order");return rows("select i.id,i.inventory_item_id,c.sku,c.name item_name,i.description,i.quantity,i.received_quantity,i.unit,i.unit_price,i.quantity*i.unit_price line_total from ops_purchase_order_items i left join ops_inventory_items c on c.id=i.inventory_item_id where i.company_id=:companyId and i.purchase_order_id=:order order by i.id",params(companyId,"order",orderId));
    }

    @Transactional
    public CreatedResource createPurchaseOrder(UUID companyId,PurchaseOrderRequest request,String actor) {
        ensure(companyId,"suppliers",request.supplierId(),"Supplier");ensureOptional(companyId,"warehouses",request.warehouseId(),"Warehouse");
        if(request.purchaseRequestId()!=null){Map<String,Object>r=one("select status,currency from purchase_requests where company_id=:companyId and id=:id",params(companyId,"id",request.purchaseRequestId()));if(!"APPROVED".equals(text(r.get("status"))))bad("Purchase request must be approved");if(!text(r.get("currency")).equals(request.currency()))bad("Purchase order currency must match request");long existing=jdbc.sql("select count(*) from purchase_orders where company_id=:companyId and purchase_request_id=:request and status not in ('REJECTED','CANCELLED')").param("companyId",companyId.toString()).param("request",request.purchaseRequestId().toString()).query(Long.class).single();if(existing>0)bad("Purchase request already has an active purchase order");}
        BigDecimal total=request.items().stream().map(i->i.quantity().multiply(i.unitPrice())).reduce(BigDecimal.ZERO,BigDecimal::add);UUID id=UUID.randomUUID();String number=nextCode("purchase_orders","po_number","PO",companyId);
        jdbc.sql("insert into purchase_orders(id,company_id,supplier_id,purchase_request_id,warehouse_id,po_number,status,currency,total,expected_on,items,created_by_subject) values(:id,:companyId,:supplier,:request,:warehouse,:number,'DRAFT',:currency,:total,:expected,'[]',:actor)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("supplier",request.supplierId().toString()).param("request",uuid(request.purchaseRequestId())).param("warehouse",uuid(request.warehouseId())).param("number",number).param("currency",request.currency()).param("total",total).param("expected",request.expectedOn()).param("actor",actor).update();
        for(PurchaseOrderItemRequest item:request.items()){ensureOptional(companyId,"ops_inventory_items",item.inventoryItemId(),"Inventory item");jdbc.sql("insert into ops_purchase_order_items(id,company_id,purchase_order_id,inventory_item_id,description,quantity,unit,unit_price) values(:id,:companyId,:order,:item,:description,:quantity,:unit,:price)")
            .param("id",UUID.randomUUID().toString()).param("companyId",companyId.toString()).param("order",id.toString()).param("item",uuid(item.inventoryItemId())).param("description",item.description()).param("quantity",item.quantity()).param("unit",item.unit()).param("price",item.unitPrice()).update();}
        audit.write(companyId,actor,"PURCHASE_ORDER_CREATED","PURCHASE_ORDER",id.toString(),"{\"total\":"+total+"}");return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource decidePurchaseOrder(UUID companyId,UUID orderId,DecisionRequest request,String actor) {
        ensureStatus(request.status(),Set.of("SUBMITTED","APPROVED","REJECTED","CANCELLED"));Map<String,Object>o=one("select status,purchase_request_id from purchase_orders where company_id=:companyId and id=:id for update",params(companyId,"id",orderId));String current=text(o.get("status"));
        boolean allowed=("DRAFT".equals(current)&&Set.of("SUBMITTED","CANCELLED").contains(request.status()))||("SUBMITTED".equals(current)&&Set.of("APPROVED","REJECTED").contains(request.status()))||("APPROVED".equals(current)&&"CANCELLED".equals(request.status()));if(!allowed)bad("Unsupported purchase order status transition");
        jdbc.sql("update purchase_orders set status=:status,ordered_on=case when :status='APPROVED' then current_date else ordered_on end,approved_by_subject=case when :status='APPROVED' then :actor else approved_by_subject end,approved_at=case when :status='APPROVED' then current_timestamp else approved_at end,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("status",request.status()).param("actor",actor).param("companyId",companyId.toString()).param("id",orderId.toString()).update();
        if("APPROVED".equals(request.status())&&o.get("purchase_request_id")!=null)jdbc.sql("update purchase_requests set status='ORDERED',updated_at=current_timestamp where company_id=:companyId and id=:id").param("companyId",companyId.toString()).param("id",text(o.get("purchase_request_id"))).update();
        audit.write(companyId,actor,"PURCHASE_ORDER_STATUS_CHANGED","PURCHASE_ORDER",orderId.toString(),meta(request.status(),request.note()));return new CreatedResource(orderId,request.status());
    }

    public List<Map<String,Object>> goodsReceipts(UUID companyId) {
        return rows("select r.id,r.receipt_number,r.purchase_order_id,o.po_number,r.warehouse_id,w.name warehouse_name,r.received_on,r.status,r.note,r.received_by_subject,r.created_at,count(l.id) line_count,coalesce(sum(l.quantity),0) total_quantity from ops_goods_receipts r join purchase_orders o on o.id=r.purchase_order_id join warehouses w on w.id=r.warehouse_id left join ops_goods_receipt_lines l on l.goods_receipt_id=r.id where r.company_id=:companyId group by r.id,o.po_number,w.name order by r.received_on desc,r.created_at desc",params(companyId));
    }

    @Transactional
    public CreatedResource receivePurchaseOrder(UUID companyId,UUID orderId,GoodsReceiptRequest request,String actor) {
        ensure(companyId,"warehouses",request.warehouseId(),"Warehouse");Map<String,Object>o=one("select status,purchase_request_id from purchase_orders where company_id=:companyId and id=:id for update",params(companyId,"id",orderId));if(!Set.of("APPROVED","PARTIALLY_RECEIVED").contains(text(o.get("status"))))bad("Only approved purchase orders can be received");
        UUID receiptId=UUID.randomUUID();String number=nextCode("ops_goods_receipts","receipt_number","GR",companyId);jdbc.sql("insert into ops_goods_receipts(id,company_id,receipt_number,purchase_order_id,warehouse_id,received_on,note,received_by_subject) values(:id,:companyId,:number,:order,:warehouse,:date,:note,:actor)")
            .param("id",receiptId.toString()).param("companyId",companyId.toString()).param("number",number).param("order",orderId.toString()).param("warehouse",request.warehouseId().toString()).param("date",request.receivedOn()).param("note",blank(request.note())).param("actor",actor).update();
        BigDecimal receiptValue=BigDecimal.ZERO;
        for(ReceiptLineRequest line:request.lines()){
            Map<String,Object>item=one("select id,inventory_item_id,quantity,received_quantity,unit_price from ops_purchase_order_items where company_id=:companyId and purchase_order_id=:order and id=:line for update",params(companyId,"order",orderId,"line",line.purchaseOrderItemId()));if(item.get("inventory_item_id")==null)bad("Received purchase order lines must reference an inventory item");
            BigDecimal received=decimal(item.get("received_quantity")).add(line.quantity());if(received.compareTo(decimal(item.get("quantity")))>0)bad("Received quantity exceeds ordered quantity");UUID inventoryItemId=id(item.get("inventory_item_id"));
            jdbc.sql("insert into ops_goods_receipt_lines(id,company_id,goods_receipt_id,purchase_order_item_id,inventory_item_id,quantity) values(:id,:companyId,:receipt,:line,:item,:quantity)").param("id",UUID.randomUUID().toString()).param("companyId",companyId.toString()).param("receipt",receiptId.toString()).param("line",line.purchaseOrderItemId().toString()).param("item",inventoryItemId.toString()).param("quantity",line.quantity()).update();
            jdbc.sql("update ops_purchase_order_items set received_quantity=:received where company_id=:companyId and id=:id").param("received",received).param("companyId",companyId.toString()).param("id",line.purchaseOrderItemId().toString()).update();
            addStock(companyId,request.warehouseId(),inventoryItemId,line.quantity());recordMovement(companyId,inventoryItemId,null,request.warehouseId(),"RECEIPT",line.quantity(),"GOODS_RECEIPT",receiptId,request.note(),actor);
            receiptValue=receiptValue.add(line.quantity().multiply(decimal(item.get("unit_price"))));
        }
        long remaining=jdbc.sql("select count(*) from ops_purchase_order_items where company_id=:companyId and purchase_order_id=:order and received_quantity<quantity").param("companyId",companyId.toString()).param("order",orderId.toString()).query(Long.class).single();String status=remaining==0?"RECEIVED":"PARTIALLY_RECEIVED";
        jdbc.sql("update purchase_orders set status=:status,warehouse_id=:warehouse,received_at=case when :status='RECEIVED' then current_timestamp else received_at end,updated_at=current_timestamp where company_id=:companyId and id=:id").param("status",status).param("warehouse",request.warehouseId().toString()).param("companyId",companyId.toString()).param("id",orderId.toString()).update();
        if(o.get("purchase_request_id")!=null)applyReceiptBudget(companyId,id(o.get("purchase_request_id")),receiptValue,status);
        audit.write(companyId,actor,"GOODS_RECEIPT_POSTED","PURCHASE_ORDER",orderId.toString(),"{\"receipt\":\""+receiptId+"\"}");return new CreatedResource(receiptId,status);
    }

    public List<Map<String,Object>> stock(UUID companyId,UUID warehouseId) {
        Map<String,Object>p=params(companyId);String filter=" where s.company_id=:companyId";if(warehouseId!=null){filter+=" and s.warehouse_id=:warehouse";p.put("warehouse",warehouseId.toString());}
        return rows("select s.id,s.warehouse_id,w.code warehouse_code,w.name warehouse_name,s.inventory_item_id,i.sku,i.name item_name,i.unit,i.min_stock,s.on_hand,s.committed,s.on_hand-s.committed available,(s.on_hand-s.committed)<=i.min_stock low_stock,s.updated_at from ops_stock_levels s join warehouses w on w.id=s.warehouse_id join ops_inventory_items i on i.id=s.inventory_item_id"+filter+" order by w.name,i.name",p);
    }

    public List<Map<String,Object>> stockMovements(UUID companyId) {return rows("select m.id,m.movement_number,m.movement_type,m.inventory_item_id,i.sku,i.name item_name,m.from_warehouse_id,fw.name from_warehouse,m.to_warehouse_id,tw.name to_warehouse,m.quantity,m.occurred_at,m.reference_type,m.reference_id,m.note,m.created_by_subject from ops_stock_movements m join ops_inventory_items i on i.id=m.inventory_item_id left join warehouses fw on fw.id=m.from_warehouse_id left join warehouses tw on tw.id=m.to_warehouse_id where m.company_id=:companyId order by m.occurred_at desc",params(companyId));}

    @Transactional
    public CreatedResource moveStock(UUID companyId,StockMovementRequest request,String actor) {
        ensure(companyId,"ops_inventory_items",request.inventoryItemId(),"Inventory item");ensureOptional(companyId,"warehouses",request.fromWarehouseId(),"Source warehouse");ensureOptional(companyId,"warehouses",request.toWarehouseId(),"Destination warehouse");validateMovement(request);
        if(request.fromWarehouseId()!=null)removeStock(companyId,request.fromWarehouseId(),request.inventoryItemId(),request.quantity());if(request.toWarehouseId()!=null)addStock(companyId,request.toWarehouseId(),request.inventoryItemId(),request.quantity());
        UUID id=recordMovement(companyId,request.inventoryItemId(),request.fromWarehouseId(),request.toWarehouseId(),request.movementType(),request.quantity(),"MANUAL",null,request.note(),actor);audit.write(companyId,actor,"STOCK_MOVED","STOCK_MOVEMENT",id.toString(),"{}");return new CreatedResource(id,"POSTED");
    }

    public List<Map<String,Object>> stockCounts(UUID companyId) {return rows("select c.id,c.count_number,c.warehouse_id,w.name warehouse_name,c.counted_on,c.status,c.note,c.created_by_subject,c.approved_by_subject,c.approved_at,c.created_at,count(l.id) line_count,coalesce(sum(l.counted_quantity-l.system_quantity),0) variance from ops_stock_counts c join warehouses w on w.id=c.warehouse_id left join ops_stock_count_lines l on l.stock_count_id=c.id where c.company_id=:companyId group by c.id,w.name order by c.created_at desc",params(companyId));}

    @Transactional
    public CreatedResource createStockCount(UUID companyId,StockCountRequest request,String actor) {
        ensure(companyId,"warehouses",request.warehouseId(),"Warehouse");UUID id=UUID.randomUUID();String number=nextCode("ops_stock_counts","count_number","SC",companyId);jdbc.sql("insert into ops_stock_counts(id,company_id,count_number,warehouse_id,counted_on,note,created_by_subject) values(:id,:companyId,:number,:warehouse,:date,:note,:actor)").param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("warehouse",request.warehouseId().toString()).param("date",request.countedOn()).param("note",blank(request.note())).param("actor",actor).update();
        for(StockCountLineRequest line:request.lines()){ensure(companyId,"ops_inventory_items",line.inventoryItemId(),"Inventory item");BigDecimal system=stockQuantity(companyId,request.warehouseId(),line.inventoryItemId());jdbc.sql("insert into ops_stock_count_lines(id,company_id,stock_count_id,inventory_item_id,system_quantity,counted_quantity) values(:id,:companyId,:count,:item,:system,:counted)").param("id",UUID.randomUUID().toString()).param("companyId",companyId.toString()).param("count",id.toString()).param("item",line.inventoryItemId().toString()).param("system",system).param("counted",line.countedQuantity()).update();}
        audit.write(companyId,actor,"STOCK_COUNT_CREATED","STOCK_COUNT",id.toString(),"{}");return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource decideStockCount(UUID companyId,UUID countId,DecisionRequest request,String actor) {
        ensureStatus(request.status(),Set.of("SUBMITTED","APPROVED","REJECTED"));Map<String,Object>c=one("select status,warehouse_id from ops_stock_counts where company_id=:companyId and id=:id for update",params(companyId,"id",countId));String current=text(c.get("status"));boolean allowed=("DRAFT".equals(current)&&"SUBMITTED".equals(request.status()))||("SUBMITTED".equals(current)&&Set.of("APPROVED","REJECTED").contains(request.status()));if(!allowed)bad("Unsupported stock count status transition");
        if("APPROVED".equals(request.status())){UUID warehouse=id(c.get("warehouse_id"));for(Map<String,Object>line:rows("select inventory_item_id,system_quantity,counted_quantity from ops_stock_count_lines where company_id=:companyId and stock_count_id=:count",params(companyId,"count",countId))){UUID item=id(line.get("inventory_item_id"));BigDecimal currentQty=stockQuantity(companyId,warehouse,item);if(currentQty.compareTo(decimal(line.get("system_quantity")))!=0)bad("Stock changed after count; create a new count");BigDecimal variance=decimal(line.get("counted_quantity")).subtract(currentQty);if(variance.signum()>0){addStock(companyId,warehouse,item,variance);recordMovement(companyId,item,null,warehouse,"ADJUSTMENT_IN",variance,"STOCK_COUNT",countId,request.note(),actor);}else if(variance.signum()<0){removeStock(companyId,warehouse,item,variance.abs());recordMovement(companyId,item,warehouse,null,"ADJUSTMENT_OUT",variance.abs(),"STOCK_COUNT",countId,request.note(),actor);}}}
        jdbc.sql("update ops_stock_counts set status=:status,approved_by_subject=case when :status='APPROVED' then :actor else approved_by_subject end,approved_at=case when :status='APPROVED' then current_timestamp else approved_at end where company_id=:companyId and id=:id").param("status",request.status()).param("actor",actor).param("companyId",companyId.toString()).param("id",countId.toString()).update();audit.write(companyId,actor,"STOCK_COUNT_STATUS_CHANGED","STOCK_COUNT",countId.toString(),meta(request.status(),request.note()));return new CreatedResource(countId,request.status());
    }

    public List<Map<String,Object>> assets(UUID companyId,String status,Authentication authentication) {
        Map<String,Object>p=params(companyId);String filter=" where a.company_id=:companyId";if(notBlank(status)){filter+=" and a.status=:status";p.put("status",status);}
        return rows("select a.id,a.asset_code,a.name,a.asset_type,a.serial_number,a.status,a.condition_status,a.purchase_cost,a.currency,a.acquired_on,a.warranty_until,a.custodian_employee_id,e.full_name custodian_name,a.department_id,d.name department_name,a.warehouse_id,w.name warehouse_name,a.note,a.created_at from ops_assets a left join employees e on e.id=a.custodian_employee_id left join departments d on d.id=a.department_id left join warehouses w on w.id=a.warehouse_id"+filter+" order by a.created_at desc",p).stream().filter(r->access.canAccessAsset(authentication,companyId,id(r.get("id")),"asset.read")).toList();
    }

    @Transactional
    public CreatedResource createAsset(UUID companyId,AssetRequest request,String actor) {
        ensureOptional(companyId,"purchase_orders",request.purchaseOrderId(),"Purchase order");ensureOptional(companyId,"warehouses",request.warehouseId(),"Warehouse");ensureOptional(companyId,"departments",request.departmentId(),"Department");UUID id=UUID.randomUUID();String code=notBlank(request.assetCode())?request.assetCode():nextCode("ops_assets","asset_code","AST",companyId);
        jdbc.sql("insert into ops_assets(id,company_id,asset_code,name,asset_type,serial_number,purchase_order_id,warehouse_id,department_id,acquired_on,purchase_cost,currency,warranty_until,condition_status,note) values(:id,:companyId,:code,:name,:type,:serial,:order,:warehouse,:department,:acquired,:cost,:currency,:warranty,:condition,:note)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("code",code).param("name",request.name()).param("type",request.assetType()).param("serial",blank(request.serialNumber())).param("order",uuid(request.purchaseOrderId())).param("warehouse",uuid(request.warehouseId())).param("department",uuid(request.departmentId())).param("acquired",request.acquiredOn()).param("cost",zero(request.purchaseCost())).param("currency",request.currency()).param("warranty",request.warrantyUntil()).param("condition",request.conditionStatus()).param("note",blank(request.note())).update();
        audit.write(companyId,actor,"ASSET_CREATED","ASSET",id.toString(),"{}");return new CreatedResource(id,"AVAILABLE");
    }

    @Transactional
    public CreatedResource assignAsset(UUID companyId,UUID assetId,AssetAssignmentRequest request,String actor) {
        ensure(companyId,"employees",request.employeeId(),"Employee");Map<String,Object>a=one("select status from ops_assets where company_id=:companyId and id=:id for update",params(companyId,"id",assetId));if(!"AVAILABLE".equals(text(a.get("status"))))bad("Only available assets can be assigned");Map<String,Object>employee=one("select department_id from employees where company_id=:companyId and id=:id",params(companyId,"id",request.employeeId()));UUID assignment=UUID.randomUUID();jdbc.sql("insert into ops_asset_assignments(id,company_id,asset_id,employee_id,assigned_on,assigned_by_subject,note) values(:id,:companyId,:asset,:employee,:date,:actor,:note)").param("id",assignment.toString()).param("companyId",companyId.toString()).param("asset",assetId.toString()).param("employee",request.employeeId().toString()).param("date",request.assignedOn()).param("actor",actor).param("note",blank(request.note())).update();jdbc.sql("update ops_assets set status='ASSIGNED',custodian_employee_id=:employee,department_id=:department,updated_at=current_timestamp where company_id=:companyId and id=:id").param("employee",request.employeeId().toString()).param("department",textOrNull(employee.get("department_id"))).param("companyId",companyId.toString()).param("id",assetId.toString()).update();audit.write(companyId,actor,"ASSET_ASSIGNED","ASSET",assetId.toString(),"{\"employeeId\":\""+request.employeeId()+"\"}");return new CreatedResource(assignment,"ASSIGNED");
    }

    @Transactional
    public CreatedResource returnAsset(UUID companyId,UUID assetId,AssetReturnRequest request,String actor) {
        Map<String,Object>a=one("select status from ops_assets where company_id=:companyId and id=:id for update",params(companyId,"id",assetId));if(!"ASSIGNED".equals(text(a.get("status"))))bad("Asset is not assigned");String assignment=jdbc.sql("select id from ops_asset_assignments where company_id=:companyId and asset_id=:asset and returned_on is null order by assigned_on desc limit 1").param("companyId",companyId.toString()).param("asset",assetId.toString()).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Active assignment not found"));jdbc.sql("update ops_asset_assignments set returned_on=:date,return_condition=:condition,note=coalesce(:note,note) where id=:id").param("date",request.returnedOn()).param("condition",request.returnCondition()).param("note",blank(request.note())).param("id",assignment.trim()).update();jdbc.sql("update ops_assets set status='AVAILABLE',custodian_employee_id=null,condition_status=:condition,updated_at=current_timestamp where company_id=:companyId and id=:id").param("condition",normalizeCondition(request.returnCondition())).param("companyId",companyId.toString()).param("id",assetId.toString()).update();audit.write(companyId,actor,"ASSET_RETURNED","ASSET",assetId.toString(),"{}");return new CreatedResource(assetId,"AVAILABLE");
    }

    public List<Map<String,Object>> maintenance(UUID companyId) {return rows("select m.id,m.maintenance_number,m.asset_id,a.asset_code,a.name asset_name,m.supplier_id,s.name supplier_name,m.maintenance_type,m.scheduled_on,m.completed_on,m.cost,m.currency,m.status,m.description,m.result,m.created_at from ops_maintenance_orders m join ops_assets a on a.id=m.asset_id left join suppliers s on s.id=m.supplier_id where m.company_id=:companyId order by coalesce(m.scheduled_on,current_date) desc,m.created_at desc",params(companyId));}

    @Transactional
    public CreatedResource createMaintenance(UUID companyId,MaintenanceRequest request,String actor) {
        ensure(companyId,"ops_assets",request.assetId(),"Asset");ensureOptional(companyId,"suppliers",request.supplierId(),"Supplier");UUID id=UUID.randomUUID();String number=nextCode("ops_maintenance_orders","maintenance_number","MT",companyId);jdbc.sql("insert into ops_maintenance_orders(id,company_id,maintenance_number,asset_id,supplier_id,maintenance_type,scheduled_on,cost,currency,description,created_by_subject) values(:id,:companyId,:number,:asset,:supplier,:type,:scheduled,:cost,:currency,:description,:actor)").param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("asset",request.assetId().toString()).param("supplier",uuid(request.supplierId())).param("type",request.maintenanceType()).param("scheduled",request.scheduledOn()).param("cost",zero(request.cost())).param("currency",request.currency()).param("description",request.description()).param("actor",actor).update();audit.write(companyId,actor,"MAINTENANCE_CREATED","MAINTENANCE",id.toString(),"{}");return new CreatedResource(id,"OPEN");
    }

    @Transactional
    public CreatedResource updateMaintenance(UUID companyId,UUID maintenanceId,DecisionRequest request,String actor) {
        ensureStatus(request.status(),Set.of("SCHEDULED","IN_PROGRESS","COMPLETED","CANCELLED"));Map<String,Object>m=one("select status,asset_id from ops_maintenance_orders where company_id=:companyId and id=:id for update",params(companyId,"id",maintenanceId));if(Set.of("COMPLETED","CANCELLED").contains(text(m.get("status"))))bad("Maintenance order is closed");jdbc.sql("update ops_maintenance_orders set status=:status,completed_on=case when :status='COMPLETED' then current_date else completed_on end,result=case when :status='COMPLETED' then :note else result end where company_id=:companyId and id=:id").param("status",request.status()).param("note",blank(request.note())).param("companyId",companyId.toString()).param("id",maintenanceId.toString()).update();if("IN_PROGRESS".equals(request.status()))jdbc.sql("update ops_assets set status='MAINTENANCE',updated_at=current_timestamp where company_id=:companyId and id=:id").param("companyId",companyId.toString()).param("id",text(m.get("asset_id"))).update();if(Set.of("COMPLETED","CANCELLED").contains(request.status()))jdbc.sql("update ops_assets set status=case when custodian_employee_id is null then 'AVAILABLE' else 'ASSIGNED' end,updated_at=current_timestamp where company_id=:companyId and id=:id and status='MAINTENANCE'").param("companyId",companyId.toString()).param("id",text(m.get("asset_id"))).update();audit.write(companyId,actor,"MAINTENANCE_STATUS_CHANGED","MAINTENANCE",maintenanceId.toString(),meta(request.status(),request.note()));return new CreatedResource(maintenanceId,request.status());
    }

    public List<Map<String,Object>> licenses(UUID companyId) {return rows("select l.id,l.code,l.product_name,l.supplier_id,s.name supplier_name,l.owner_employee_id,e.full_name owner_name,l.seat_count,l.assigned_seats,l.starts_on,l.renews_on,l.billing_cycle,l.cost,l.currency,l.status,l.credential_reference,l.created_at from ops_software_licenses l left join suppliers s on s.id=l.supplier_id left join employees e on e.id=l.owner_employee_id where l.company_id=:companyId order by coalesce(l.renews_on,date '9999-12-31'),l.product_name",params(companyId));}

    @Transactional
    public CreatedResource createLicense(UUID companyId,LicenseRequest request,String actor) {
        ensureOptional(companyId,"suppliers",request.supplierId(),"Supplier");ensureOptional(companyId,"employees",request.ownerEmployeeId(),"Employee");int seats=request.seatCount()==null?1:request.seatCount(),assigned=request.assignedSeats()==null?0:request.assignedSeats();if(assigned>seats)bad("Assigned seats cannot exceed seat count");UUID id=UUID.randomUUID();jdbc.sql("insert into ops_software_licenses(id,company_id,code,product_name,supplier_id,owner_employee_id,seat_count,assigned_seats,starts_on,renews_on,billing_cycle,cost,currency,credential_reference) values(:id,:companyId,:code,:name,:supplier,:owner,:seats,:assigned,:starts,:renews,:cycle,:cost,:currency,:credential)").param("id",id.toString()).param("companyId",companyId.toString()).param("code",request.code()).param("name",request.productName()).param("supplier",uuid(request.supplierId())).param("owner",uuid(request.ownerEmployeeId())).param("seats",seats).param("assigned",assigned).param("starts",request.startsOn()).param("renews",request.renewsOn()).param("cycle",blank(request.billingCycle())).param("cost",zero(request.cost())).param("currency",request.currency()).param("credential",blank(request.credentialReference())).update();audit.write(companyId,actor,"SOFTWARE_LICENSE_CREATED","SOFTWARE_LICENSE",id.toString(),"{}");return new CreatedResource(id,"ACTIVE");
    }

    public List<Map<String,Object>> manufacturingOrders(UUID companyId) {return rows("select o.id,o.order_number,o.inventory_item_id,i.sku,i.name item_name,o.warehouse_id,w.name warehouse_name,o.planned_quantity,o.completed_quantity,o.planned_start,o.planned_end,o.status,o.note,o.created_at from ops_manufacturing_orders o join ops_inventory_items i on i.id=o.inventory_item_id join warehouses w on w.id=o.warehouse_id where o.company_id=:companyId order by o.created_at desc",params(companyId));}

    @Transactional
    public CreatedResource createManufacturingOrder(UUID companyId,ManufacturingRequest request,String actor) {
        ensure(companyId,"ops_inventory_items",request.inventoryItemId(),"Inventory item");ensure(companyId,"warehouses",request.warehouseId(),"Warehouse");if(request.plannedStart()!=null&&request.plannedEnd()!=null&&request.plannedEnd().isBefore(request.plannedStart()))bad("Planned end must be on or after planned start");UUID id=UUID.randomUUID();String number=nextCode("ops_manufacturing_orders","order_number","MO",companyId);jdbc.sql("insert into ops_manufacturing_orders(id,company_id,order_number,inventory_item_id,warehouse_id,planned_quantity,planned_start,planned_end,note,created_by_subject) values(:id,:companyId,:number,:item,:warehouse,:quantity,:start,:end,:note,:actor)").param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("item",request.inventoryItemId().toString()).param("warehouse",request.warehouseId().toString()).param("quantity",request.plannedQuantity()).param("start",request.plannedStart()).param("end",request.plannedEnd()).param("note",blank(request.note())).param("actor",actor).update();audit.write(companyId,actor,"MANUFACTURING_ORDER_CREATED","MANUFACTURING_ORDER",id.toString(),"{}");return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource updateManufacturingOrder(UUID companyId,UUID orderId,ManufacturingProgressRequest request,String actor) {
        ensureStatus(request.status(),Set.of("RELEASED","IN_PROGRESS","COMPLETED","CANCELLED"));Map<String,Object>o=one("select status,inventory_item_id,warehouse_id,planned_quantity,completed_quantity from ops_manufacturing_orders where company_id=:companyId and id=:id for update",params(companyId,"id",orderId));String current=text(o.get("status"));boolean allowed=("DRAFT".equals(current)&&Set.of("RELEASED","CANCELLED").contains(request.status()))||("RELEASED".equals(current)&&Set.of("IN_PROGRESS","CANCELLED").contains(request.status()))||("IN_PROGRESS".equals(current)&&Set.of("IN_PROGRESS","COMPLETED","CANCELLED").contains(request.status()));if(!allowed)bad("Unsupported manufacturing status transition");BigDecimal completed=request.completedQuantity()==null?decimal(o.get("completed_quantity")):request.completedQuantity();if(completed.compareTo(decimal(o.get("planned_quantity")))>0)bad("Completed quantity cannot exceed planned quantity");if("COMPLETED".equals(request.status())&&completed.compareTo(decimal(o.get("planned_quantity")))!=0)bad("Completed quantity must equal planned quantity");BigDecimal delta=completed.subtract(decimal(o.get("completed_quantity")));if(delta.signum()<0)bad("Completed quantity cannot decrease");if(delta.signum()>0){UUID item=id(o.get("inventory_item_id")),warehouse=id(o.get("warehouse_id"));addStock(companyId,warehouse,item,delta);recordMovement(companyId,item,null,warehouse,"RECEIPT",delta,"MANUFACTURING_ORDER",orderId,request.note(),actor);}jdbc.sql("update ops_manufacturing_orders set status=:status,completed_quantity=:completed,note=coalesce(:note,note),updated_at=current_timestamp where company_id=:companyId and id=:id").param("status",request.status()).param("completed",completed).param("note",blank(request.note())).param("companyId",companyId.toString()).param("id",orderId.toString()).update();audit.write(companyId,actor,"MANUFACTURING_STATUS_CHANGED","MANUFACTURING_ORDER",orderId.toString(),meta(request.status(),request.note()));return new CreatedResource(orderId,request.status());
    }

    public List<Map<String,Object>> shipments(UUID companyId) {return rows("select s.id,s.shipment_number,s.direction,s.purchase_order_id,p.po_number,s.sales_order_id,o.order_number,s.from_warehouse_id,w.name warehouse_name,s.to_address,s.carrier,s.tracking_number,s.expected_on,s.shipped_at,s.delivered_at,s.status,s.note,s.created_at from ops_shipments s left join purchase_orders p on p.id=s.purchase_order_id left join crm_sales_orders o on o.id=s.sales_order_id left join warehouses w on w.id=s.from_warehouse_id where s.company_id=:companyId order by s.created_at desc",params(companyId));}

    @Transactional
    public CreatedResource createShipment(UUID companyId,ShipmentRequest request,String actor) {
        ensureOptional(companyId,"purchase_orders",request.purchaseOrderId(),"Purchase order");ensureOptional(companyId,"crm_sales_orders",request.salesOrderId(),"Sales order");ensureOptional(companyId,"warehouses",request.fromWarehouseId(),"Warehouse");if("INBOUND".equals(request.direction())&&request.purchaseOrderId()==null)bad("Inbound shipment requires a purchase order");if("OUTBOUND".equals(request.direction())&&request.salesOrderId()==null)bad("Outbound shipment requires a sales order");UUID id=UUID.randomUUID();String number=nextCode("ops_shipments","shipment_number","SH",companyId);jdbc.sql("insert into ops_shipments(id,company_id,shipment_number,direction,purchase_order_id,sales_order_id,from_warehouse_id,to_address,carrier,tracking_number,expected_on,note,created_by_subject) values(:id,:companyId,:number,:direction,:po,:order,:warehouse,:address,:carrier,:tracking,:expected,:note,:actor)").param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("direction",request.direction()).param("po",uuid(request.purchaseOrderId())).param("order",uuid(request.salesOrderId())).param("warehouse",uuid(request.fromWarehouseId())).param("address",blank(request.toAddress())).param("carrier",blank(request.carrier())).param("tracking",blank(request.trackingNumber())).param("expected",request.expectedOn()).param("note",blank(request.note())).param("actor",actor).update();audit.write(companyId,actor,"SHIPMENT_CREATED","SHIPMENT",id.toString(),"{}");return new CreatedResource(id,"PLANNED");
    }

    @Transactional
    public CreatedResource updateShipment(UUID companyId,UUID shipmentId,DecisionRequest request,String actor) {
        ensureStatus(request.status(),Set.of("READY","IN_TRANSIT","DELIVERED","EXCEPTION","CANCELLED"));Map<String,Object>s=one("select status from ops_shipments where company_id=:companyId and id=:id for update",params(companyId,"id",shipmentId));String current=text(s.get("status"));boolean allowed=("PLANNED".equals(current)&&Set.of("READY","CANCELLED").contains(request.status()))||("READY".equals(current)&&Set.of("IN_TRANSIT","CANCELLED").contains(request.status()))||("IN_TRANSIT".equals(current)&&Set.of("DELIVERED","EXCEPTION").contains(request.status()))||("EXCEPTION".equals(current)&&Set.of("IN_TRANSIT","DELIVERED","CANCELLED").contains(request.status()));if(!allowed)bad("Unsupported shipment status transition");jdbc.sql("update ops_shipments set status=:status,shipped_at=case when :status='IN_TRANSIT' and shipped_at is null then current_timestamp else shipped_at end,delivered_at=case when :status='DELIVERED' then current_timestamp else delivered_at end,note=coalesce(:note,note),updated_at=current_timestamp where company_id=:companyId and id=:id").param("status",request.status()).param("note",blank(request.note())).param("companyId",companyId.toString()).param("id",shipmentId.toString()).update();audit.write(companyId,actor,"SHIPMENT_STATUS_CHANGED","SHIPMENT",shipmentId.toString(),meta(request.status(),request.note()));return new CreatedResource(shipmentId,request.status());
    }

    private void reserveBudget(UUID companyId,UUID budgetId,BigDecimal amount,String currency){Map<String,Object>b=one("select status,currency,planned_amount,committed_amount,actual_amount from finance_budgets where company_id=:companyId and id=:id for update",params(companyId,"id",budgetId));if(!"ACTIVE".equals(text(b.get("status"))))bad("Budget must be active");if(!currency.equals(text(b.get("currency"))))bad("Budget currency does not match purchase request");BigDecimal committed=decimal(b.get("committed_amount")).add(amount);if(committed.add(decimal(b.get("actual_amount"))).compareTo(decimal(b.get("planned_amount")))>0)bad("Purchase request exceeds available budget");jdbc.sql("update finance_budgets set committed_amount=:committed,updated_at=current_timestamp where company_id=:companyId and id=:id").param("committed",committed).param("companyId",companyId.toString()).param("id",budgetId.toString()).update();}
    private void applyReceiptBudget(UUID companyId,UUID requestId,BigDecimal amount,String orderStatus){Map<String,Object>r=one("select budget_id,budget_reserved_amount from purchase_requests where company_id=:companyId and id=:id for update",params(companyId,"id",requestId));if(r.get("budget_id")==null)return;UUID budget=id(r.get("budget_id"));Map<String,Object>b=one("select planned_amount,committed_amount,actual_amount from finance_budgets where company_id=:companyId and id=:id for update",params(companyId,"id",budget));BigDecimal reserved=decimal(r.get("budget_reserved_amount"));BigDecimal release="RECEIVED".equals(orderStatus)?reserved:amount.min(reserved);BigDecimal actual=decimal(b.get("actual_amount")).add(amount),committed=decimal(b.get("committed_amount")).subtract(release).max(BigDecimal.ZERO);if(actual.add(committed).compareTo(decimal(b.get("planned_amount")))>0)bad("Receipt exceeds budget limit");jdbc.sql("update finance_budgets set actual_amount=:actual,committed_amount=:committed,updated_at=current_timestamp where company_id=:companyId and id=:id").param("actual",actual).param("committed",committed).param("companyId",companyId.toString()).param("id",budget.toString()).update();jdbc.sql("update purchase_requests set budget_reserved_amount=:reserved,updated_at=current_timestamp where company_id=:companyId and id=:id").param("reserved",reserved.subtract(release).max(BigDecimal.ZERO)).param("companyId",companyId.toString()).param("id",requestId.toString()).update();}
    private void validateMovement(StockMovementRequest r){if("TRANSFER".equals(r.movementType())&&(r.fromWarehouseId()==null||r.toWarehouseId()==null||r.fromWarehouseId().equals(r.toWarehouseId())))bad("Transfer requires two different warehouses");if(Set.of("ISSUE","ADJUSTMENT_OUT").contains(r.movementType())&&r.fromWarehouseId()==null)bad("Outbound movement requires source warehouse");if(Set.of("ADJUSTMENT_IN","RETURN").contains(r.movementType())&&r.toWarehouseId()==null)bad("Inbound movement requires destination warehouse");}
    private BigDecimal stockQuantity(UUID companyId,UUID warehouse,UUID item){return jdbc.sql("select on_hand from ops_stock_levels where company_id=:companyId and warehouse_id=:warehouse and inventory_item_id=:item for update").param("companyId",companyId.toString()).param("warehouse",warehouse.toString()).param("item",item.toString()).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);}
    private void addStock(UUID companyId,UUID warehouse,UUID item,BigDecimal quantity){jdbc.sql("insert into ops_stock_levels(id,company_id,warehouse_id,inventory_item_id,on_hand) values(:id,:companyId,:warehouse,:item,:quantity) on conflict(warehouse_id,inventory_item_id) do update set on_hand=ops_stock_levels.on_hand+excluded.on_hand,updated_at=current_timestamp").param("id",UUID.randomUUID().toString()).param("companyId",companyId.toString()).param("warehouse",warehouse.toString()).param("item",item.toString()).param("quantity",quantity).update();}
    private void removeStock(UUID companyId,UUID warehouse,UUID item,BigDecimal quantity){BigDecimal current=stockQuantity(companyId,warehouse,item);BigDecimal committed=jdbc.sql("select committed from ops_stock_levels where company_id=:companyId and warehouse_id=:warehouse and inventory_item_id=:item").param("companyId",companyId.toString()).param("warehouse",warehouse.toString()).param("item",item.toString()).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);if(current.subtract(quantity).compareTo(committed)<0)bad("Movement would make available stock negative");jdbc.sql("update ops_stock_levels set on_hand=on_hand-:quantity,updated_at=current_timestamp where company_id=:companyId and warehouse_id=:warehouse and inventory_item_id=:item").param("quantity",quantity).param("companyId",companyId.toString()).param("warehouse",warehouse.toString()).param("item",item.toString()).update();}
    private UUID recordMovement(UUID companyId,UUID item,UUID from,UUID to,String type,BigDecimal quantity,String referenceType,UUID referenceId,String note,String actor){UUID id=UUID.randomUUID();String number=nextCode("ops_stock_movements","movement_number","SM",companyId);jdbc.sql("insert into ops_stock_movements(id,company_id,movement_number,inventory_item_id,from_warehouse_id,to_warehouse_id,movement_type,quantity,reference_type,reference_id,note,created_by_subject) values(:id,:companyId,:number,:item,:from,:to,:type,:quantity,:referenceType,:referenceId,:note,:actor)").param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("item",item.toString()).param("from",uuid(from)).param("to",uuid(to)).param("type",type).param("quantity",quantity).param("referenceType",referenceType).param("referenceId",uuid(referenceId)).param("note",blank(note)).param("actor",actor).update();return id;}
    private String normalizeCondition(String value){String v=value.toUpperCase();return Set.of("NEW","GOOD","FAIR","DAMAGED").contains(v)?v:"FAIR";}
    private long count(String table,UUID companyId,String condition){if(!Set.of("suppliers","purchase_orders","ops_shipments").contains(table))throw new IllegalArgumentException("Unsupported count table");return jdbc.sql("select count(*) from "+table+" where company_id=:companyId and "+condition).param("companyId",companyId.toString()).query(Long.class).single();}
    private void ensureOptional(UUID companyId,String table,UUID id,String label){if(id!=null)ensure(companyId,table,id,label);}
    private void ensure(UUID companyId,String table,UUID id,String label){if(!Set.of("departments","pm_projects","finance_budgets","suppliers","warehouses","ops_inventory_items","purchase_requests","purchase_orders","employees","ops_assets","crm_sales_orders").contains(table))throw new IllegalArgumentException("Unsupported operations resource table");long found=jdbc.sql("select count(*) from "+table+" where company_id=:companyId and id=:id").param("companyId",companyId.toString()).param("id",id.toString()).query(Long.class).single();if(found==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,label+" not found");}
    private String nextCode(String table,String column,String prefix,UUID companyId){if(!Set.of("suppliers","purchase_requests","purchase_orders","ops_goods_receipts","ops_stock_movements","ops_stock_counts","ops_assets","ops_maintenance_orders","ops_manufacturing_orders","ops_shipments").contains(table))throw new IllegalArgumentException("Unsupported operations code table");long n=jdbc.sql("select count(*) from "+table+" where company_id=:companyId").param("companyId",companyId.toString()).query(Long.class).single();String code;do{code=prefix+"-"+String.format("%05d",++n);}while(jdbc.sql("select count(*) from "+table+" where company_id=:companyId and "+column+"=:code").param("companyId",companyId.toString()).param("code",code).query(Long.class).single()>0);return code;}
    private List<Map<String,Object>> rows(String sql,Map<String,Object>params){return namedJdbc.queryForList(sql,params);}private Map<String,Object>one(String sql,Map<String,Object>params){List<Map<String,Object>>r=rows(sql,params);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Operations record not found");return r.getFirst();}
    private static Map<String,Object>params(UUID companyId){Map<String,Object>p=new LinkedHashMap<>();p.put("companyId",companyId.toString());return p;}private static Map<String,Object>params(UUID companyId,String key,UUID value){Map<String,Object>p=params(companyId);p.put(key,value.toString());return p;}private static Map<String,Object>params(UUID companyId,String k1,UUID v1,String k2,UUID v2){Map<String,Object>p=params(companyId,k1,v1);p.put(k2,v2.toString());return p;}
    private static void ensureStatus(String status,Set<String>allowed){if(!allowed.contains(status))bad("Unsupported status");}private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}private static boolean notBlank(String v){return v!=null&&!v.isBlank();}private static String blank(String v){return notBlank(v)?v.trim():null;}private static String uuid(UUID v){return v==null?null:v.toString();}private static UUID id(Object v){return v==null?null:UUID.fromString(v.toString().trim());}private static String text(Object v){return v==null?"":v.toString();}private static String textOrNull(Object v){String s=text(v);return s.isBlank()?null:s.trim();}private static BigDecimal decimal(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}private static BigDecimal zero(BigDecimal v){return v==null?BigDecimal.ZERO:v;}private static String meta(String status,String note){return "{\"status\":\""+status+"\",\"note\":\""+(note==null?"":note.replace("\\","").replace("\"",""))+"\"}";}
}
