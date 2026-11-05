import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from 'shared-auth';
import { EMPTY, Subject, Subscription, catchError, distinctUntilChanged, forkJoin, map, startWith, switchMap, tap } from 'rxjs';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
  ShellComponent,
  StatusBadgeComponent,
} from 'ui';
import { CUSTOMER_NAV } from '../customer-history/customer-nav';
import { LiveIndicatorComponent } from './live-indicator.component';
import { PageState, failure, followParcel } from './live-stream';
import { ParcelRatingComponent } from './parcel-rating.component';
import { ReceiptButtonComponent } from './receipt-button.component';
import { LiveTimeline } from './live-timeline';
import { TrackingApiService } from './tracking-api.service';
import { StreamState, TrackingStreamService } from './tracking-stream.service';
import { TrackingTimelineComponent } from './tracking-timeline.component';
import { ParcelDetail } from './tracking.model';

/**
 * Signed-in parcel detail with a live timeline (CU-04, CU-05; docs/tracking-design.md 3.5).
 * Full details and history come from the authenticated APIs (owner check on the server);
 * live updates come from the public stream, which carries only status and time.
 */
@Component({
  selector: 'app-parcel-detail',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    CurrencyPipe,
    ShellComponent,
    CardComponent,
    ButtonComponent,
    StatusBadgeComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    LiveIndicatorComponent,
    TrackingTimelineComponent,
    ReceiptButtonComponent,
    ParcelRatingComponent,
  ],
  template: `
    <ui-shell [navItems]="nav" homeLink="/customer/dashboard" pageTitle="Parcel details">
      @switch (page()) {
        @case ('loading') {
          <ui-loading-state message="Loading your parcel…" />
        }
        @case ('not-found') {
          <ui-empty-state title="Parcel not found" message="It may have been removed, or the link is wrong.">
            <ui-button routerLink="/customer/parcels" variant="secondary">Back to my parcels</ui-button>
          </ui-empty-state>
        }
        @case ('forbidden') {
          <ui-error-state title="No access" [message]="error()" retryLabel="" />
        }
        @case ('unauthorized') {
          <ui-error-state title="Signed out" [message]="error()" retryLabel="" />
        }
        @case ('error') {
          <ui-error-state title="Could not load this parcel" [message]="error()" (retry)="reload()" />
        }
        @default {
          @if (parcel(); as p) {
            <ui-card>
              <div class="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p class="text-sm text-ink-muted">Tracking number</p>
                  <p class="font-mono text-lg font-semibold text-ink" data-testid="tracking-number">{{ p.trackingNumber }}</p>
                </div>
                @if (timeline.status(); as status) {
                  <ui-status-badge [status]="status" data-testid="current-status" />
                }
              </div>
              <dl class="mt-4 grid grid-cols-1 gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt class="text-ink-muted">From</dt>
                  <dd class="text-ink">{{ p.pickupAddress.line1 }}, {{ p.pickupAddress.city }}, {{ p.pickupAddress.district }}</dd>
                </div>
                <div>
                  <dt class="text-ink-muted">To</dt>
                  <dd class="text-ink">{{ p.recipientName }} · {{ p.dropoffAddress.line1 }}, {{ p.dropoffAddress.city }}, {{ p.dropoffAddress.district }}</dd>
                </div>
                <div>
                  <dt class="text-ink-muted">Parcel</dt>
                  <dd class="text-ink">{{ p.size }} · {{ p.weightKg }} kg · {{ p.fee | currency: 'LKR' : 'symbol' : '1.2-2' }}</dd>
                </div>
                <div>
                  <dt class="text-ink-muted">Last update</dt>
                  <dd class="text-ink" data-testid="last-updated">{{ timeline.lastUpdatedAt() | date: 'medium' }}</dd>
                </div>
              </dl>
              @if (timeline.status() === 'DELIVERED') {
                <app-receipt-button class="mt-4 block" [parcelId]="p.id" [trackingNumber]="p.trackingNumber" />
              }
              <app-live-indicator class="mt-4 border-t border-surface-border pt-4" [state]="stream()" (retry)="reconnect()" />
            </ui-card>

            @if (timeline.status() === 'DELIVERED') {
              <app-parcel-rating class="mt-4 block" [parcelId]="p.id" />
            }

            <ui-card class="mt-4" title="Timeline">
              @if (timeline.isEmpty()) {
                <ui-empty-state title="No updates yet" message="Updates appear here as soon as the parcel moves." />
              } @else {
                <app-tracking-timeline [items]="timeline.items()" [status]="timeline.status()" />
              }
            </ui-card>
          }
        }
      }
    </ui-shell>
  `,
})
export class ParcelDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(TrackingApiService);
  private readonly streams = inject(TrackingStreamService);
  private readonly auth = inject(AuthService);
  private readonly reload$ = new Subject<void>();
  private readonly retry$ = new Subject<void>();
  private readonly subscription: Subscription;

  protected readonly nav = CUSTOMER_NAV;
  readonly timeline = new LiveTimeline();
  readonly page = signal<PageState>('loading');
  readonly stream = signal<StreamState>('connecting');
  readonly error = signal('');
  readonly parcel = signal<ParcelDetail | null>(null);

  constructor() {
    this.subscription = this.route.paramMap
      .pipe(
        map((params) => params.get('id') ?? ''),
        distinctUntilChanged(),
        // switchMap: opening another parcel closes the previous stream first.
        switchMap((id) => this.reload$.pipe(startWith(undefined), switchMap(() => this.load(id)))),
        takeUntilDestroyed(),
      )
      .subscribe();

    // Logout: close the stream (no reconnect loop) and drop the parcel data from memory.
    effect(() => {
      if (!this.auth.isAuthenticated()) {
        this.subscription.unsubscribe();
        this.timeline.clear();
        this.parcel.set(null);
        this.stream.set('disconnected');
        this.error.set('Your session has ended. Please sign in again.');
        this.page.set('unauthorized');
      }
    });
  }

  reload(): void {
    this.reload$.next();
  }

  reconnect(): void {
    this.retry$.next();
  }

  private load(id: string) {
    this.page.set('loading');
    this.stream.set('connecting');
    this.timeline.clear();
    this.parcel.set(null);
    return forkJoin({ parcel: this.api.parcel(id), history: this.api.history(id) }).pipe(
      catchError((err: unknown) => {
        const { state, message } = failure(err);
        this.page.set(state);
        this.error.set(message);
        return EMPTY;
      }),
      tap(({ parcel, history }) => {
        this.parcel.set(parcel);
        this.timeline.reset(
          parcel.trackingNumber,
          parcel.status,
          history.entries.map((entry) => ({ status: entry.toStatus, occurredAt: entry.occurredAt, reason: entry.reason })),
          null,
        );
        this.page.set('ready');
      }),
      switchMap(({ parcel }) =>
        followParcel(this.streams, this.timeline, parcel.trackingNumber, parcel.status, (s) => this.stream.set(s), this.retry$),
      ),
    );
  }
}
