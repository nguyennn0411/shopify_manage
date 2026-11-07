import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { ProtectedRoute } from './ProtectedRoute';

vi.mock('../../core/http/apiClient', () => ({ api: { get: vi.fn().mockRejectedValue({ response: { status: 401 } }) }, ensureCsrf: vi.fn() }));

describe('ProtectedRoute', () => {
  it('redirects an unauthenticated visitor to login', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/private']}><AuthProvider><Routes><Route element={<ProtectedRoute />}><Route path="/private" element={<div>Private</div>} /></Route><Route path="/login" element={<div>Login screen</div>} /></Routes></AuthProvider></MemoryRouter></QueryClientProvider>);
    expect(await screen.findByText('Login screen')).toBeInTheDocument();
  });
});
