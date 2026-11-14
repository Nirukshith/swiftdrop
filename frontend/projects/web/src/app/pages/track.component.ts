import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { EMPTY, Subject, catchError, distinctUntilChanged, map, startWith, switchMap, tap } from 'rxjs';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  InputComponent,
  LoadingStateComponent,
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
    RouterModule,
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
    <!-- Full-Screen Live Tracking Layout (Matching My Deliveries Sample Design) -->
    <div class="w-full min-h-screen flex flex-col md:flex-row bg-white">
      <!-- Slim Left Sidebar Navigation Rail (Docked & Sticky) -->
      <aside
        class="w-full md:w-20 flex-shrink-0 flex md:flex-col items-center justify-between md:justify-start py-4 md:py-7 px-4 md:px-0 border-b md:border-b-0 md:border-r border-[#f0ebe5] bg-[#faf8f6] md:sticky md:top-0 md:h-screen md:space-y-7 z-20"
      >
        <!-- Brand Logo Icon -->
        <a
          routerLink="/"
          class="w-11 h-11 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 group cursor-pointer hover:scale-105 transition-transform"
          title="SwiftDrop Home"
        >
          <svg class="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
            <line x1="12" y1="22.08" x2="12" y2="12"/>
          </svg>
        </a>

        <!-- Navigation Icons Stack -->
        <nav class="flex md:flex-col items-center gap-3 sm:gap-4" aria-label="Customer Navigation">
          <!-- 1. Grid Icon (My Deliveries) -->
          <a
            routerLink="/customer"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer"
            title="My Deliveries"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="14" width="7" height="7" rx="2"></rect>
              <rect x="3" y="14" width="7" height="7" rx="2"></rect>
            </svg>
          </a>

          <!-- 2. Package Clock Icon (Live Tracking) - ACTIVE HERE -->
          <a
            routerLink="/track"
            class="rail-icon-btn rail-active relative w-10 h-10 rounded-xl flex items-center justify-center text-[#a53a26] bg-white shadow-sm transition-all cursor-pointer no-underline"
            title="Live Tracking"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-2.5 1.55v12.42a1.78 1.78 0 0 0 2.5 1.55L16.5 14.6a1.78 1.78 0 0 0 0-3.2z"></path>
              <circle cx="18" cy="6" r="3"></circle>
            </svg>
            <span class="hidden md:block absolute -right-[1px] top-2 bottom-2 w-1 bg-[#a53a26] rounded-l-full"></span>
          </a>

          <!-- 3. Address / Home Icon -->
          <a
            routerLink="/customer"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer"
            title="Address Book"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
              <polyline points="9 22 9 12 15 12 15 22"></polyline>
            </svg>
          </a>

          <!-- 4. Plus Button (Book New Delivery) -->
          <a
            routerLink="/customer"
            class="w-10 h-10 rounded-2xl bg-[#a53a26] hover:bg-[#7e2615] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 hover:scale-105 transition-all cursor-pointer"
            title="Book New Delivery"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </a>
        </nav>

        <!-- Sidebar Footer Utilities -->
        <div class="hidden md:flex flex-col items-center gap-3 pt-4 border-t border-[#f0ebe5]/60 mt-auto">
          <a
            routerLink="/"
            class="w-9 h-9 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-colors cursor-pointer"
            title="SwiftDrop Home"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </a>
        </div>
      </aside>

      <!-- Main Content Area -->
      <main class="flex-1 min-h-screen p-6 sm:p-8 lg:p-10 space-y-7 min-w-0 bg-white">
        <!-- Top Header: Search Bar & Actions -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[#f4efe9]">
          <!-- Search Form matching test contract -->
          <form
            class="relative flex-1 max-w-xl flex items-start gap-2"
            role="search"
            (submit)="$event.preventDefault(); search()"
            novalidate
            data-testid="track-search"
          >
            <div class="relative flex-1">
              <ui-input
                class="w-full tracking-search-input"
                inputId="tracking-number-input"
                label=""
                type="search"
                placeholder="SD-20261026-A1B2C3"
                autocomplete="off"
                [formControl]="query"
                [messages]="{ required: 'Enter your tracking number.', pattern: 'Use the format SD-YYYYMMDD-XXXXXX, for example SD-20261026-A1B2C3.' }"
              />
            </div>
            <ui-button
              type="submit"
              class="tracking-search-btn flex-shrink-0"
              data-testid="track-submit"
            >
              Track
            </ui-button>
          </form>

          <!-- Quick Back to Customer Portal -->
          <div class="flex items-center gap-3 self-end sm:self-auto">
            <a
              routerLink="/customer"
              class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#faf8f6] hover:bg-[#f0ebe5] border border-[#e8e2dc] text-xs font-bold text-[#1d1b2c] transition-colors no-underline"
            >
              <span>← Back to My Deliveries</span>
            </a>
          </div>
        </div>

        <!-- Title & Live Status Banner -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h1 class="text-2xl sm:text-3xl font-black text-[#1d1b2c] tracking-tight">
              Live Parcel Tracking
            </h1>
            <p class="text-xs text-[#6f6d85] font-medium mt-1">
              Real-time updates directly from courier telemetry and depot checkpoints.
            </p>
          </div>

          <div
            class="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-[#faf6f0] border border-[#a53a26]/20 text-[#a53a26] text-xs font-bold shadow-sm"
          >
            <span>⚡ Live Telemetry:</span>
            <span class="font-semibold text-[#1d1b2c]">Real-time stream enabled</span>
          </div>
        </div>

        <!-- Dynamic Content According to Page State -->
        @switch (page()) {
          @case ('search') {
            <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-10 text-center">
              <ui-empty-state
                title="Track a parcel"
                message="Enter the tracking number from your receipt or email to see where your parcel is."
              />
            </div>
          }
          @case ('invalid') {
            <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-10 text-center">
              <ui-empty-state
                title="That is not a tracking number"
                data-testid="track-invalid"
                message="Tracking numbers look like SD-20261026-A1B2C3. Check the number and try again."
              />
            </div>
          }
          @case ('loading') {
            <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-12 text-center">
              <ui-loading-state message="Looking up your parcel…" />
            </div>
          }
          @case ('not-found') {
            <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-10 text-center">
              <ui-empty-state
                title="Parcel not found"
                message="Check the tracking number. It looks like SD-20261026-A1B2C3."
              />
            </div>
          }
          @case ('error') {
            <div class="bg-[#faf8f6] rounded-3xl border border-[#eee7e0] p-8 text-center" role="alert">
              <ui-error-state title="Could not load tracking" [message]="error()" (retry)="reload()" />
            </div>
          }
          @default {
            <div class="space-y-7">
              <!-- Two Hero Cards Grid (Matching My Deliveries Dashboard) -->
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
                <!-- Hero Card 1: Parcel Info & Live Status -->
                <div
                  class="hero-summary-card bg-white rounded-3xl p-6 sm:p-7 border border-[#eee7e0] shadow-sm flex flex-col justify-between space-y-6"
                >
                  <div>
                    <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-[#f4efe9]">
                      <div>
                        <span class="text-[11px] font-bold text-[#6f6d85] block uppercase tracking-wider">
                          Tracking Number
                        </span>
                        <span
                          class="font-mono text-xl sm:text-2xl font-black text-[#1d1b2c] tracking-tight"
                          data-testid="tracking-number"
                        >
                          {{ timeline.trackingNumber() }}
                        </span>
                      </div>

                      @if (timeline.status(); as status) {
                        <ui-status-badge [status]="status" data-testid="current-status" />
                      }
                    </div>

                    <!-- Recipient & Destination Grid -->
                    <dl class="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-4 text-xs">
                      <div class="p-3.5 rounded-2xl bg-[#faf8f6] border border-[#f0ebe5]">
                        <dt class="text-[#6f6d85] font-bold mb-1">Recipient</dt>
                        <dd class="font-bold text-[#1d1b2c] text-sm truncate">{{ recipient() }}</dd>
                      </div>
                      <div class="p-3.5 rounded-2xl bg-[#faf8f6] border border-[#f0ebe5]">
                        <dt class="text-[#6f6d85] font-bold mb-1">Delivering to</dt>
                        <dd class="font-bold text-[#1d1b2c] text-sm truncate">{{ district() }}</dd>
                      </div>
                      <div class="p-3.5 rounded-2xl bg-[#faf8f6] border border-[#f0ebe5]">
                        <dt class="text-[#6f6d85] font-bold mb-1">Last update</dt>
                        <dd class="font-bold text-[#1d1b2c] text-sm truncate" data-testid="last-updated">
                          {{ timeline.lastUpdatedAt() | date: 'medium' }}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  <!-- Live Indicator Section -->
                  <div class="pt-4 border-t border-[#f0ebe5]">
                    <app-live-indicator [state]="stream()" (retry)="reconnect()" />
                  </div>
                </div>

                <!-- Hero Card 2: Unified Terracotta Card (#a53a26) with 3D Graphic -->
                <div
                  class="hero-track-card bg-[#a53a26] rounded-3xl p-6 sm:p-7 text-white shadow-md shadow-[#a53a26]/20 flex flex-col justify-between space-y-4 relative overflow-hidden"
                >
                  <div class="relative z-10">
                    <span class="text-[10px] font-black uppercase tracking-widest text-white/70 block mb-1">
                      SwiftDrop Express
                    </span>
                    <h3 class="text-xl sm:text-2xl font-black text-white tracking-tight leading-snug max-w-sm">
                      Track your delivery in real-time
                    </h3>
                    <p class="text-xs text-white/80 font-medium mt-2 max-w-xs leading-relaxed">
                      Telemetry events stream automatically from our network of dispatch centers and active couriers.
                    </p>
                  </div>

                  <div class="flex items-center justify-between pt-4 relative z-10">
                    <a
                      routerLink="/customer"
                      class="px-5 py-2.5 rounded-full bg-white hover:bg-[#faf6f0] text-[#a53a26] font-bold text-xs transition-all shadow-sm hover:scale-[1.02] active:scale-95 cursor-pointer no-underline inline-block"
                    >
                      View All Deliveries
                    </a>

                    <!-- 3D Isometric Parcels Graphic in Terracotta/White -->
                    <div class="relative w-24 h-24 flex-shrink-0 -mb-2 -mr-2">
                      <svg class="w-full h-full drop-shadow-md" viewBox="0 0 160 140" fill="none">
                        <!-- Terracotta/Dark Cube -->
                        <g transform="translate(68, 15)">
                          <polygon points="40,0 80,20 40,40 0,20" fill="#7e2615" />
                          <polygon points="0,20 40,40 40,84 0,64" fill="#5c1b0f" />
                          <polygon points="40,40 80,20 80,64 40,84" fill="#42130a" />
                        </g>
                        <!-- White/Sand Isometric Cube -->
                        <g transform="translate(15, 45)">
                          <polygon points="34,0 68,17 34,34 0,17" fill="#ffffff" stroke="#eee7e0" stroke-width="0.5" />
                          <polygon points="0,17 34,34 34,72 0,55" fill="#f8f6f4" stroke="#eee7e0" stroke-width="0.5" />
                          <polygon points="34,34 68,17 68,55 34,72" fill="#e8e2dc" stroke="#eee7e0" stroke-width="0.5" />
                        </g>
                      </svg>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Timeline Card (Matching Table Card Styling) -->
              <div class="timeline-card bg-white rounded-3xl border border-[#eee7e0] p-6 sm:p-8 shadow-sm space-y-6">
                <div class="flex items-center justify-between border-b border-[#f4efe9] pb-4">
                  <h2 class="text-lg font-black text-[#1d1b2c] tracking-tight">Delivery Journey & Timeline</h2>
                  <span class="inline-flex items-center px-3 py-1 rounded-full bg-[#faf6f0] text-[#a53a26] text-xs font-bold border border-[#a53a26]/10">
                    Live Telemetry
                  </span>
                </div>

                @if (timeline.isEmpty()) {
                  <ui-empty-state title="No updates yet" message="Updates appear here as soon as the parcel moves." />
                } @else {
                  <app-tracking-timeline [items]="timeline.items()" [status]="timeline.status()" />
                }
              </div>
            </div>
          }
        }
      </main>
    </div>
  `,
  styles: [
    `
      :host {
        --terracotta: #a53a26;
        --terracotta-dark: #7e2615;
        --ink: #1d1b2c;
        --ink-2: #3a384e;
        --ink-3: #6f6d85;
        --sand: #faf6f0;
        --disp: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
        display: block;
        font-family: var(--disp);
        background-color: #ffffff;
      }
      .rail-icon-btn.rail-active {
        color: var(--terracotta);
        background: #ffffff;
        box-shadow: 0 4px 12px rgba(165, 58, 38, 0.12);
      }
      :host ::ng-deep .tracking-search-input input {
        border-radius: 9999px !important;
        background-color: #faf8f6 !important;
        border-color: #e8e2dc !important;
        font-size: 0.75rem !important;
        font-weight: 600 !important;
        color: #1d1b2c !important;
        padding-top: 0.6rem !important;
        padding-bottom: 0.6rem !important;
      }
      :host ::ng-deep .tracking-search-input input:focus {
        border-color: var(--terracotta) !important;
        box-shadow: 0 0 0 2px rgba(165, 58, 38, 0.2) !important;
      }
      :host ::ng-deep .tracking-search-btn button {
        border-radius: 9999px !important;
        background-color: var(--terracotta) !important;
        color: #ffffff !important;
        font-weight: 700 !important;
        font-size: 0.75rem !important;
        padding: 0.5rem 1.25rem !important;
        box-shadow: 0 2px 6px rgba(165, 58, 38, 0.2) !important;
        transition: all 0.2s ease !important;
      }
      :host ::ng-deep .tracking-search-btn button:hover {
        background-color: var(--terracotta-dark) !important;
      }
    `,
  ],
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
