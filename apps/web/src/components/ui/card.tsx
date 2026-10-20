import * as React from 'react';
import { cn } from '@/lib/utils';
export function Card({ className, ...props }: React.ComponentProps<'div'>) { return <div className={cn('rounded-2xl border border-line bg-surface shadow-sm', className)} {...props} />; }
export function CardHeader({ className, ...props }: React.ComponentProps<'div'>) { return <div className={cn('p-5 pb-2', className)} {...props} />; }
export function CardContent({ className, ...props }: React.ComponentProps<'div'>) { return <div className={cn('p-5', className)} {...props} />; }
