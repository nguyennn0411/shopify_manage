import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = { title: 'Company ERP', description: 'Quản trị doanh nghiệp Shopify' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="vi" suppressHydrationWarning><body>{children}</body></html>;
}
