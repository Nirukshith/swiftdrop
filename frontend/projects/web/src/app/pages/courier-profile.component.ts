import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="mx-auto max-w-3xl space-y-6">
      <div>
        <p class="text-sm font-semibold text-indigo-600">Courier account</p>
        <h1 class="text-3xl font-extrabold text-slate-900">Profile and availability</h1>
        <p class="mt-1 text-sm text-slate-500">Keep the dispatch directory up to date.</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="text-xl font-bold text-slate-900">{{ service.profile().name }}</h2>
            <p class="text-sm text-slate-500">{{ service.profile().userId }}</p>
          </div>
          <span class="rounded-full px-3 py-1 text-xs font-bold"
            [class.bg-emerald-100]="service.profile().availability"
            [class.text-emerald-700]="service.profile().availability"
            [class.bg-slate-100]="!service.profile().availability"
            [class.text-slate-600]="!service.profile().availability">
            {{ service.profile().availability ? 'Available' : 'Unavailable' }}
          </span>
        </div>
        <dl class="mt-6 grid gap-4 sm:grid-cols-2">
          <div><dt class="text-xs font-bold uppercase text-slate-400">Phone</dt><dd class="mt-1 text-slate-700">{{ service.profile().phone || 'Not provided' }}</dd></div>
          <div><dt class="text-xs font-bold uppercase text-slate-400">Zone</dt><dd class="mt-1 text-slate-700">{{ service.profile().zone || 'Not assigned' }}</dd></div>
          <div><dt class="text-xs font-bold uppercase text-slate-400">Vehicle</dt><dd class="mt-1 text-slate-700">{{ service.profile().vehicle || 'Not provided' }}</dd></div>
        </dl>
        <button type="button" class="mt-6 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white hover:bg-indigo-700"
          (click)="service.toggleAvailability()">
          Set {{ service.profile().availability ? 'unavailable' : 'available' }}
        </button>
      </div>
    </section>
  `,
})
export class CourierProfileComponent {
  readonly service = inject(CourierService);
}
