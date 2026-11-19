import { BadgeDollarSign, BarChart3, BookOpenCheck, ClipboardList, FolderKanban, Receipt, ReceiptText } from 'lucide-react';

export const financeModules = [
  { key:'transactions', summaryKey:'pendingTransactions', label:'Thu & chi', description:'Phiếu thu, phiếu chi, chứng từ và quy trình duyệt.', permission:'finance.transaction.read', icon:Receipt },
  { key:'budgets', summaryKey:'availableBudget', label:'Ngân sách', description:'Kế hoạch, hạn mức và thực tế theo phòng ban hoặc dự án.', permission:'finance.budget.read', icon:ClipboardList },
  { key:'invoices', summaryKey:'overdueInvoices', label:'Hóa đơn & công nợ', description:'Phải thu, phải trả, hạn thanh toán và nhắc nợ.', permission:'finance.invoice.read', icon:ReceiptText },
  { key:'advances', summaryKey:'pendingAdvances', label:'Tạm ứng & hoàn ứng', description:'Đề nghị, phê duyệt, giải ngân và đối soát chứng từ.', permission:'finance.advance.read', icon:BadgeDollarSign },
  { key:'project-costs', summaryKey:null, label:'Chi phí dự án', description:'Chi phí nhân sự, dịch vụ, phần mềm và vật tư theo dự án.', permission:'finance.project-cost.read', icon:FolderKanban },
  { key:'accounting', summaryKey:null, label:'Kế toán quản trị', description:'Hệ thống tài khoản, bút toán, sổ cái và khóa kỳ.', permission:'finance.accounting.read', icon:BookOpenCheck },
  { key:'reports', summaryKey:'profit', label:'Báo cáo quản trị', description:'Doanh thu, chi phí, lợi nhuận, dòng tiền và số dư công nợ.', permission:'finance.report.read', icon:BarChart3 },
] as const;

export type FinanceModuleKey = typeof financeModules[number]['key'];
