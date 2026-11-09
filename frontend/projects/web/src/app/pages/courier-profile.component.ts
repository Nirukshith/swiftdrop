import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { CourierProfile } from './courier/courier.model';
import { CourierService } from './courier/courier.service';

@Component({
  selector: 'app-courier-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="mx-auto max-w-3xl space-y-6">
      <div><p class="text-sm font-semibold text-indigo-600">Courier account</p><h1 class="text-3xl font-extrabold text-slate-900">Profile and availability</h1><p class="mt-1 text-sm text-slate-500">Keep the dispatch directory up to date.</p></div>
      <p *ngIf="loading()" class="rounded-2xl border border-slate-200 bg-white p-6 text-slate-600">Loading courier profile...</p>
      <div *ngIf="error()" class="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-800"><p>{{ error() }}</p><button type="button" class="mt-3 rounded-lg bg-rose-700 px-3 py-2 text-sm font-bold text-white" (click)="load()">Retry</button></div>
      <div *ngIf="!loading() && !error() && !profile()" class="rounded-2xl border border-dashed border-slate-300 p-8 text-center text-slate-600">Profile unavailable for this courier account.</div>
      <div *ngIf="profile() as courier" class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4"><div><h2 class="text-xl font-bold text-slate-900">{{ courier.name }}</h2><p class="text-sm text-slate-500">{{ courier.userId }}</p></div><span class="rounded-full px-3 py-1 text-xs font-bold" [class.bg-emerald-100]="courier.availability" [class.text-emerald-700]="courier.availability" [class.bg-slate-100]="!courier.availability" [class.text-slate-600]="!courier.availability">{{ courier.availability ? 'Available' : 'Unavailable' }}</span></div>
        <dl class="mt-6 grid gap-4 sm:grid-cols-2"><div><dt class="text-xs font-bold uppercase text-slate-400">Phone</dt><dd class="mt-1 text-slate-700">{{ courier.phone || 'Not provided' }}</dd></div><div><dt class="text-xs font-bold uppercase text-slate-400">Zone</dt><dd class="mt-1 text-slate-700">{{ courier.zone || 'Not assigned' }}</dd></div><div><dt class="text-xs font-bold uppercase text-slate-400">Vehicle</dt><dd class="mt-1 text-slate-700">{{ courier.vehicle || 'Not provided' }}</dd></div></dl>
        <p *ngIf="availabilityError()" class="mt-4 text-sm text-rose-700">{{ availabilityError() }}</p>
        <button type="button" class="mt-6 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white disabled:opacity-50" [disabled]="availabilityPending()" (click)="toggleAvailability()">{{ availabilityPending() ? 'Saving...' : ('Set ' + (courier.availability ? 'unavailable' : 'available')) }}</button>
      </div>
    </section>
  `,
})
export class CourierProfileComponent {
  private readonly service = inject(CourierService);
  readonly profile = signal<CourierProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly availabilityPending = signal(false);
  readonly availabilityError = signal<string | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true); this.error.set(null);
    this.service.getProfile().subscribe({
      next: (profile) => this.profile.set(profile),
      error: (error) => { this.profile.set(null); this.error.set(this.messageFor(error, 'Unable to load the courier profile.')); this.loading.set(false); },
      complete: () => this.loading.set(false),
    });
  }

  toggleAvailability(): void {
    const profile = this.profile();
    if (!profile || this.availabilityPending()) return;
    this.availabilityPending.set(true); this.availabilityError.set(null);
    this.service.setAvailability(!profile.availability).subscribe({
      next: (updated) => this.profile.set(updated),
      error: (error) => { this.availabilityError.set(this.messageFor(error, 'Unable to update availability.')); this.availabilityPending.set(false); },
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
