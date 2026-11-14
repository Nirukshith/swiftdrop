import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { CourierProfile } from './courier/courier.model';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="mx-auto max-w-3xl space-y-6 pb-16">
      <div class="bg-white rounded-3xl p-6 sm:p-7 border border-[#e8e2dc] shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <span class="portal-pill">Courier Account</span>
          <h1 class="portal-title text-2xl sm:text-3xl font-black text-[#1d1b2c] tracking-tight mt-1">
            Profile and Availability
          </h1>
          <p class="text-xs font-semibold text-[#6f6d85] mt-1">Keep the dispatch directory up to date.</p>
        </div>
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
    </section>
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
  readonly profile = signal<CourierProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly availabilityPending = signal(false);
  readonly availabilityError = signal<string | null>(null);

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

  private messageFor(error: { status?: number; error?: { message?: string } }, fallback: string): string {
    if (error?.status === 401) return 'Your session has expired. Please sign in again.';
    if (error?.status === 403) return 'Only authenticated couriers can access this profile.';
    if (error?.status === 404) return 'No courier profile is available for this account.';
    return error?.error?.message || fallback;
  }
}
