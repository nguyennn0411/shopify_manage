package com.company.erp.operations;

import static com.company.erp.operations.OperationsModels.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService operations;
    public OperationsController(OperationsService operations){this.operations=operations;}

    @GetMapping("/summary")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'operations.access')")
    OperationsSummary summary(@RequestParam UUID companyId,Authentication authentication){return operations.summary(companyId,authentication);}

    @GetMapping("/references/{kind}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'operations.access')")
    List<Map<String,Object>> references(@RequestParam UUID companyId,@PathVariable String kind){return operations.references(companyId,kind);}

    @GetMapping("/suppliers")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'supplier.read')")
    List<Map<String,Object>> suppliers(@RequestParam UUID companyId,@RequestParam(required=false) String status){return operations.suppliers(companyId,status);}
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'supplier.write')")
    CreatedResource createSupplier(@RequestParam UUID companyId,@Valid @RequestBody SupplierRequest request,Authentication auth){return operations.createSupplier(companyId,request,auth.getName());}
    @GetMapping("/suppliers/{supplierId}/reviews")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'supplier.read')")
    List<Map<String,Object>> supplierReviews(@RequestParam UUID companyId,@PathVariable UUID supplierId){return operations.supplierReviews(companyId,supplierId);}
    @PostMapping("/suppliers/{supplierId}/reviews") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'supplier.write')")
    CreatedResource addSupplierReview(@RequestParam UUID companyId,@PathVariable UUID supplierId,@Valid @RequestBody SupplierReviewRequest request,Authentication auth){return operations.addSupplierReview(companyId,supplierId,request,auth.getName());}

    @GetMapping("/warehouses")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.read')")
    List<Map<String,Object>> warehouses(@RequestParam UUID companyId){return operations.warehouses(companyId);}
    @PostMapping("/warehouses") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.write')")
    CreatedResource createWarehouse(@RequestParam UUID companyId,@Valid @RequestBody WarehouseRequest request,Authentication auth){return operations.createWarehouse(companyId,request,auth.getName());}
    @GetMapping("/items")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.read')")
    List<Map<String,Object>> inventoryItems(@RequestParam UUID companyId){return operations.inventoryItems(companyId);}
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.write')")
    CreatedResource createInventoryItem(@RequestParam UUID companyId,@Valid @RequestBody InventoryItemRequest request,Authentication auth){return operations.createInventoryItem(companyId,request,auth.getName());}

    @GetMapping("/purchase-requests")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.read')")
    List<Map<String,Object>> purchaseRequests(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return operations.purchaseRequests(companyId,status,auth);}
    @GetMapping("/purchase-requests/{requestId}/items")
    @PreAuthorize("@operationsAccess.canAccessPurchaseRequest(authentication,#companyId,#requestId,'procurement.read')")
    List<Map<String,Object>> purchaseRequestItems(@RequestParam UUID companyId,@PathVariable UUID requestId){return operations.purchaseRequestItems(companyId,requestId);}
    @PostMapping("/purchase-requests") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.write')")
    CreatedResource createPurchaseRequest(@RequestParam UUID companyId,@Valid @RequestBody PurchaseRequestCreate request,Authentication auth){return operations.createPurchaseRequest(companyId,request,auth.getName());}
    @PostMapping("/purchase-requests/{requestId}/submit")
    @PreAuthorize("@operationsAccess.canAccessPurchaseRequest(authentication,#companyId,#requestId,'procurement.write')")
    CreatedResource submitPurchaseRequest(@RequestParam UUID companyId,@PathVariable UUID requestId,Authentication auth){return operations.decidePurchaseRequest(companyId,requestId,new DecisionRequest("SUBMITTED",null),auth.getName());}
    @PostMapping("/purchase-requests/{requestId}/decision")
    @PreAuthorize("@operationsAccess.canAccessPurchaseRequest(authentication,#companyId,#requestId,'procurement.approve')")
    CreatedResource decidePurchaseRequest(@RequestParam UUID companyId,@PathVariable UUID requestId,@Valid @RequestBody DecisionRequest request,Authentication auth){return operations.decidePurchaseRequest(companyId,requestId,request,auth.getName());}

    @GetMapping("/purchase-orders")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.read')")
    List<Map<String,Object>> purchaseOrders(@RequestParam UUID companyId,@RequestParam(required=false) String status){return operations.purchaseOrders(companyId,status);}
    @GetMapping("/purchase-orders/{orderId}/items")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.read')")
    List<Map<String,Object>> purchaseOrderItems(@RequestParam UUID companyId,@PathVariable UUID orderId){return operations.purchaseOrderItems(companyId,orderId);}
    @PostMapping("/purchase-orders") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.write')")
    CreatedResource createPurchaseOrder(@RequestParam UUID companyId,@Valid @RequestBody PurchaseOrderRequest request,Authentication auth){return operations.createPurchaseOrder(companyId,request,auth.getName());}
    @PostMapping("/purchase-orders/{orderId}/submit")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.write')")
    CreatedResource submitPurchaseOrder(@RequestParam UUID companyId,@PathVariable UUID orderId,Authentication auth){return operations.decidePurchaseOrder(companyId,orderId,new DecisionRequest("SUBMITTED",null),auth.getName());}
    @PostMapping("/purchase-orders/{orderId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'procurement.approve')")
    CreatedResource decidePurchaseOrder(@RequestParam UUID companyId,@PathVariable UUID orderId,@Valid @RequestBody DecisionRequest request,Authentication auth){return operations.decidePurchaseOrder(companyId,orderId,request,auth.getName());}
    @PostMapping("/purchase-orders/{orderId}/receipts") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.write')")
    CreatedResource receivePurchaseOrder(@RequestParam UUID companyId,@PathVariable UUID orderId,@Valid @RequestBody GoodsReceiptRequest request,Authentication auth){return operations.receivePurchaseOrder(companyId,orderId,request,auth.getName());}
    @GetMapping("/goods-receipts")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.read')")
    List<Map<String,Object>> goodsReceipts(@RequestParam UUID companyId){return operations.goodsReceipts(companyId);}

    @GetMapping("/stock")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.read')")
    List<Map<String,Object>> stock(@RequestParam UUID companyId,@RequestParam(required=false) UUID warehouseId){return operations.stock(companyId,warehouseId);}
    @GetMapping("/stock-movements")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.read')")
    List<Map<String,Object>> stockMovements(@RequestParam UUID companyId){return operations.stockMovements(companyId);}
    @PostMapping("/stock-movements") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.write')")
    CreatedResource moveStock(@RequestParam UUID companyId,@Valid @RequestBody StockMovementRequest request,Authentication auth){return operations.moveStock(companyId,request,auth.getName());}
    @GetMapping("/stock-counts")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.read')")
    List<Map<String,Object>> stockCounts(@RequestParam UUID companyId){return operations.stockCounts(companyId);}
    @PostMapping("/stock-counts") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.write')")
    CreatedResource createStockCount(@RequestParam UUID companyId,@Valid @RequestBody StockCountRequest request,Authentication auth){return operations.createStockCount(companyId,request,auth.getName());}
    @PostMapping("/stock-counts/{countId}/submit")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.write')")
    CreatedResource submitStockCount(@RequestParam UUID companyId,@PathVariable UUID countId,Authentication auth){return operations.decideStockCount(companyId,countId,new DecisionRequest("SUBMITTED",null),auth.getName());}
    @PostMapping("/stock-counts/{countId}/decision")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'inventory.count.approve')")
    CreatedResource decideStockCount(@RequestParam UUID companyId,@PathVariable UUID countId,@Valid @RequestBody DecisionRequest request,Authentication auth){return operations.decideStockCount(companyId,countId,request,auth.getName());}

    @GetMapping("/assets")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'asset.read')")
    List<Map<String,Object>> assets(@RequestParam UUID companyId,@RequestParam(required=false) String status,Authentication auth){return operations.assets(companyId,status,auth);}
    @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'asset.write')")
    CreatedResource createAsset(@RequestParam UUID companyId,@Valid @RequestBody AssetRequest request,Authentication auth){return operations.createAsset(companyId,request,auth.getName());}
    @PostMapping("/assets/{assetId}/assign")
    @PreAuthorize("@operationsAccess.canAccessAsset(authentication,#companyId,#assetId,'asset.write')")
    CreatedResource assignAsset(@RequestParam UUID companyId,@PathVariable UUID assetId,@Valid @RequestBody AssetAssignmentRequest request,Authentication auth){return operations.assignAsset(companyId,assetId,request,auth.getName());}
    @PostMapping("/assets/{assetId}/return")
    @PreAuthorize("@operationsAccess.canAccessAsset(authentication,#companyId,#assetId,'asset.write')")
    CreatedResource returnAsset(@RequestParam UUID companyId,@PathVariable UUID assetId,@Valid @RequestBody AssetReturnRequest request,Authentication auth){return operations.returnAsset(companyId,assetId,request,auth.getName());}

    @GetMapping("/maintenance")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'maintenance.read')")
    List<Map<String,Object>> maintenance(@RequestParam UUID companyId){return operations.maintenance(companyId);}
    @PostMapping("/maintenance") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'maintenance.write')")
    CreatedResource createMaintenance(@RequestParam UUID companyId,@Valid @RequestBody MaintenanceRequest request,Authentication auth){return operations.createMaintenance(companyId,request,auth.getName());}
    @PostMapping("/maintenance/{maintenanceId}/status")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'maintenance.write')")
    CreatedResource updateMaintenance(@RequestParam UUID companyId,@PathVariable UUID maintenanceId,@Valid @RequestBody DecisionRequest request,Authentication auth){return operations.updateMaintenance(companyId,maintenanceId,request,auth.getName());}

    @GetMapping("/licenses")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'license.read')")
    List<Map<String,Object>> licenses(@RequestParam UUID companyId){return operations.licenses(companyId);}
    @PostMapping("/licenses") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'license.write')")
    CreatedResource createLicense(@RequestParam UUID companyId,@Valid @RequestBody LicenseRequest request,Authentication auth){return operations.createLicense(companyId,request,auth.getName());}

    @GetMapping("/manufacturing")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'manufacturing.read')")
    List<Map<String,Object>> manufacturingOrders(@RequestParam UUID companyId){return operations.manufacturingOrders(companyId);}
    @PostMapping("/manufacturing") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'manufacturing.write')")
    CreatedResource createManufacturingOrder(@RequestParam UUID companyId,@Valid @RequestBody ManufacturingRequest request,Authentication auth){return operations.createManufacturingOrder(companyId,request,auth.getName());}
    @PostMapping("/manufacturing/{orderId}/status")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'manufacturing.write')")
    CreatedResource updateManufacturingOrder(@RequestParam UUID companyId,@PathVariable UUID orderId,@Valid @RequestBody ManufacturingProgressRequest request,Authentication auth){return operations.updateManufacturingOrder(companyId,orderId,request,auth.getName());}

    @GetMapping("/shipments")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'fulfillment.read')")
    List<Map<String,Object>> shipments(@RequestParam UUID companyId){return operations.shipments(companyId);}
    @PostMapping("/shipments") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'fulfillment.write')")
    CreatedResource createShipment(@RequestParam UUID companyId,@Valid @RequestBody ShipmentRequest request,Authentication auth){return operations.createShipment(companyId,request,auth.getName());}
    @PostMapping("/shipments/{shipmentId}/status")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'fulfillment.write')")
    CreatedResource updateShipment(@RequestParam UUID companyId,@PathVariable UUID shipmentId,@Valid @RequestBody DecisionRequest request,Authentication auth){return operations.updateShipment(companyId,shipmentId,request,auth.getName());}
}
