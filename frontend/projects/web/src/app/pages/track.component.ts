import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subject, catchError, distinctUntilChanged, map, startWith, switchMap, tap } from 'rxjs';
import {
  ButtonComponent,
  CardComponent,
  InputComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
  ShellComponent,
  StatusBadgeComponent,
} from 'ui';
import { LiveIndicatorComponent } from './tracking/live-indicator.component';
import { PageState, failure, followParcel } from './tracking/live-stream';
import { LiveTimeline } from './tracking/live-timeline';
import { TrackingApiService } from './tracking/tracking-api.service';
import { StreamState, TrackingStreamService } from './tracking/tracking-stream.service';
import { TrackingTimelineComponent } from './tracking/tracking-timeline.component';

/** SwiftDrop tracking numbers (same pattern as parcel-service's TrackingService). */
export const TRACKING_NUMBER = /^SD-\d{8}-[A-Z0-9]{6}$/;

/** Trims, removes inner spaces and upper-cases what people paste ("sd-20261026-a1b2c3 "). */
export function normalizeTrackingNumber(value: string): string {
  return value.replace(/\s+/g, '').toUpperCase();
}

type TrackPageState = PageState | 'search' | 'invalid';

/**
 * Public tracking page (CU-06, CU-04): no login, masked data only. Loads the public lookup,
 * then follows the public stream so the timeline updates without a refresh.
 * It is the page M3's notification emails link to.
 */
@Component({
  selector: 'app-track',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    ShellComponent,
    CardComponent,
    ButtonComponent,
    InputComponent,
    StatusBadgeComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    LiveIndicatorComponent,
    TrackingTimelineComponent,
  ],
  template: `
    <ui-shell homeLink="/" pageTitle="Track your parcel">
      <form class="mb-4 flex flex-col gap-2 sm:flex-row sm:items-start" role="search" (submit)="$event.preventDefault(); search()" novalidate data-testid="track-search">
        <ui-input
          class="w-full sm:max-w-sm"
          inputId="tracking-number-input"
          label="Tracking number"
          type="search"
          placeholder="SD-20261026-A1B2C3"
          autocomplete="off"
          [formControl]="query"
          [messages]="{ required: 'Enter your tracking number.', pattern: 'Use the format SD-YYYYMMDD-XXXXXX, for example SD-20261026-A1B2C3.' }"
        />
        <ui-button type="submit" class="sm:mt-6" data-testid="track-submit">Track</ui-button>
      </form>

      @switch (page()) {
        @case ('search') {
          <ui-empty-state title="Track a parcel"
                          message="Enter the tracking number from your receipt or email to see where your parcel is." />
        }
        @case ('invalid') {
          <ui-empty-state title="That is not a tracking number" data-testid="track-invalid"
                          message="Tracking numbers look like SD-20261026-A1B2C3. Check the number and try again." />
        }
        @case ('loading') {
          <ui-loading-state message="Looking up your parcel…" />
        }
        @case ('not-found') {
          <ui-empty-state title="Parcel not found"
                          message="Check the tracking number. It looks like SD-20261026-A1B2C3." />
        }
        @case ('error') {
          <ui-error-state title="Could not load tracking" [message]="error()" (retry)="reload()" />
        }
        @default {
          <ui-card>
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p class="text-sm text-ink-muted">Tracking number</p>
                <p class="font-mono text-lg font-semibold text-ink" data-testid="tracking-number">{{ timeline.trackingNumber() }}</p>
              </div>
              @if (timeline.status(); as status) {
                <ui-status-badge [status]="status" data-testid="current-status" />
              }
            </div>
            <dl class="mt-4 grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
              <div>
                <dt class="text-ink-muted">Recipient</dt>
                <dd class="font-medium text-ink">{{ recipient() }}</dd>
              </div>
              <div>
                <dt class="text-ink-muted">Delivering to</dt>
                <dd class="font-medium text-ink">{{ district() }}</dd>
              </div>
              <div>
                <dt class="text-ink-muted">Last update</dt>
                <dd class="font-medium text-ink" data-testid="last-updated">{{ timeline.lastUpdatedAt() | date: 'medium' }}</dd>
              </div>
            </dl>
            <app-live-indicator class="mt-4 border-t border-surface-border pt-4" [state]="stream()" (retry)="reconnect()" />
          </ui-card>

          <ui-card class="mt-4" title="Timeline">
            @if (timeline.isEmpty()) {
              <ui-empty-state title="No updates yet" message="Updates appear here as soon as the parcel moves." />
            } @else {
              <app-tracking-timeline [items]="timeline.items()" [status]="timeline.status()" />
            }
          </ui-card>
        }
      }
    </ui-shell>
  `,
})
export class TrackComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(TrackingApiService);
  private readonly streams = inject(TrackingStreamService);
  private readonly reload$ = new Subject<void>();
  private readonly retry$ = new Subject<void>();

  /** The number typed in the search box; checked here so a typo never reaches the server. */
  readonly query = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(TRACKING_NUMBER)],
  });

  readonly timeline = new LiveTimeline();
  readonly page = signal<TrackPageState>('loading');
  readonly stream = signal<StreamState>('connecting');
  readonly error = signal('');
  readonly recipient = signal('');
  readonly district = signal('');

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => normalizeTrackingNumber(params.get('trackingNo') ?? '')),
        distinctUntilChanged(),
        // switchMap: a new tracking number closes the previous stream before the next one opens.
        switchMap((trackingNumber) => this.reload$.pipe(startWith(undefined), switchMap(() => this.load(trackingNumber)))),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  reload(): void {
    this.reload$.next();
  }

  reconnect(): void {
    this.retry$.next();
  }

  /** Opens /track/<number> for a well-formed number; otherwise shows the format hint. */
  search(): void {
    this.query.setValue(normalizeTrackingNumber(this.query.value));
    this.query.markAsTouched();
    if (this.query.invalid) {
      return;
    }
    void this.router.navigate(['/track', this.query.value]);
  }

  private load(trackingNumber: string) {
    this.query.setValue(trackingNumber);
    this.timeline.clear();
    if (!trackingNumber) {
      this.page.set('search');
      return EMPTY;
    }
    if (!TRACKING_NUMBER.test(trackingNumber)) {
      // Same answer the server would give, without a request (and without a stream).
      this.query.markAsTouched();
      this.page.set('invalid');
      return EMPTY;
    }
    this.page.set('loading');
    this.stream.set('connecting');
    this.timeline.clear();
    return this.api.publicTracking(trackingNumber).pipe(
      catchError((err: unknown) => {
        const { state, message } = failure(err);
        this.page.set(state === 'not-found' ? 'not-found' : 'error');
        this.error.set(message);
        return EMPTY;
      }),
      tap((tracking) => {
        this.timeline.reset(tracking.trackingNumber, tracking.status, tracking.timeline, tracking.lastUpdatedAt);
        this.recipient.set(tracking.recipientNameMasked);
        this.district.set(tracking.dropoffDistrict);
        this.page.set('ready');
      }),
      switchMap((tracking) =>
        followParcel(this.streams, this.timeline, tracking.trackingNumber, tracking.status, (s) => this.stream.set(s), this.retry$),
      ),
    );
  }
}
