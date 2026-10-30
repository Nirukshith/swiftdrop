import { Component, input, output } from '@angular/core';
import { ButtonComponent } from '../button/button.component';
import { SpinnerComponent } from '../spinner/spinner.component';

/** Shown while data is loading. Announced politely to screen readers. */
@Component({
  selector: 'ui-loading-state',
  standalone: true,
  imports: [SpinnerComponent],
  template: `
    <div class="flex flex-col items-center justify-center gap-3 py-10 text-ink-muted" role="status" aria-live="polite">
      <ui-spinner size="lg" class="text-brand-600" />
      <p class="text-sm">{{ message() }}</p>
    </div>
  `,
})
export class LoadingStateComponent {
  readonly message = input('Loading…');
}

/** Shown when a list or page has no data. Project an optional call to action. */
@Component({
  selector: 'ui-empty-state',
  standalone: true,
  template: `
    <div class="flex flex-col items-center justify-center gap-2 py-10 text-center">
      <p class="text-base font-semibold text-ink">{{ title() }}</p>
      @if (message()) {
        <p class="max-w-sm text-sm text-ink-muted">{{ message() }}</p>
      }
      <div class="mt-2 empty:hidden"><ng-content /></div>
    </div>
  `,
})
export class EmptyStateComponent {
  readonly title = input('Nothing here yet');
  readonly message = input('');
}

/** Shown when loading failed. Emits (retry) when the retry button is pressed. */
@Component({
  selector: 'ui-error-state',
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <div class="flex flex-col items-center justify-center gap-2 py-10 text-center" role="alert">
      <p class="text-base font-semibold text-danger-700">{{ title() }}</p>
      @if (message()) {
        <p class="max-w-sm text-sm text-ink-muted">{{ message() }}</p>
      }
      @if (retryLabel()) {
        <ui-button class="mt-2" variant="secondary" size="sm" (click)="retry.emit()">{{ retryLabel() }}</ui-button>
      }
    </div>
  `,
})
export class ErrorStateComponent {
  readonly title = input('Something went wrong');
  readonly message = input('');
  /** Empty string hides the retry button. */
  readonly retryLabel = input('Try again');
  readonly retry = output<void>();
}
