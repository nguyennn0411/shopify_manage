'use client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

export function Providers({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false }, mutations: { retry: false } } }));
  useEffect(() => {
    const expired = () => { client.clear(); router.replace('/login?reason=session-expired'); };
    window.addEventListener('erp:session-expired', expired);
    return () => window.removeEventListener('erp:session-expired', expired);
  }, [client, router]);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
