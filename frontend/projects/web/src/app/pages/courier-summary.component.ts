import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="mx-auto max-w-6xl space-y-6">
      <div>
        <p class="text-sm font-semibold text-indigo-600">{{ summary.date }}</p>
        <h1 class="text-3xl font-extrabold text-slate-900">Daily delivery summary</h1>
      </div>
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><p class="text-xs font-bold uppercase text-slate-400">Total deliveries</p><p class="mt-2 text-3xl font-extrabold text-slate-900">{{ summary.totalDeliveries }}</p></div>
        <div class="rounded-2xl border border-amber-200 bg-amber-50 p-5"><p class="text-xs font-bold uppercase text-amber-700">Pending</p><p class="mt-2 text-3xl font-extrabold text-amber-800">{{ summary.pendingCount }}</p></div>
        <div class="rounded-2xl border border-emerald-200 bg-emerald-50 p-5"><p class="text-xs font-bold uppercase text-emerald-700">Completed</p><p class="mt-2 text-3xl font-extrabold text-emerald-800">{{ summary.completedCount }}</p></div>
      </div>
      <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div class="border-b border-slate-100 px-5 py-4"><h2 class="font-bold text-slate-900">Delivery cards</h2></div>
        <div *ngFor="let delivery of summary.deliveries" class="grid gap-2 border-b border-slate-100 px-5 py-4 last:border-0 sm:grid-cols-5 sm:items-center">
          <div><p class="font-mono text-sm font-bold text-slate-800">{{ delivery.trackingNumber }}</p><p class="text-xs text-slate-500">{{ delivery.recipientName }}</p></div>
          <p class="text-sm text-slate-600 sm:col-span-2">{{ delivery.dropoffAddress }}</p>
          <span class="w-fit rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{{ delivery.status }}</span>
          <p class="text-xs text-slate-500">Attempt {{ delivery.attemptCount }} · #{{ delivery.sequenceOrder }}</p>
        </div>
      </div>
    </section>
  `,
})
export class CourierSummaryComponent {
  private readonly service = inject(CourierService);
  readonly summary = this.service.getDailySummary();
}
