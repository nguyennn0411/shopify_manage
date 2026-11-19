import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Printer } from 'lucide-react';
import { Link, useOutletContext, useParams } from 'react-router-dom';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';

type QuoteDocument = Record<string, unknown> & { items?: Array<Record<string, unknown>> };

const value = (source: Record<string, unknown>, key: string) => source[key] == null ? '' : String(source[key]);
const amount = (source: Record<string, unknown>, key: string) => {
  const parsed = Number(source[key]);
  return Number.isFinite(parsed) ? parsed.toLocaleString('vi-VN') : '0';
};
const date = (source: Record<string, unknown>, key: string) => {
  const raw = value(source, key);
  if (!raw) return '—';
  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleDateString('vi-VN');
};

export function QuotePrintPage() {
  const { quoteId = '' } = useParams<{ quoteId: string }>();
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const query = useQuery({
    queryKey: ['crm', 'quote-document', companyId, quoteId],
    queryFn: async () => (await api.get<QuoteDocument>(`/crm/quotes/${quoteId}/document`, { params: { companyId } })).data,
    enabled: Boolean(companyId && quoteId),
  });

  if (!companyId) return <section className="quote-state">Hãy chọn công ty trước khi mở báo giá.</section>;
  if (query.isLoading) return <section className="quote-state">Đang chuẩn bị bản báo giá…</section>;
  if (query.isError || !query.data) return <section className="quote-state"><p>Không thể tải báo giá.</p><Link className="secondary" to="/crm/quotes">Quay lại</Link></section>;

  const document = query.data;
  const items = document.items ?? [];
  const currency = value(document, 'currency');
  return <article className="quote-document">
    <div className="quote-print-actions"><Link className="secondary" to="/crm/quotes"><ArrowLeft size={16}/>Quay lại</Link><button className="primary" onClick={() => window.print()}><Printer size={16}/>In / Lưu PDF</button></div>
    <header className="quote-letterhead"><div><p className="quote-company">{value(document, 'legal_name') || value(document, 'company_name') || company?.name}</p><p>{value(document, 'company_address')}</p><p>{[value(document, 'company_phone'), value(document, 'company_email')].filter(Boolean).join(' · ')}</p><p>{value(document, 'company_tax_code') && `Mã số thuế: ${value(document, 'company_tax_code')}`}</p></div><div className="quote-number"><span>Số báo giá</span><strong>{value(document, 'quote_number')}</strong><small>Phiên bản {value(document, 'version')}</small></div></header>
    <section className="quote-title"><h1>{value(document, 'template_title') || 'BÁO GIÁ'}</h1><p>Ngày lập: {date(document, 'created_at')} · Hiệu lực đến: {date(document, 'valid_until')}</p></section>
    <section className="quote-customer"><h2>Kính gửi</h2><strong>{value(document, 'customer_name')}</strong><p>{value(document, 'customer_address')}</p><p>{[value(document, 'customer_phone'), value(document, 'customer_email')].filter(Boolean).join(' · ')}</p><p>{value(document, 'tax_code') && `Mã số thuế: ${value(document, 'tax_code')}`}</p></section>
    <div className="quote-table-wrap"><table className="quote-table"><thead><tr><th>STT</th><th>Mã hàng</th><th>Mô tả</th><th>Số lượng</th><th>Đơn giá</th><th>CK (%)</th><th>Thành tiền</th></tr></thead><tbody>{items.map((item, index) => <tr key={value(item, 'id') || index}><td>{index + 1}</td><td>{value(item, 'item_code') || '—'}</td><td>{value(item, 'description')}</td><td>{amount(item, 'quantity')}</td><td>{amount(item, 'unit_price')}</td><td>{amount(item, 'discount_percent')}</td><td>{amount(item, 'line_total')}</td></tr>)}</tbody></table></div>
    <section className="quote-totals"><div><span>Tạm tính</span><strong>{amount(document, 'subtotal')} {currency}</strong></div><div><span>Chiết khấu</span><strong>{amount(document, 'discount_amount')} {currency}</strong></div><div><span>Thuế</span><strong>{amount(document, 'tax_amount')} {currency}</strong></div><div className="grand-total"><span>Tổng cộng</span><strong>{amount(document, 'total')} {currency}</strong></div></section>
    {value(document, 'terms') && <section className="quote-terms"><h2>Điều khoản</h2><p>{value(document, 'terms')}</p></section>}
    {value(document, 'template_footer') && <footer className="quote-footer">{value(document, 'template_footer')}</footer>}
  </article>;
}
