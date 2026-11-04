import { Component, computed, input, output } from '@angular/core';
import { ButtonComponent } from 'ui';
import { StreamState } from './tracking-stream.service';

const TEXT: Record<StreamState, { label: string; hint: string; dot: string }> = {
  connecting: { label: 'Connecting…', hint: 'Starting live updates', dot: 'bg-neutral-400' },
  live: { label: 'Live', hint: 'New updates appear automatically', dot: 'bg-success-500 animate-pulse' },
  reconnecting: { label: 'Reconnecting…', hint: 'Connection lost, trying again', dot: 'bg-warning-500' },
  disconnected: { label: 'Live updates off', hint: 'Could not connect', dot: 'bg-danger-500' },
  ended: { label: 'Tracking complete', hint: 'This parcel will not change any more', dot: 'bg-neutral-400' },
};

/** Shows the live connection state. Never says "Live" unless the stream is actually open. */
@Component({
  selector: 'app-live-indicator',
  standalone: true,
  imports: [ButtonComponent],
  host: { class: 'flex flex-wrap items-center gap-2 text-sm' },
  template: `
    <span class="inline-flex items-center gap-2" role="status" aria-live="polite" [attr.data-state]="state()">
      <span class="h-2.5 w-2.5 rounded-full" [class]="text().dot" aria-hidden="true"></span>
      <span class="font-medium text-ink">{{ text().label }}</span>
      <span class="text-ink-muted">· {{ text().hint }}</span>
    </span>
    @if (state() === 'disconnected') {
      <ui-button variant="secondary" size="sm" (click)="retry.emit()">Try again</ui-button>
    }
  `,
})
export class LiveIndicatorComponent {
  readonly state = input.required<StreamState>();
  readonly retry = output<void>();

  protected readonly text = computed(() => TEXT[this.state()]);
}
