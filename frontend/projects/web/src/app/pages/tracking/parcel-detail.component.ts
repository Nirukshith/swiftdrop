import { CurrencyPipe, DatePipe, Location } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from 'shared-auth';
import { EMPTY, Subject, Subscription, catchError, distinctUntilChanged, forkJoin, map, startWith, switchMap, tap } from 'rxjs';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
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
 * Styled with a 2-column side-by-side layout matching the Nomu Customer Portal design.
 */
@Component({
  selector: 'app-parcel-detail',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    CurrencyPipe,
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
    <!-- Full-Screen Edge-to-Edge Layout matching Customer Portal -->
    <div class="w-full min-h-screen flex flex-col md:flex-row bg-white font-sans text-[#1d1b2c]">
      <!-- Slim Left Sidebar Navigation Rail (Docked & Sticky) -->
      <aside
        class="w-full md:w-20 flex-shrink-0 flex md:flex-col items-center justify-between md:justify-start py-4 md:py-7 px-4 md:px-0 border-b md:border-b-0 md:border-r border-[#f0ebe5] bg-[#faf8f6] md:sticky md:top-0 md:h-screen md:space-y-7 z-20"
      >
        <!-- Brand Logo Icon -->
        <a
          routerLink="/customer"
          class="w-11 h-11 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 group cursor-pointer hover:scale-105 transition-transform"
          title="SwiftDrop Customer Portal"
        >
          <svg class="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
            <line x1="12" y1="22.08" x2="12" y2="12"/>
          </svg>
        </a>

        <!-- Navigation Icons Stack -->
        <nav class="flex md:flex-col items-center gap-3 sm:gap-4" aria-label="Customer Navigation">
          <!-- 1. Grid Icon (My Deliveries) - Active Rail Indicator -->
          <a
            routerLink="/customer"
            class="rail-icon-btn rail-active relative w-10 h-10 rounded-xl flex items-center justify-center text-[#a53a26] bg-white shadow-sm transition-all cursor-pointer no-underline"
            title="My Deliveries"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="14" width="7" height="7" rx="2"></rect>
              <rect x="3" y="14" width="7" height="7" rx="2"></rect>
            </svg>
            <span class="hidden md:block absolute -right-[1px] top-2 bottom-2 w-1 bg-[#a53a26] rounded-l-full"></span>
          </a>

          <!-- 2. Package Clock Icon (Live Tracking) -->
          <a
            routerLink="/track"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer"
            title="Live Tracking"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-2.5 1.55v12.42a1.78 1.78 0 0 0 2.5 1.55L16.5 14.6a1.78 1.78 0 0 0 0-3.2z"></path>
              <circle cx="18" cy="6" r="3"></circle>
            </svg>
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

        <!-- Bottom Actions (Sign Out / Home) -->
        <div class="hidden md:flex flex-col items-center gap-5 mt-auto pb-4">
          <button
            (click)="signOut()"
            class="text-[#6f6d85] hover:text-[#a53a26] transition-colors p-2 cursor-pointer"
            title="Sign Out"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </button>
        </div>
      </aside>

      <!-- Main Content Column -->
      <main class="flex-1 flex flex-col min-w-0 bg-[#ffffff] min-h-screen">
        <!-- Top Navigation Header (Breadcrumb removed) -->
        <header
          class="w-full flex items-center justify-between py-4 px-4 sm:px-8 md:px-12 border-b border-[#f0ebe5] bg-white sticky top-0 z-10"
        >
          <!-- Back Button -->
          <button
            type="button"
            (click)="goBack()"
            class="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-[#eee7e0] bg-[#faf8f6] hover:bg-[#f3ece4] text-[#1d1b2c] text-xs font-bold transition-all shadow-xs cursor-pointer group"
            title="Back to Deliveries"
          >
            <svg
              class="w-4 h-4 text-[#a53a26] group-hover:-translate-x-0.5 transition-transform"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              stroke-width="2.5"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            <span>Back to Deliveries</span>
          </button>

          <!-- User Profile Badge -->
          <div class="flex items-center gap-3">
            <div class="flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#faf8f6] border border-[#eee7e0]">
              <div
                class="w-7 h-7 rounded-full bg-[#a53a26] text-white flex items-center justify-center text-xs font-black shadow-xs"
              >
                {{ customerInitials() }}
              </div>
              <span class="text-xs font-bold text-[#1d1b2c] hidden sm:inline">{{ customerName() }}</span>
            </div>
          </div>
        </header>

        <!-- Page Content Body -->
        <div class="flex-1 p-4 sm:p-6 md:p-8 max-w-7xl w-full mx-auto space-y-6">
          @switch (page()) {
            @case ('loading') {
              <div class="py-16 text-center">
                <ui-loading-state message="Loading your parcel…" />
              </div>
            }
            @case ('not-found') {
              <div class="p-8 sm:p-12 rounded-3xl border border-[#eee7e0] bg-[#faf8f6] text-center shadow-xs">
                <ui-empty-state title="Parcel not found" message="It may have been removed, or the link is wrong.">
                  <div class="mt-4 flex justify-center">
                    <ui-button routerLink="/customer/parcels" variant="secondary">Back to my parcels</ui-button>
                  </div>
                </ui-empty-state>
              </div>
            }
            @case ('forbidden') {
              <div class="p-8 rounded-3xl border border-[#eee7e0] bg-[#faf8f6] shadow-xs">
                <ui-error-state title="No access" [message]="error()" retryLabel="" />
              </div>
            }
            @case ('unauthorized') {
              <div class="p-8 rounded-3xl border border-[#eee7e0] bg-[#faf8f6] shadow-xs">
                <ui-error-state title="Signed out" [message]="error()" retryLabel="" />
              </div>
            }
            @case ('error') {
              <div class="p-8 rounded-3xl border border-[#eee7e0] bg-[#faf8f6] shadow-xs">
                <ui-error-state title="Could not load this parcel" [message]="error()" (retry)="reload()" />
              </div>
            }
            @default {
              @if (parcel(); as p) {
                <!-- Side-by-Side 2-Column Grid Layout -->
                <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                  <!-- Left Column (7 cols): Tracking Details & Summary -->
                  <div class="lg:col-span-7 space-y-6">
                    <div class="rounded-3xl border border-[#eee7e0] bg-[#faf8f6] p-6 sm:p-7 space-y-6 shadow-xs">
                      <!-- Header: Tracking Number & Current Status -->
                      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-[#eee7e0]">
                        <div>
                          <p class="text-xs font-bold uppercase tracking-wider text-[#a53a26]">Tracking Number</p>
                          <h1
                            class="text-2xl sm:text-3xl font-black text-[#1d1b2c] tracking-tight font-mono mt-1"
                            data-testid="tracking-number"
                          >
                            {{ p.trackingNumber }}
                          </h1>
                        </div>
                        @if (timeline.status(); as status) {
                          <div class="flex-shrink-0">
                            <ui-status-badge [status]="status" data-testid="current-status" />
                          </div>
                        }
                      </div>

                      <!-- Visual Delivery Route Card -->
                      <div class="rounded-2xl bg-white border border-[#eee7e0] p-5 space-y-4 shadow-xs">
                        <div class="text-[11px] font-bold uppercase tracking-wider text-[#6f6d85]">Delivery Route</div>
                        <div class="relative pl-6 space-y-4">
                          <!-- Vertical route line -->
                          <div class="absolute left-2 top-2 bottom-2 w-0.5 bg-[#eee7e0]"></div>

                          <!-- Origin / Pickup -->
                          <div class="relative">
                            <span class="absolute -left-6 top-1 w-3 h-3 rounded-full bg-[#a53a26] ring-4 ring-[#faf6f0]"></span>
                            <div class="text-[11px] font-bold text-[#6f6d85] uppercase tracking-wide">Pickup Point</div>
                            <p class="text-sm font-black text-[#1d1b2c] mt-0.5">
                              {{ p.pickupAddress.line1 }}, {{ p.pickupAddress.city }}, {{ p.pickupAddress.district }}
                            </p>
                          </div>

                          <!-- Destination / Dropoff -->
                          <div class="relative pt-2">
                            <span class="absolute -left-6 top-3 w-3 h-3 rounded-full bg-[#2e7d32] ring-4 ring-[#faf6f0]"></span>
                            <div class="text-[11px] font-bold text-[#6f6d85] uppercase tracking-wide">Destination</div>
                            <p class="text-sm font-black text-[#1d1b2c] mt-0.5">
                              <span class="text-[#a53a26]">{{ p.recipientName }}</span> · {{ p.dropoffAddress.line1 }}, {{ p.dropoffAddress.city }}, {{ p.dropoffAddress.district }}
                            </p>
                          </div>
                        </div>
                      </div>

                      <!-- 2-Stat Grid: Specs & Last Updated -->
                      <dl class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-semibold">
                        <!-- Parcel Specs -->
                        <div class="p-4 rounded-2xl bg-white border border-[#eee7e0] space-y-1 shadow-xs">
                          <dt class="text-[#6f6d85] font-bold uppercase tracking-wider text-[11px]">Parcel</dt>
                          <dd class="text-[#1d1b2c] font-black text-sm pt-0.5 leading-snug">
                            {{ p.size }} · {{ p.weightKg }} kg · {{ p.fee | currency: 'LKR' : 'symbol' : '1.2-2' }}
                          </dd>
                        </div>

                        <!-- Last Updated -->
                        <div class="p-4 rounded-2xl bg-white border border-[#eee7e0] space-y-1 shadow-xs">
                          <dt class="text-[#6f6d85] font-bold uppercase tracking-wider text-[11px]">Last update</dt>
                          <dd class="text-[#1d1b2c] font-black text-sm pt-0.5 leading-snug" data-testid="last-updated">
                            {{ timeline.lastUpdatedAt() | date: 'medium' }}
                          </dd>
                        </div>
                      </dl>

                      <!-- Receipt Button (if DELIVERED) -->
                      @if (timeline.status() === 'DELIVERED') {
                        <div class="pt-1">
                          <app-receipt-button class="block" [parcelId]="p.id" [trackingNumber]="p.trackingNumber" />
                        </div>
                      }

                      <!-- Live Stream Indicator Bar -->
                      <app-live-indicator
                        class="mt-4 border-t border-[#eee7e0] pt-4 block"
                        [state]="stream()"
                        (retry)="reconnect()"
                      />
                    </div>

                    <!-- Rating Component (if DELIVERED) -->
                    @if (timeline.status() === 'DELIVERED') {
                      <app-parcel-rating class="block" [parcelId]="p.id" />
                    }
                  </div>

                  <!-- Right Column (5 cols): Timeline Beside the Details -->
                  <div class="lg:col-span-5 space-y-6">
                    <div class="rounded-3xl border border-[#eee7e0] bg-[#faf8f6] p-6 sm:p-7 space-y-5 shadow-xs lg:sticky lg:top-24">
                      <div class="flex items-center justify-between pb-3.5 border-b border-[#eee7e0]">
                        <div>
                          <h2 class="text-lg font-black text-[#1d1b2c] tracking-tight">Timeline</h2>
                          <p class="text-xs text-[#6f6d85] font-semibold mt-0.5">Real-time status updates & parcel journey</p>
                        </div>
                      </div>

                      @if (timeline.isEmpty()) {
                        <ui-empty-state title="No updates yet" message="Updates appear here as soon as the parcel moves." />
                      } @else {
                        <div class="bg-white rounded-2xl p-5 border border-[#eee7e0] shadow-xs">
                          <app-tracking-timeline [items]="timeline.items()" [status]="timeline.status()" />
                        </div>
                      }
                    </div>
                  </div>
                </div>
              }
            }
          }
        </div>
      </main>
    </div>
  `,
  styles: [
    `
      :host {
        --terracotta: #a53a26;
        --terracotta-dark: #7e2615;
        --coral: #a53a26;
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
    `,
  ],
})
export class ParcelDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
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

  customerName = computed(() => {
    const user = typeof (this.auth as any).currentUser === 'function' ? (this.auth as any).currentUser() : null;
    return user?.fullName || user?.email?.split('@')[0] || 'Customer';
  });

  customerInitials = computed(() => {
    const name = this.customerName();
    if (!name) return 'CU';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  });

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

  goBack(): void {
    if (typeof window !== 'undefined' && window.history.length > 1) {
      this.location.back();
    } else {
      this.router.navigate(['/customer']);
    }
  }

  signOut(): void {
    if (typeof (this.auth as any).logout === 'function') {
      (this.auth as any).logout().subscribe({
        next: () => this.router.navigate(['/login']),
        error: () => this.router.navigate(['/login']),
      });
    } else {
      this.router.navigate(['/login']);
    }
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
