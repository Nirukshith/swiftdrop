import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { CourierDailySummary } from './courier/courier.model';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="mx-auto max-w-6xl space-y-6 pb-16">
      <div
        class="bg-white rounded-3xl p-6 sm:p-7 border border-[#e8e2dc] shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4"
      >
        <div>
          <span class="portal-pill">Daily Summary</span>
          <h1 class="portal-title text-2xl sm:text-3xl font-black text-[#1d1b2c] tracking-tight mt-1">
            Daily Delivery Summary
          </h1>
          <p class="text-xs font-semibold text-[#6f6d85] mt-1">{{ summary()?.date || 'Sri Lanka business date' }}</p>
        </div>
        <label class="flex items-center gap-2 text-xs font-bold text-[#6f6d85]">
          View date
          <input
            type="date"
            [value]="selectedDate()"
            (change)="selectDate($any($event.target).value)"
            class="rounded-full border border-[#e8e2dc] bg-[#faf8f6] px-4 py-2 text-xs font-bold text-[#1d1b2c] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] transition-all"
          />
        </label>
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
    `,
  ],
})
export class CourierSummaryComponent {
  private readonly service = inject(CourierService);
  readonly summary = signal<CourierDailySummary | null>(null);
  readonly selectedDate = signal('');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

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
}
