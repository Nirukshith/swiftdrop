import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonComponent, ButtonSize, ButtonVariant } from 'ui';
import { ReceiptService, receiptErrorMessage } from './receipt.service';

/**
 * "Download receipt" button for a delivered parcel (CU-05). Used on the parcel detail page and
 * in the history table; clicks do not reach a clickable table row.
 */
@Component({
  selector: 'app-receipt-button',
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <span class="inline-flex flex-col items-start gap-1" (click)="$event.stopPropagation()" (keydown.enter)="$event.stopPropagation()">
      <ui-button
        [variant]="variant()"
        [size]="size()"
        [loading]="downloading()"
        data-testid="receipt-button"
        (click)="download()"
      >
        Download receipt
        @if (trackingNumber()) {
          <span class="sr-only"> for {{ trackingNumber() }}</span>
        }
      </ui-button>
      @if (error()) {
        <span class="text-sm text-danger-700" role="alert" data-testid="receipt-error">{{ error() }}</span>
      }
    </span>
  `,
})
export class ReceiptButtonComponent {
  private readonly receipts = inject(ReceiptService);
  private readonly destroyRef = inject(DestroyRef);

  readonly parcelId = input.required<string>();
  /** Shown to screen readers, e.g. the tracking number, so list buttons are distinguishable. */
  readonly trackingNumber = input<string>('');
  readonly variant = input<ButtonVariant>('secondary');
  readonly size = input<ButtonSize>('md');

  readonly downloading = signal(false);
  readonly error = signal('');

  download(): void {
    if (this.downloading()) {
      return;
    }
    this.downloading.set(true);
    this.error.set('');
    this.receipts
      .download(this.parcelId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.downloading.set(false),
        error: (err: unknown) => {
          this.error.set(receiptErrorMessage(err));
          this.downloading.set(false);
        },
      });
  }
}
