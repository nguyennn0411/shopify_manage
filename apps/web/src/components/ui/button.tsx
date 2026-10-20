import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';
const variants = cva('inline-flex items-center justify-center gap-2 rounded-lg text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent', {
  variants: { variant: { default: 'bg-accent text-white hover:brightness-95', secondary: 'bg-accentsoft text-accent hover:brightness-95', outline: 'border border-line bg-surface hover:bg-canvas', ghost: 'hover:bg-canvas text-muted hover:text-ink', destructive: 'bg-red-600 text-white hover:bg-red-700' }, size: { default: 'h-10 px-4', sm: 'h-8 px-3 text-xs', icon: 'h-10 w-10' } }, defaultVariants: { variant: 'default', size: 'default' },
});
export function Button({ className, variant, size, asChild = false, ...props }: React.ComponentProps<'button'> & VariantProps<typeof variants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : 'button';
  return <Comp className={cn(variants({ variant, size }), className)} {...props} />;
}
