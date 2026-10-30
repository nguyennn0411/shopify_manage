import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginPage from '@/app/login/page';

const replace = vi.fn();
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }));
vi.mock('@/lib/api', () => ({ api: vi.fn() }));

describe('LoginPage', () => {
  beforeEach(() => replace.mockReset());
  it('chặn dữ liệu không hợp lệ trước khi gọi API', async () => {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText('Email công việc'), { target: { value: 'not-an-email' } });
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: /đăng nhập/i }));
    await waitFor(() => expect(screen.getByText('Email không hợp lệ')).toBeInTheDocument());
    expect(screen.getByText('Mật khẩu phải có ít nhất 12 ký tự')).toBeInTheDocument();
  });
});
