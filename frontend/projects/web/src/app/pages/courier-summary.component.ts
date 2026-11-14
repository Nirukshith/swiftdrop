import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'shared-auth';
import { CourierDailySummary } from './courier/courier.model';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Full-Screen Courier Layout (Matching Courier Run Sheet & Customer Portal) -->
    <div class="w-full min-h-screen flex flex-col md:flex-row bg-white">
      <!-- Slim Left Sidebar Navigation Rail (Docked & Sticky) -->
      <aside
        class="w-full md:w-20 flex-shrink-0 flex md:flex-col items-center justify-between md:justify-start py-4 md:py-7 px-4 md:px-0 border-b md:border-b-0 md:border-r border-[#f0ebe5] bg-[#faf8f6] md:sticky md:top-0 md:h-screen md:space-y-7 z-20"
      >
        <!-- Brand Logo Icon -->
        <a
          href="/"
          (click)="$event.preventDefault(); navigateTo('/')"
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
        <nav class="flex md:flex-col items-center gap-3 sm:gap-4" aria-label="Courier Navigation">
          <!-- 1. Grid / Deliveries Run Sheet Icon -->
          <a
            href="/courier"
            (click)="$event.preventDefault(); navigateTo('/courier')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Courier Run Sheet"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="14" width="7" height="7" rx="2"></rect>
              <rect x="3" y="14" width="7" height="7" rx="2"></rect>
            </svg>
          </a>

          <!-- 2. Profile & Availability Icon -->
          <a
            href="/courier/profile"
            (click)="$event.preventDefault(); navigateTo('/courier/profile')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Profile & Availability"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </a>

          <!-- 3. Daily Summary Icon (Active) -->
          <a
            href="/courier/summary"
            (click)="$event.preventDefault(); navigateTo('/courier/summary')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#a53a26] bg-white shadow-2xs transition-all cursor-pointer no-underline"
            title="Daily Summary"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
            <span class="hidden md:block absolute -right-[1px] top-2 bottom-2 w-1 bg-[#a53a26] rounded-l-full"></span>
          </a>

          <!-- 4. Track a Parcel Link -->
          <a
            href="/track"
            (click)="$event.preventDefault(); navigateTo('/track')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Track a Parcel"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-2.5 1.55v12.42a1.78 1.78 0 0 0 2.5 1.55L16.5 14.6a1.78 1.78 0 0 0 0-3.2z"></path>
              <circle cx="18" cy="6" r="3"></circle>
            </svg>
          </a>
        </nav>

        <!-- Sidebar Footer Utilities -->
        <div class="hidden md:flex flex-col items-center gap-3 pt-4 border-t border-[#f0ebe5]/60 mt-auto">
          <button
            type="button"
            id="courier-signout-btn"
            (click)="signOut()"
            class="w-9 h-9 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-colors cursor-pointer"
            title="Sign Out"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </button>
        </div>
      </aside>

      <!-- Main Content Area (Edge-to-Edge Full Screen) -->
      <main class="flex-1 min-h-screen p-6 sm:p-8 lg:p-10 space-y-7 min-w-0 bg-white">
        <!-- Top Header: Search Bar & User Profile (Matching Customer Portal) -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-[#f4efe9]">
          <!-- Quick search / back link -->
          <div class="relative flex-1 max-w-lg">
            <svg
              class="w-4 h-4 text-[#a09eaf] absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
            >
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input
              type="text"
              placeholder="Search or press Enter to return to Run Sheet..."
              (keydown.enter)="navigateTo('/courier')"
              class="w-full pl-11 pr-8 py-2.5 rounded-full bg-[#faf8f6] border border-[#e8e2dc] text-xs font-semibold text-[#1d1b2c] placeholder-[#a09eaf] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] transition-all"
            />
          </div>

          <!-- User Profile Dropdown (Matching Customer Portal) -->
          <div class="relative flex items-center gap-3 self-end sm:self-auto">
            <button
              type="button"
              id="profile-menu-btn"
              (click)="toggleProfileMenu()"
              aria-haspopup="menu"
              [attr.aria-expanded]="profileMenuOpen()"
              class="flex items-center gap-2.5 px-3 py-1.5 rounded-full bg-[#faf8f6] border border-[#e8e2dc] hover:border-[#a53a26]/40 transition-colors cursor-pointer"
            >
              <span
                class="w-8 h-8 rounded-full bg-[#a53a26] text-white flex items-center justify-center font-bold text-xs uppercase shadow-sm"
              >
                {{ courierInitials() }}
              </span>
              <span class="text-xs font-bold text-[#1d1b2c]">{{ courierDisplayName() }}</span>
              <svg
                class="w-3.5 h-3.5 text-[#6f6d85] transition-transform"
                [class.rotate-180]="profileMenuOpen()"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
              >
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>

            <div
              *ngIf="profileMenuOpen()"
              role="menu"
              id="profile-menu-panel"
              class="absolute right-0 top-full mt-2 w-56 rounded-2xl bg-white border border-[#e8e2dc] shadow-lg shadow-[#1d1b2c]/5 py-2 z-30"
            >
              <div class="px-4 py-2 border-b border-[#f0ebe5]">
                <p class="text-xs font-bold text-[#1d1b2c] truncate">{{ courierDisplayName() }}</p>
                <p class="text-[11px] text-[#6f6d85] truncate">{{ courierEmail() }}</p>
              </div>
              <button
                type="button"
                role="menuitem"
                id="profile-menu-settings"
                (click)="openSettings()"
                class="w-full flex items-center gap-2.5 px-4 py-2.5 text-xs font-bold text-[#1d1b2c] hover:bg-[#faf8f6] hover:text-[#a53a26] transition-colors cursor-pointer"
              >
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="3"></circle>
                  <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                </svg>
                <span>Settings</span>
              </button>
              <button
                type="button"
                role="menuitem"
                id="profile-menu-logout"
                (click)="logOutFromMenu()"
                class="w-full flex items-center gap-2.5 px-4 py-2.5 text-xs font-bold text-[#a53a26] hover:bg-[#faf8f6] transition-colors cursor-pointer"
              >
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                  <polyline points="16 17 21 12 16 7"></polyline>
                  <line x1="21" y1="12" x2="9" y2="12"></line>
                </svg>
                <span>Log out</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Page Content Section -->
        <div class="space-y-6">
          <div
            class="bg-white rounded-3xl p-6 sm:p-7 border border-[#e8e2dc] shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4"
          >
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 flex-shrink-0">
                <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                  <line x1="16" y1="2" x2="16" y2="6"></line>
                  <line x1="8" y1="2" x2="8" y2="6"></line>
                  <line x1="3" y1="10" x2="21" y2="10"></line>
                </svg>
              </div>
              <div>
                <span class="portal-pill">Daily Summary</span>
                <h1 class="portal-title text-2xl font-black text-[#1d1b2c] tracking-tight mt-0.5">
                  Daily Delivery Summary
                </h1>
                <p class="text-xs font-semibold text-[#6f6d85] mt-0.5">{{ summary()?.date || 'Sri Lanka business date' }}</p>
              </div>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <label class="flex items-center gap-2 text-xs font-bold text-[#6f6d85]">
                View date
                <input
                  type="date"
                  [value]="selectedDate()"
                  (change)="selectDate($any($event.target).value)"
                  class="rounded-full border border-[#e8e2dc] bg-[#faf8f6] px-4 py-2 text-xs font-bold text-[#1d1b2c] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] transition-all"
                />
              </label>
              <a
                href="/courier"
                (click)="$event.preventDefault(); navigateTo('/courier')"
                class="inline-flex items-center gap-1.5 px-4 py-2 rounded-full border border-[#e8e2dc] hover:border-[#a53a26] text-xs font-bold text-[#6f6d85] hover:text-[#a53a26] transition-colors cursor-pointer"
              >
                ← Back to Run Sheet
              </a>
            </div>
          </div>

          <p
            *ngIf="loading()"
            class="rounded-3xl border border-[#e8e2dc] bg-white p-8 text-[#6f6d85] font-semibold text-center shadow-sm"
          >
            Loading daily summary...
          </p>

          <div *ngIf="error()" class="rounded-3xl border border-rose-200 bg-rose-50 p-6 text-rose-800 shadow-sm">
            <p class="font-bold">{{ error() }}</p>
            <button
              type="button"
              class="mt-3 rounded-full bg-rose-700 hover:bg-rose-800 px-5 py-2 text-xs font-bold text-white shadow-sm transition-all cursor-pointer"
              (click)="load()"
            >
              Retry
            </button>
          </div>

          <div
            *ngIf="!loading() && !error() && !summary()"
            class="rounded-3xl border border-dashed border-[#e8e2dc] bg-white p-10 text-center text-[#6f6d85] font-medium shadow-sm"
          >
            Summary unavailable.
          </div>

          <ng-container *ngIf="summary() as daily">
            <!-- Stat Cards Grid -->
            <div class="grid gap-4 sm:grid-cols-3">
              <div class="rounded-3xl border border-[#e8e2dc] bg-white p-6 shadow-sm">
                <p class="text-[10px] font-bold uppercase tracking-wider text-[#a09eaf]">Total deliveries</p>
                <p class="mt-2 text-3xl font-black text-[#1d1b2c]">{{ daily.totalDeliveries }}</p>
              </div>
              <div class="rounded-3xl border border-[#fef3c7] bg-[#fffbeb] p-6 shadow-sm">
                <p class="text-[10px] font-bold uppercase tracking-wider text-amber-700">Pending</p>
                <p class="mt-2 text-3xl font-black text-amber-800">{{ daily.pendingCount }}</p>
              </div>
              <div class="rounded-3xl border border-[#d1fae5] bg-[#ecfdf5] p-6 shadow-sm">
                <p class="text-[10px] font-bold uppercase tracking-wider text-emerald-700">Completed</p>
                <p class="mt-2 text-3xl font-black text-emerald-800">{{ daily.completedCount }}</p>
              </div>
            </div>

            <div
              *ngIf="daily.deliveries.length === 0"
              class="rounded-3xl border border-dashed border-[#e8e2dc] bg-white p-10 text-center text-[#6f6d85] font-medium shadow-sm"
            >
              No deliveries for this business date.
            </div>

            <div
              *ngIf="daily.deliveries.length > 0"
              class="overflow-hidden rounded-3xl border border-[#eee7e0] bg-white shadow-sm"
            >
              <div class="border-b border-[#f4efe9] px-6 py-4 bg-[#faf8f6]/60">
                <h2 class="font-black text-sm text-[#1d1b2c] uppercase tracking-wider">Delivery cards</h2>
              </div>
              <div
                *ngFor="let delivery of daily.deliveries"
                class="grid gap-3 border-b border-[#f4efe9] px-6 py-4.5 last:border-0 sm:grid-cols-5 sm:items-center hover:bg-[#faf8f6]/40 transition-colors"
              >
                <div>
                  <p class="font-mono text-xs font-black text-[#1d1b2c]">{{ delivery.trackingNumber }}</p>
                  <p class="text-xs text-[#6f6d85] font-medium mt-0.5">{{ delivery.recipientName }}</p>
                </div>
                <p class="text-xs font-semibold text-[#1d1b2c] sm:col-span-2">{{ delivery.dropoffAddress }}</p>
                <span
                  class="w-fit rounded-full px-3 py-0.5 text-xs font-extrabold uppercase tracking-wider"
                  [class.bg-[#ecfdf5]]="delivery.status === 'DELIVERED'"
                  [class.text-emerald-800]="delivery.status === 'DELIVERED'"
                  [class.bg-amber-50]="delivery.status !== 'DELIVERED'"
                  [class.text-amber-800]="delivery.status !== 'DELIVERED'"
                >
                  ● {{ delivery.status }}
                </span>
                <p class="text-xs font-bold text-[#a09eaf]">
                  Attempt {{ delivery.attemptCount }} · #{{ delivery.sequenceOrder }}
                </p>
              </div>
            </div>
          </ng-container>
        </div>
      </main>
    </div>
  `,
  styles: [
    `
      :host {
        --terracotta: #a53a26;
        --terracotta-dark: #7e2615;
        --ink: #1d1b2c;
        --ink-3: #6f6d85;
        --sand: #faf6f0;
        --disp: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
        display: block;
        font-family: var(--disp);
      }
      .portal-title {
        font-family: var(--disp);
      }
      .portal-pill {
        display: inline-block;
        padding: 0.2rem 0.65rem;
        font-size: 0.7rem;
        font-weight: 800;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--terracotta);
        background: rgba(165, 58, 38, 0.08);
        border-radius: 9999px;
      }
    `,
  ],
})
export class CourierSummaryComponent {
  private readonly service = inject(CourierService);
  private readonly router = inject(Router, { optional: true });
  private readonly authService = inject(AuthService, { optional: true });

  readonly summary = signal<CourierDailySummary | null>(null);
  readonly selectedDate = signal('');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly profileMenuOpen = signal<boolean>(false);

  readonly courierDisplayName = computed(() => {
    return this.authService?.currentUser()?.fullName || 'Sample Courier';
  });

  readonly courierEmail = computed(() => {
    return this.authService?.currentUser()?.email || 'courier@swiftdrop.lk';
  });

  readonly courierInitials = computed(() => {
    const name = this.courierDisplayName();
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getDailySummary(this.selectedDate() || undefined).subscribe({
      next: (summary) => this.summary.set(summary),
      error: (error) => {
        this.summary.set(null);
        this.error.set(error?.error?.message || 'Unable to load the daily summary.');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  selectDate(date: string): void {
    this.selectedDate.set(date);
    this.load();
  }

  toggleProfileMenu(): void {
    this.profileMenuOpen.update((open) => !open);
  }

  openSettings(): void {
    this.profileMenuOpen.set(false);
    this.navigateTo('/courier/profile');
  }

  logOutFromMenu(): void {
    this.profileMenuOpen.set(false);
    this.signOut();
  }

  navigateTo(path: string): void {
    if (this.router) {
      this.router.navigate([path]);
    } else if (typeof window !== 'undefined') {
      window.location.href = path;
    }
  }

  signOut(): void {
    if (this.authService) {
      this.authService.logout().subscribe({
        next: () => this.navigateTo('/login'),
        error: () => this.navigateTo('/login'),
      });
    } else {
      this.navigateTo('/login');
    }
  }
}
