import * as React from 'react';
import * as Primitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';
export const Dialog = Primitive.Root;
export const DialogTitle = Primitive.Title;
export const DialogDescription = Primitive.Description;
export const DialogContent = React.forwardRef<React.ElementRef<typeof Primitive.Content>, React.ComponentPropsWithoutRef<typeof Primitive.Content>>(({ className, children, ...props }, ref) => <Primitive.Portal><Primitive.Overlay className="fixed inset-0 z-50 bg-slate-950/40 backdrop-blur-sm"/><Primitive.Content ref={ref} className={cn('fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-auto rounded-2xl border bg-white p-7 shadow-xl', className)} {...props}>{children}<Primitive.Close className="absolute right-4 top-4 rounded p-1 hover:bg-slate-100" aria-label="Close"><X size={18}/></Primitive.Close></Primitive.Content></Primitive.Portal>);
DialogContent.displayName='DialogContent';
