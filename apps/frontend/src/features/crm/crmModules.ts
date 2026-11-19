import { BadgeDollarSign, FileSignature, Headphones, Megaphone, PackageCheck, TrendingUp, UsersRound } from 'lucide-react';

export const crmModules = [
  { key: 'customers', summaryKey: 'activeCustomers', label: 'Khách hàng', description: 'Hồ sơ khách hàng, người liên hệ và lịch sử tương tác.', permission: 'crm.customer.read', icon: UsersRound },
  { key: 'pipeline', summaryKey: 'openOpportunities', label: 'Sales Pipeline', description: 'Lead, cơ hội, giai đoạn bán hàng và giá trị dự kiến.', permission: 'crm.pipeline.read', icon: TrendingUp },
  { key: 'quotes', summaryKey: 'pendingQuotes', label: 'Báo giá', description: 'Phiên bản báo giá, chiết khấu và quy trình phê duyệt.', permission: 'crm.quote.read', icon: BadgeDollarSign },
  { key: 'contracts', summaryKey: 'expiringContracts', label: 'Hợp đồng', description: 'Thời hạn, giá trị, lịch thanh toán và nhắc gia hạn.', permission: 'crm.contract.read', icon: FileSignature },
  { key: 'orders', summaryKey: 'openOrders', label: 'Đơn hàng & doanh số', description: 'Đơn bán nội bộ, trạng thái xử lý và trả hàng.', permission: 'crm.order.read', icon: PackageCheck },
  { key: 'support', summaryKey: 'openTickets', label: 'Chăm sóc khách hàng', description: 'Ticket, SLA, khiếu nại và mức độ hài lòng.', permission: 'crm.support.read', icon: Headphones },
  { key: 'marketing', summaryKey: null, label: 'Marketing', description: 'Chiến dịch, nguồn lead, ngân sách và tỷ lệ chuyển đổi.', permission: 'crm.marketing.read', icon: Megaphone },
] as const;

export type CrmModuleKey = typeof crmModules[number]['key'];
