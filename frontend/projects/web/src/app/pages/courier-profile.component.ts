import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'shared-auth';
import { CourierProfile } from './courier/courier.model';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-profile',
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

          <!-- 2. Profile & Availability Icon (Active) -->
          <a
            href="/courier/profile"
            (click)="$event.preventDefault(); navigateTo('/courier/profile')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#a53a26] bg-white shadow-2xs transition-all cursor-pointer no-underline"
            title="Profile & Availability"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            <span class="hidden md:block absolute -right-[1px] top-2 bottom-2 w-1 bg-[#a53a26] rounded-l-full"></span>
          </a>

          <!-- 3. Daily Summary Icon -->
          <a
            href="/courier/summary"
            (click)="$event.preventDefault(); navigateTo('/courier/summary')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Daily Summary"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
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

          <!-- User Profile & Sign Out -->
          <div class="flex items-center gap-3 self-end sm:self-auto">
            <div class="flex items-center gap-2.5 px-3 py-1.5 rounded-full bg-[#faf8f6] border border-[#e8e2dc]">
              <div
                class="w-8 h-8 rounded-full bg-[#a53a26] text-white flex items-center justify-center font-bold text-xs uppercase shadow-sm"
              >
                {{ courierInitials() }}
              </div>
              <div class="flex items-center gap-1 cursor-pointer" (click)="signOut()" title="Click to Sign Out">
                <span class="text-xs font-bold text-[#1d1b2c]">{{ courierDisplayName() }}</span>
                <svg class="w-3.5 h-3.5 text-[#6f6d85]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </div>
            </div>
          </div>
        </div>

        <!-- Page Content Section -->
        <div class="max-w-4xl space-y-6">
          <div class="bg-white rounded-3xl p-6 sm:p-7 border border-[#e8e2dc] shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div class="flex items-center gap-4">
              <div class="w-12 h-12 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 flex-shrink-0">
                <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
              </div>
              <div>
                <span class="portal-pill">Courier Account</span>
                <h1 class="portal-title text-2xl font-black text-[#1d1b2c] tracking-tight mt-0.5">
                  Profile and Availability
                </h1>
                <p class="text-xs font-semibold text-[#6f6d85] mt-0.5">Keep the dispatch directory up to date.</p>
              </div>
            </div>
            <a
              href="/courier"
              (click)="$event.preventDefault(); navigateTo('/courier')"
              class="inline-flex items-center gap-1.5 px-4 py-2 rounded-full border border-[#e8e2dc] hover:border-[#a53a26] text-xs font-bold text-[#6f6d85] hover:text-[#a53a26] transition-colors cursor-pointer self-start sm:self-auto"
            >
              ← Back to Run Sheet
            </a>
          </div>

          <p *ngIf="loading()" class="rounded-3xl border border-[#e8e2dc] bg-white p-8 text-[#6f6d85] font-semibold text-center shadow-sm">
            Loading courier profile...
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

          <div *ngIf="!loading() && !error() && !profile()" class="rounded-3xl border border-dashed border-[#e8e2dc] bg-white p-10 text-center text-[#6f6d85] font-medium shadow-sm">
            Profile unavailable for this courier account.
          </div>

          <div *ngIf="profile() as courier" class="rounded-3xl border border-[#eee7e0] bg-white p-7 sm:p-8 shadow-sm space-y-6">
            <div class="flex items-start justify-between gap-4 border-b border-[#f4efe9] pb-5">
              <div class="flex items-center gap-3.5">
                <div class="w-12 h-12 rounded-2xl bg-[#faf6f0] text-[#a53a26] flex items-center justify-center text-xl font-black">
                  👤
                </div>
                <div>
                  <h2 class="text-xl font-black text-[#1d1b2c]">{{ courier.name }}</h2>
                  <p class="text-xs font-mono text-[#6f6d85] mt-0.5">{{ courier.userId }}</p>
                </div>
              </div>
              <span
                class="rounded-full px-3.5 py-1 text-xs font-extrabold tracking-wider uppercase border"
                [class.bg-[#ecfdf5]]="courier.availability"
                [class.text-emerald-800]="courier.availability"
                [class.border-[#d1fae5]]="courier.availability"
                [class.bg-[#f1f5f9]]="!courier.availability"
                [class.text-[#6f6d85]]="!courier.availability"
                [class.border-[#e2e8f0]]="!courier.availability"
              >
                ● {{ courier.availability ? 'Available' : 'Unavailable' }}
              </span>
            </div>

            <dl class="grid gap-4 sm:grid-cols-3">
              <div class="p-4 rounded-2xl bg-[#faf8f6] border border-[#e8e2dc]">
                <dt class="text-[10px] font-bold uppercase tracking-wider text-[#a09eaf]">Phone</dt>
                <dd class="mt-1 font-bold text-sm text-[#1d1b2c]">{{ courier.phone || 'Not provided' }}</dd>
              </div>
              <div class="p-4 rounded-2xl bg-[#faf8f6] border border-[#e8e2dc]">
                <dt class="text-[10px] font-bold uppercase tracking-wider text-[#a09eaf]">Zone</dt>
                <dd class="mt-1 font-bold text-sm text-[#1d1b2c]">{{ courier.zone || 'Not assigned' }}</dd>
              </div>
              <div class="p-4 rounded-2xl bg-[#faf8f6] border border-[#e8e2dc]">
                <dt class="text-[10px] font-bold uppercase tracking-wider text-[#a09eaf]">Vehicle</dt>
                <dd class="mt-1 font-bold text-sm text-[#1d1b2c]">{{ courier.vehicle || 'Not provided' }}</dd>
              </div>
            </dl>

            <p *ngIf="availabilityError()" class="text-xs text-rose-700 font-bold">{{ availabilityError() }}</p>

            <button
              type="button"
              class="action-pill-btn rounded-full bg-[#a53a26] hover:bg-[#7e2615] px-6 py-2.5 text-xs sm:text-sm font-bold text-white shadow-md shadow-[#a53a26]/20 disabled:opacity-40 transition-all cursor-pointer"
              [disabled]="availabilityPending()"
              (click)="toggleAvailability()"
            >
              {{ availabilityPending() ? 'Saving...' : ('Set ' + (courier.availability ? 'unavailable' : 'available')) }}
            </button>
          </div>
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
      .action-pill-btn {
        transition: all 0.2s ease;
      }
      .action-pill-btn:hover:not(:disabled) {
        transform: translateY(-1px);
      }
    `,
  ],
})
export class CourierProfileComponent {
  private readonly service = inject(CourierService);
  private readonly router = inject(Router, { optional: true });
  private readonly authService = inject(AuthService, { optional: true });

  readonly profile = signal<CourierProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly availabilityPending = signal(false);
  readonly availabilityError = signal<string | null>(null);

  readonly courierDisplayName = computed(() => this.profile()?.name || 'Sample Courier');
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
    this.service.getProfile().subscribe({
      next: (profile) => this.profile.set(profile),
      error: (error) => {
        this.profile.set(null);
        this.error.set(this.messageFor(error, 'Unable to load the courier profile.'));
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  toggleAvailability(): void {
    const profile = this.profile();
    if (!profile || this.availabilityPending()) return;
    this.availabilityPending.set(true);
    this.availabilityError.set(null);
    this.service.setAvailability(!profile.availability).subscribe({
      next: (updated) => this.profile.set(updated),
      error: (error) => {
        this.availabilityError.set(this.messageFor(error, 'Unable to update availability.'));
        this.availabilityPending.set(false);
      },
      complete: () => this.availabilityPending.set(false),
    });
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

  private messageFor(error: { status?: number; error?: { message?: string } }, fallback: string): string {
    if (error?.status === 401) return 'Your session has expired. Please sign in again.';
    if (error?.status === 403) return 'Only authenticated couriers can access this profile.';
    if (error?.status === 404) return 'No courier profile is available for this account.';
    return error?.error?.message || fallback;
  }
}
