import { Component, computed, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'info' | 'brand' | 'success' | 'warning' | 'danger';

const TONE_CLASSES: Record<BadgeTone, string> = {
  neutral: 'bg-neutral-100 text-neutral-700 ring-neutral-200',
  info: 'bg-info-50 text-info-700 ring-info-200',
  brand: 'bg-brand-50 text-brand-700 ring-brand-200',
  success: 'bg-success-50 text-success-700 ring-success-200',
  warning: 'bg-warning-50 text-warning-800 ring-warning-200',
  danger: 'bg-danger-50 text-danger-700 ring-danger-200',
};

/** Tones for the parcel lifecycle (contracts/openapi/parcel.yaml ParcelStatus). */
export const PARCEL_STATUS_TONES: Record<string, BadgeTone> = {
  CREATED: 'info',
  PICKED_UP: 'brand',
  IN_TRANSIT: 'brand',
  OUT_FOR_DELIVERY: 'warning',
  DELIVERED: 'success',
  FAILED: 'danger',
  CANCELLED: 'neutral',
};

/** `OUT_FOR_DELIVERY` -> `Out for delivery` */
export function humanizeStatus(status: string): string {
  const words = status.toLowerCase().split('_').filter(Boolean).join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Coloured pill for a status. Parcel statuses get their tone automatically; other statuses
 * (merchant PENDING, courier availability, ...) pass [tone].
 */
@Component({
  selector: 'ui-status-badge',
  standalone: true,
  host: { class: 'inline-flex' },
  template: `
    <span
      class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset"
      [class]="toneClasses()"
    >
      {{ text() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  /** Overrides the automatic tone. */
  readonly tone = input<BadgeTone | null>(null);
  /** Overrides the humanized status text. */
  readonly label = input('');

  protected readonly text = computed(() => this.label() || humanizeStatus(this.status()));
  protected readonly toneClasses = computed(
    () => TONE_CLASSES[this.tone() ?? PARCEL_STATUS_TONES[this.status()] ?? 'neutral'],
  );
}
