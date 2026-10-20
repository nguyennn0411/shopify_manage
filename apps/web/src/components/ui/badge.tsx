import * as React from 'react';
import { cn } from '@/lib/utils';
export function Badge({ className, ...props }: React.ComponentProps<'span'>) { return <span className={cn('inline-flex items-center rounded-full bg-accentsoft px-2.5 py-1 text-xs font-semibold text-accent', className)} {...props} />; }
