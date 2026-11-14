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
            title="Profile & Settings"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <circle cx="12" cy="12" r="3"></circle>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
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
        <!-- Top Header: Search Bar & User Profile Dropdown (Matching Customer Portal) -->
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

        <!-- Premium Settings Panel (Identical Design to Customer Portal) -->
        <div id="settings-panel" class="space-y-6 max-w-3xl">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="text-xl font-black text-[#1d1b2c] tracking-tight">Settings</h2>
              <p class="text-xs text-[#6f6d85] font-medium mt-0.5">Your account details and preferences.</p>
            </div>
            <a
              href="/courier"
              (click)="$event.preventDefault(); navigateTo('/courier')"
              class="inline-flex items-center gap-1.5 px-4 py-2 rounded-full border border-[#e8e2dc] hover:border-[#a53a26] text-xs font-bold text-[#6f6d85] hover:text-[#a53a26] transition-colors cursor-pointer"
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

          <ng-container *ngIf="profile() as courier">
            <!-- 1. Account Card (Warm Sand #faf8f6 background, rounded-3xl, matching customer) -->
            <section class="rounded-3xl border border-[#eee7e0] bg-[#faf8f6] p-6 space-y-4">
              <h3 class="text-sm font-black text-[#1d1b2c]">Account</h3>
              <div class="flex items-center gap-4">
                <span class="w-12 h-12 flex-shrink-0 rounded-full bg-[#a53a26] text-white flex items-center justify-center font-bold text-sm uppercase shadow-sm">
                  {{ courierInitials() }}
                </span>
                <dl class="grid grid-cols-1 sm:grid-cols-3 gap-4 flex-1 min-w-0 text-xs">
                  <div>
                    <dt class="text-[#6f6d85] font-medium">Full name</dt>
                    <dd id="settings-name" class="font-bold text-[#1d1b2c] mt-0.5">{{ courier.name }}</dd>
                  </div>
                  <div class="min-w-0">
                    <dt class="text-[#6f6d85] font-medium">Email</dt>
                    <dd id="settings-email" class="font-bold text-[#1d1b2c] mt-0.5 truncate">{{ courierEmail() }}</dd>
                  </div>
                  <div>
                    <dt class="text-[#6f6d85] font-medium">Account type</dt>
                    <dd class="font-bold text-[#1d1b2c] mt-0.5">Courier</dd>
                  </div>
                </dl>
              </div>
            </section>

            <!-- 2. Duty & Availability Section -->
            <section class="rounded-3xl border border-[#eee7e0] p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <div class="flex items-center gap-2">
                  <h3 class="text-sm font-black text-[#1d1b2c]">Duty & Availability</h3>
                  <span
                    class="rounded-full px-3 py-0.5 text-xs font-extrabold uppercase tracking-wider border"
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
                <p class="text-xs text-[#6f6d85] font-medium mt-1">
                  {{ courier.availability ? 'You are active on duty and receiving assigned deliveries.' : 'You are currently off duty and marked unavailable for new dispatches.' }}
                </p>
                <p *ngIf="availabilityError()" class="text-xs text-rose-700 font-bold mt-1">{{ availabilityError() }}</p>
              </div>
              <button
                type="button"
                id="settings-availability-btn"
                class="action-pill-btn px-6 py-2.5 rounded-full text-xs font-bold text-white transition-all cursor-pointer whitespace-nowrap shadow-sm disabled:opacity-40"
                [class.bg-[#a53a26]]="courier.availability"
                [class.hover:bg-[#7e2615]]="courier.availability"
                [class.bg-emerald-600]="!courier.availability"
                [class.hover:bg-emerald-700]="!courier.availability"
                [disabled]="availabilityPending()"
                (click)="toggleAvailability()"
              >
                {{ availabilityPending() ? 'Saving...' : ('Set ' + (courier.availability ? 'unavailable' : 'available')) }}
              </button>
            </section>

            <!-- 3. Courier Details Section (Phone, Zone, Vehicle) -->
            <section class="rounded-3xl border border-[#eee7e0] bg-[#faf8f6] p-6 space-y-4">
              <h3 class="text-sm font-black text-[#1d1b2c]">Courier Details</h3>
              <dl class="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                <div>
                  <dt class="text-[#6f6d85] font-medium">Phone number</dt>
                  <dd class="font-bold text-[#1d1b2c] mt-0.5">{{ courier.phone || 'Not provided' }}</dd>
                </div>
                <div>
                  <dt class="text-[#6f6d85] font-medium">Assigned Zone</dt>
                  <dd class="font-bold text-[#1d1b2c] mt-0.5">{{ courier.zone || 'Colombo' }}</dd>
                </div>
                <div>
                  <dt class="text-[#6f6d85] font-medium">Vehicle</dt>
                  <dd class="font-bold text-[#1d1b2c] mt-0.5">{{ courier.vehicle || 'MOTORBIKE' }}</dd>
                </div>
              </dl>
            </section>

            <!-- 4. Password Section -->
            <section class="rounded-3xl border border-[#eee7e0] p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h3 class="text-sm font-black text-[#1d1b2c]">Password</h3>
                <p class="text-xs text-[#6f6d85] font-medium mt-0.5">We will email you a secure link to set a new password.</p>
              </div>
              <a
                href="/forgot-password"
                (click)="$event.preventDefault(); navigateTo('/forgot-password')"
                id="settings-password-link"
                class="px-5 py-2.5 bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#1d1b2c] text-xs font-bold rounded-full transition-all no-underline whitespace-nowrap text-center cursor-pointer"
              >
                Change password
              </a>
            </section>

            <!-- 5. Sign Out Section -->
            <section class="rounded-3xl border border-[#eee7e0] p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h3 class="text-sm font-black text-[#1d1b2c]">Sign out</h3>
                <p class="text-xs text-[#6f6d85] font-medium mt-0.5">Log out of SwiftDrop on this device.</p>
              </div>
              <button
                type="button"
                id="settings-logout-btn"
                (click)="signOut()"
                class="px-5 py-2.5 bg-[#a53a26] hover:bg-[#7e2615] text-white text-xs font-bold rounded-full transition-all cursor-pointer whitespace-nowrap"
              >
                Log out
              </button>
            </section>
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
  readonly profileMenuOpen = signal<boolean>(false);

  readonly courierDisplayName = computed(() => {
    return this.authService?.currentUser()?.fullName || this.profile()?.name || 'Sample Courier';
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

  toggleProfileMenu(): void {
    this.profileMenuOpen.update((open) => !open);
  }

  openSettings(): void {
    this.profileMenuOpen.set(false);
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

  private messageFor(error: { status?: number; error?: { message?: string } }, fallback: string): string {
    if (error?.status === 401) return 'Your session has expired. Please sign in again.';
    if (error?.status === 403) return 'Only authenticated couriers can access this profile.';
    if (error?.status === 404) return 'No courier profile is available for this account.';
    return error?.error?.message || fallback;
  }
}
