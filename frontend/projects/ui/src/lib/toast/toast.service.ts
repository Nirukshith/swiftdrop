import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

/**
 * App-wide toast queue. Place <ui-toast-container /> once in the app shell, then call
 * `inject(ToastService).success('Saved')` anywhere.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly items = signal<Toast[]>([]);
  private nextId = 1;

  readonly toasts = this.items.asReadonly();

  /** Shows a toast and returns its id. durationMs 0 keeps it until dismissed. */
  show(message: string, type: ToastType = 'info', durationMs = 5000): number {
    const id = this.nextId++;
    this.items.update((list) => [...list, { id, type, message }]);
    if (durationMs > 0) {
      setTimeout(() => this.dismiss(id), durationMs);
    }
    return id;
  }

  success(message: string, durationMs?: number): number {
    return this.show(message, 'success', durationMs);
  }

  error(message: string, durationMs?: number): number {
    return this.show(message, 'error', durationMs);
  }

  warning(message: string, durationMs?: number): number {
    return this.show(message, 'warning', durationMs);
  }

  info(message: string, durationMs?: number): number {
    return this.show(message, 'info', durationMs);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((toast) => toast.id !== id));
  }

  clear(): void {
    this.items.set([]);
  }
}
