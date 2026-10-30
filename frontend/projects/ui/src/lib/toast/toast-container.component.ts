import { Component, inject } from '@angular/core';
import { ToastService, ToastType } from './toast.service';

const TYPE_CLASSES: Record<ToastType, string> = {
  success: 'border-success-200 bg-success-50 text-success-800',
  error: 'border-danger-200 bg-danger-50 text-danger-800',
  warning: 'border-warning-200 bg-warning-50 text-warning-800',
  info: 'border-info-200 bg-info-50 text-info-800',
};

/** Renders ToastService toasts in the bottom-right corner. Add once, in the app shell. */
@Component({
  selector: 'ui-toast-container',
  standalone: true,
  template: `
    <div class="pointer-events-none fixed bottom-4 right-4 z-toast flex w-full max-w-sm flex-col gap-2 px-4 sm:px-0" aria-live="polite">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="pointer-events-auto flex items-start justify-between gap-3 rounded-control border px-4 py-3 text-sm shadow-overlay"
          [class]="typeClasses[toast.type]"
          [attr.role]="toast.type === 'error' ? 'alert' : 'status'"
        >
          <span>{{ toast.message }}</span>
          <button type="button" class="opacity-70 hover:opacity-100" aria-label="Dismiss" (click)="toastService.dismiss(toast.id)">✕</button>
        </div>
      }
    </div>
  `,
})
export class ToastContainerComponent {
  protected readonly toastService = inject(ToastService);
  protected readonly typeClasses = TYPE_CLASSES;
}
