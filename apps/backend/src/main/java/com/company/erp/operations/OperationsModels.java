package com.company.erp.operations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OperationsModels {
    private OperationsModels() {}

    public record CreatedResource(UUID id, String status) {}
    public record OperationsSummary(long activeSuppliers, long pendingPurchaseRequests, long openPurchaseOrders,
        long lowStockItems, long assignedAssets, long maintenanceDue, long licensesDue, long shipmentsInTransit) {}

    public record SupplierRequest(String code, @NotBlank @Size(max=255) String name, @Size(max=80) String taxCode,
        @Size(max=254) String email, @Size(max=50) String phone, String address, @Size(max=80) String category,
        @Min(0) Integer leadTimeDays, @Size(max=255) String paymentTerms, String bankDetails,
        @Pattern(regexp="ACTIVE|INACTIVE|BLOCKED") String status, String notes) {}
    public record SupplierReviewRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
        @DecimalMin("0") @Max(100) BigDecimal qualityScore, @DecimalMin("0") @Max(100) BigDecimal deliveryScore,
        @DecimalMin("0") @Max(100) BigDecimal costScore, @DecimalMin("0") @Max(100) BigDecimal serviceScore,
        @Size(max=1000) String notes) {}

    public record WarehouseRequest(@NotBlank @Size(max=50) String code, @NotBlank @Size(max=180) String name,
        String address) {}
    public record InventoryItemRequest(@NotBlank @Size(max=80) String sku, @NotBlank @Size(max=255) String name,
        @Pattern(regexp="MATERIAL|PRODUCT|CONSUMABLE|SPARE_PART|OTHER") String itemType,
        @NotBlank @Size(max=30) String unit, @DecimalMin("0") BigDecimal minStock,
        @DecimalMin("0") BigDecimal standardCost, @Pattern(regexp="[A-Z]{3}") String currency) {}

    public record PurchaseRequestItemRequest(UUID inventoryItemId, @NotBlank @Size(max=500) String description,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity, @NotBlank @Size(max=30) String unit,
        @NotNull @DecimalMin("0") BigDecimal estimatedUnitPrice) {}
    public record PurchaseRequestCreate(UUID departmentId, UUID projectId, UUID budgetId, LocalDate requiredOn,
        @NotBlank @Size(max=500) String purpose, @Pattern(regexp="[A-Z]{3}") String currency,
        @NotEmpty @Valid List<PurchaseRequestItemRequest> items) {}
    public record DecisionRequest(@NotBlank String status, @Size(max=500) String note) {}

    public record PurchaseOrderItemRequest(UUID inventoryItemId, @NotBlank @Size(max=500) String description,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity, @NotBlank @Size(max=30) String unit,
        @NotNull @DecimalMin("0") BigDecimal unitPrice) {}
    public record PurchaseOrderRequest(@NotNull UUID supplierId, UUID purchaseRequestId, UUID warehouseId,
        LocalDate expectedOn, @Pattern(regexp="[A-Z]{3}") String currency,
        @NotEmpty @Valid List<PurchaseOrderItemRequest> items) {}
    public record ReceiptLineRequest(@NotNull UUID purchaseOrderItemId,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity) {}
    public record GoodsReceiptRequest(@NotNull UUID warehouseId, @NotNull LocalDate receivedOn,
        @Size(max=500) String note, @NotEmpty @Valid List<ReceiptLineRequest> lines) {}

    public record StockMovementRequest(@NotNull UUID inventoryItemId, UUID fromWarehouseId, UUID toWarehouseId,
        @Pattern(regexp="ISSUE|TRANSFER|ADJUSTMENT_IN|ADJUSTMENT_OUT|RETURN") String movementType,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity, @Size(max=500) String note) {}
    public record StockCountLineRequest(@NotNull UUID inventoryItemId,
        @NotNull @DecimalMin("0") BigDecimal countedQuantity) {}
    public record StockCountRequest(@NotNull UUID warehouseId, @NotNull LocalDate countedOn,
        @Size(max=500) String note, @NotEmpty @Valid List<StockCountLineRequest> lines) {}

    public record AssetRequest(String assetCode, @NotBlank @Size(max=255) String name,
        @NotBlank @Size(max=60) String assetType, @Size(max=180) String serialNumber, UUID purchaseOrderId,
        UUID warehouseId, UUID departmentId, LocalDate acquiredOn, @DecimalMin("0") BigDecimal purchaseCost,
        @Pattern(regexp="[A-Z]{3}") String currency, LocalDate warrantyUntil,
        @Pattern(regexp="NEW|GOOD|FAIR|DAMAGED") String conditionStatus, @Size(max=500) String note) {}
    public record AssetAssignmentRequest(@NotNull UUID employeeId, @NotNull LocalDate assignedOn,
        @Size(max=500) String note) {}
    public record AssetReturnRequest(@NotNull LocalDate returnedOn, @NotBlank @Size(max=60) String returnCondition,
        @Size(max=500) String note) {}

    public record MaintenanceRequest(@NotNull UUID assetId, UUID supplierId,
        @Pattern(regexp="PREVENTIVE|REPAIR|INSPECTION|UPGRADE") String maintenanceType,
        LocalDate scheduledOn, @DecimalMin("0") BigDecimal cost, @Pattern(regexp="[A-Z]{3}") String currency,
        @NotBlank @Size(max=500) String description) {}
    public record LicenseRequest(@NotBlank @Size(max=80) String code, @NotBlank @Size(max=255) String productName,
        UUID supplierId, UUID ownerEmployeeId, @Min(1) Integer seatCount, @Min(0) Integer assignedSeats,
        LocalDate startsOn, LocalDate renewsOn, @Size(max=30) String billingCycle,
        @DecimalMin("0") BigDecimal cost, @Pattern(regexp="[A-Z]{3}") String currency,
        @Size(max=500) String credentialReference) {}
    public record ManufacturingRequest(@NotNull UUID inventoryItemId, @NotNull UUID warehouseId,
        @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal plannedQuantity,
        LocalDate plannedStart, LocalDate plannedEnd, @Size(max=500) String note) {}
    public record ManufacturingProgressRequest(@NotBlank String status,
        @DecimalMin("0") BigDecimal completedQuantity, @Size(max=500) String note) {}
    public record ShipmentRequest(@Pattern(regexp="INBOUND|OUTBOUND|TRANSFER") String direction,
        UUID purchaseOrderId, UUID salesOrderId, UUID fromWarehouseId, String toAddress,
        @Size(max=120) String carrier, @Size(max=180) String trackingNumber,
        LocalDate expectedOn, @Size(max=500) String note) {}
}
