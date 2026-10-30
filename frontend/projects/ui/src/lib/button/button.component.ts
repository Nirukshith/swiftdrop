import { Component, computed, input } from '@angular/core';
import { SpinnerComponent } from '../spinner/spinner.component';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md';

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 focus-visible:ring-brand-500',
  secondary: 'bg-surface text-ink border border-surface-border hover:bg-surface-muted focus-visible:ring-brand-500',
  danger: 'bg-danger-600 text-white hover:bg-danger-700 focus-visible:ring-danger-500',
  ghost: 'bg-transparent text-brand-700 hover:bg-brand-50 focus-visible:ring-brand-500',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
};

/**
 * Button with variants and a loading state. Listen with (click) on <ui-button>;
 * no click is emitted while disabled or loading.
 */
@Component({
  selector: 'ui-button',
  standalone: true,
  imports: [SpinnerComponent],
  host: { '[class.block]': 'fullWidth()', '[class.inline-block]': '!fullWidth()' },
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      [attr.aria-busy]="loading() || null"
      [class]="classes()"
    >
      @if (loading()) {
        <ui-spinner />
      }
      <ng-content />
    </button>
  `,
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly fullWidth = input(false);

  protected readonly classes = computed(
    () =>
      'inline-flex items-center justify-center gap-2 rounded-control font-medium transition-colors ' +
      'focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ' +
      'disabled:cursor-not-allowed disabled:opacity-60 ' +
      `${VARIANT_CLASSES[this.variant()]} ${SIZE_CLASSES[this.size()]}` +
      (this.fullWidth() ? ' w-full' : ''),
  );
}
