'use client';
import { useState } from 'react';
import { Dialog, DialogContent } from './ui/dialog';
import { Button } from './ui/button';

export type PendingConfirmation = { title: string; description: string; confirmLabel: string; run: () => Promise<void> };

export function ConfirmAction({ action, onClose }: { action: PendingConfirmation | null; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  async function confirm() {
    if (!action) return;
    setBusy(true);
    try { await action.run(); onClose(); } finally { setBusy(false); }
  }
  return <Dialog open={!!action} onOpenChange={open => { if (!open && !busy) onClose(); }}><DialogContent title={action?.title || 'Xác nhận thao tác'}>
    <p className="text-sm leading-6 text-muted">{action?.description}</p>
    <div className="mt-6 flex justify-end gap-2"><Button type="button" variant="ghost" disabled={busy} onClick={onClose}>Hủy</Button><Button type="button" disabled={busy} className="bg-red-700 hover:bg-red-800" onClick={confirm}>{busy ? 'Đang xử lý…' : action?.confirmLabel}</Button></div>
  </DialogContent></Dialog>;
}
