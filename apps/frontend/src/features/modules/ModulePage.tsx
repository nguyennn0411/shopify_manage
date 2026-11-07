import { useOutletContext } from 'react-router-dom';
import type { Company } from '../../core/domain/types';

const copy: Record<string, { title: string; description: string }> = {
  stores: { title: 'Shopify Stores', description: 'OAuth, webhook và đồng bộ dữ liệu Shopify theo từng công ty.' },
  orders: { title: 'Sales & Orders', description: 'Đơn hàng, khách hàng, hoàn tiền và lợi nhuận.' },
  inventory: { title: 'Inventory Management', description: 'Kho, số lượng khả dụng, điều chuyển và kiểm kê.' },
  suppliers: { title: 'Supplier Management', description: 'Nhà cung cấp, báo giá, hợp đồng và đánh giá hiệu suất.' },
  procurement: { title: 'Procurement Management', description: 'Yêu cầu mua, đơn mua, thanh toán và nhận hàng.' },
  pim: { title: 'Product Information Management', description: 'SKU, biến thể, thông số, hình ảnh, giá vốn và đồng bộ Shopify.' },
  fulfillment: { title: 'Fulfillment Management', description: 'Pick/pack, vận chuyển, tracking và hoàn hàng.' },
  analytics: { title: 'Analytics', description: 'KPI, dự báo, marketing và báo cáo quản trị.' },
  security: { title: 'Bảo mật & Audit', description: 'Lịch sử đăng nhập, thay đổi quyền và thao tác dữ liệu.' },
  settings: { title: 'Cài đặt công ty', description: 'Cấu hình công ty, tích hợp và chính sách vận hành.' },
};

export function ModulePage({ module }: { module: keyof typeof copy }) {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const item = copy[module];
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'ERP'} / {module}</span><h1>{item.title}</h1><p>{item.description}</p></div><section className="panel"><h2>Phạm vi dữ liệu</h2>{companyId ? <p>API Spring chỉ trả dữ liệu khi Keycloak role và company membership đều hợp lệ.</p> : <p>Chọn công ty để truy cập module.</p>}<div className="security-tags"><span>Method Security</span><span>Company scope</span><span>Audit log</span><span>CSRF</span></div></section></>;
}
