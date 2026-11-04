import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { humanizeStatus } from 'ui';
import { HAPPY_PATH, ParcelStatus, TimelineItem } from './tracking.model';

interface Row {
  status: ParcelStatus;
  label: string;
  occurredAt: string | null;
  reason: string | null;
  state: 'done' | 'current' | 'pending';
}

/**
 * Vertical status timeline: what has happened (oldest first, the latest marked as current),
 * then the steps still to come on the normal delivery path. Nothing is invented: pending
 * steps have no time and are shown greyed out.
 */
@Component({
  selector: 'app-tracking-timeline',
  standalone: true,
  imports: [DatePipe],
  host: { class: 'block' },
  template: `
    <ol class="relative" aria-label="Status timeline">
      @for (row of rows(); track $index; let last = $last) {
        <li class="relative flex gap-3 pb-6 last:pb-0" [attr.aria-current]="row.state === 'current' ? 'step' : null"
            [attr.data-state]="row.state">
          @if (!last) {
            <span class="absolute left-[0.6875rem] top-6 h-full w-px"
                  [class]="row.state === 'pending' ? 'bg-surface-border' : 'bg-brand-200'" aria-hidden="true"></span>
          }
          <span class="relative z-10 mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full ring-4 ring-surface"
                [class]="dotClasses(row)" aria-hidden="true">
            @if (row.state === 'done') {
              <svg viewBox="0 0 20 20" class="h-3.5 w-3.5" fill="currentColor"><path fill-rule="evenodd" d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.6l7.3-7.3a1 1 0 0 1 1.4 0Z" clip-rule="evenodd"/></svg>
            } @else if (row.state === 'current') {
              <span class="h-2.5 w-2.5 rounded-full bg-white"></span>
            }
          </span>
          <div class="min-w-0">
            <p class="text-sm font-medium" [class]="row.state === 'pending' ? 'text-ink-muted' : 'text-ink'">
              {{ row.label }}
              @if (row.state === 'current') {
                <span class="sr-only">(current status)</span>
              }
            </p>
            @if (row.occurredAt) {
              <p class="text-xs text-ink-muted"><time [attr.datetime]="row.occurredAt">{{ row.occurredAt | date: 'medium' }}</time></p>
            } @else {
              <p class="text-xs text-ink-muted">Pending</p>
            }
            @if (row.reason) {
              <p class="mt-1 text-xs text-ink-muted">{{ row.reason }}</p>
            }
          </div>
        </li>
      }
    </ol>
  `,
})
export class TrackingTimelineComponent {
  readonly items = input.required<TimelineItem[]>();
  readonly status = input.required<ParcelStatus | null>();

  protected readonly rows = computed<Row[]>(() => {
    const items = this.items();
    const rows: Row[] = items.map((item, index) => ({
      status: item.status,
      label: humanizeStatus(item.status),
      occurredAt: item.occurredAt,
      reason: item.reason ?? null,
      state: index === items.length - 1 ? 'current' : 'done',
    }));
    const status = this.status();
    const position = status ? HAPPY_PATH.indexOf(status) : -1;
    if (position >= 0) {
      for (const next of HAPPY_PATH.slice(position + 1)) {
        rows.push({ status: next, label: humanizeStatus(next), occurredAt: null, reason: null, state: 'pending' });
      }
    }
    return rows;
  });

  protected dotClasses(row: Row): string {
    if (row.state === 'pending') {
      return 'border-2 border-surface-border bg-surface';
    }
    if (row.status === 'FAILED') {
      return 'bg-danger-600 text-white';
    }
    if (row.status === 'CANCELLED') {
      return 'bg-neutral-500 text-white';
    }
    return row.state === 'current' ? 'bg-brand-600 text-white' : 'bg-brand-100 text-brand-700';
  }
}
