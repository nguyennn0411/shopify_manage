import * as React from 'react';
import { cn } from '@/lib/utils';
export function Input({ className, ...props }: React.ComponentProps<'input'>) { return <input className={cn('h-10 w-full rounded-lg border border-line bg-surface px-3 text-ink placeholder:text-muted', className)} {...props} />; }
