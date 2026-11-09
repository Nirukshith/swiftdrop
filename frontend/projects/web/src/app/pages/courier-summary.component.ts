import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { CourierDailySummary } from './courier/courier.model';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="mx-auto max-w-6xl space-y-6">
      <div><p class="text-sm font-semibold text-indigo-600">{{ summary()?.date || 'Sri Lanka business date' }}</p><h1 class="text-3xl font-extrabold text-slate-900">Daily delivery summary</h1><label class="mt-3 block text-sm text-slate-600">View date <input type="date" [value]="selectedDate()" (change)="selectDate($any($event.target).value)" class="ml-2 rounded-lg border border-slate-300 px-3 py-2" /></label></div>
      <p *ngIf="loading()" class="rounded-2xl border border-slate-200 bg-white p-6 text-slate-600">Loading daily summary...</p>
      <div *ngIf="error()" class="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-800"><p>{{ error() }}</p><button type="button" class="mt-3 rounded-lg bg-rose-700 px-3 py-2 text-sm font-bold text-white" (click)="load()">Retry</button></div>
      <div *ngIf="!loading() && !error() && !summary()" class="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-slate-600">Summary unavailable.</div>
      <ng-container *ngIf="summary() as daily">
        <div class="grid gap-4 sm:grid-cols-3"><div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><p class="text-xs font-bold uppercase text-slate-400">Total deliveries</p><p class="mt-2 text-3xl font-extrabold text-slate-900">{{ daily.totalDeliveries }}</p></div><div class="rounded-2xl border border-amber-200 bg-amber-50 p-5"><p class="text-xs font-bold uppercase text-amber-700">Pending</p><p class="mt-2 text-3xl font-extrabold text-amber-800">{{ daily.pendingCount }}</p></div><div class="rounded-2xl border border-emerald-200 bg-emerald-50 p-5"><p class="text-xs font-bold uppercase text-emerald-700">Completed</p><p class="mt-2 text-3xl font-extrabold text-emerald-800">{{ daily.completedCount }}</p></div></div>
        <div *ngIf="daily.deliveries.length === 0" class="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-slate-600">No deliveries for this business date.</div>
        <div *ngIf="daily.deliveries.length > 0" class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"><div class="border-b border-slate-100 px-5 py-4"><h2 class="font-bold text-slate-900">Delivery cards</h2></div><div *ngFor="let delivery of daily.deliveries" class="grid gap-2 border-b border-slate-100 px-5 py-4 last:border-0 sm:grid-cols-5 sm:items-center"><div><p class="font-mono text-sm font-bold text-slate-800">{{ delivery.trackingNumber }}</p><p class="text-xs text-slate-500">{{ delivery.recipientName }}</p></div><p class="text-sm text-slate-600 sm:col-span-2">{{ delivery.dropoffAddress }}</p><span class="w-fit rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{{ delivery.status }}</span><p class="text-xs text-slate-500">Attempt {{ delivery.attemptCount }} · #{{ delivery.sequenceOrder }}</p></div></div>
      </ng-container>
    </section>
  `,
})
export class CourierSummaryComponent {
  private readonly service = inject(CourierService);
  readonly summary = signal<CourierDailySummary | null>(null);
  readonly selectedDate = signal('');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true); this.error.set(null);
    this.service.getDailySummary(this.selectedDate() || undefined).subscribe({
      next: (summary) => this.summary.set(summary),
      error: (error) => { this.summary.set(null); this.error.set(error?.error?.message || 'Unable to load the daily summary.'); this.loading.set(false); },
      complete: () => this.loading.set(false),
    });
  }

  selectDate(date: string): void { this.selectedDate.set(date); this.load(); }
}
