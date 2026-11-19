import { Boxes, Factory, Handshake, Laptop, PackageCheck, ShoppingBasket, Truck, Wrench } from 'lucide-react';

export const operationsModules=[
  {key:'suppliers',summaryKey:'activeSuppliers',label:'Nhà cung cấp',description:'Hồ sơ, điều khoản và đánh giá hiệu suất nhà cung cấp.',permission:'supplier.read',icon:Handshake},
  {key:'procurement',summaryKey:'pendingPurchaseRequests',label:'Mua sắm',description:'Yêu cầu mua, duyệt ngân sách, đơn mua và nhận hàng.',permission:'procurement.read',icon:ShoppingBasket},
  {key:'inventory',summaryKey:'lowStockItems',label:'Kho hàng',description:'Danh mục vật tư, tồn kho, luân chuyển và kiểm kê.',permission:'inventory.read',icon:Boxes},
  {key:'assets',summaryKey:'assignedAssets',label:'Tài sản',description:'Thiết bị, người sử dụng và lịch sử bàn giao.',permission:'asset.read',icon:Laptop},
  {key:'maintenance',summaryKey:'maintenanceDue',label:'Bảo trì & sửa chữa',description:'Lịch bảo trì, yêu cầu sửa chữa, chi phí và kết quả.',permission:'maintenance.read',icon:Wrench},
  {key:'licenses',summaryKey:'licensesDue',label:'Giấy phép phần mềm',description:'Số ghế, chủ sở hữu, chi phí và lịch gia hạn.',permission:'license.read',icon:PackageCheck},
  {key:'manufacturing',summaryKey:null,label:'Sản xuất',description:'Lệnh sản xuất và nhập thành phẩm vào kho.',permission:'manufacturing.read',icon:Factory},
  {key:'shipments',summaryKey:'shipmentsInTransit',label:'Vận chuyển',description:'Chuyến nhập, xuất, tracking và ngoại lệ giao nhận.',permission:'fulfillment.read',icon:Truck},
] as const;
export type OperationsModuleKey=typeof operationsModules[number]['key'];
