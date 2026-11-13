import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonComponent, CardComponent } from 'ui';
import { DispatchService } from './dispatch.service';
import { Zone } from './dispatch.model';

@Component({
  selector: 'app-dispatch-zones',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardComponent, ButtonComponent],
  template: `
    <div class="max-w-7xl mx-auto space-y-6 pb-16 px-4 sm:px-6">
      <!-- Portal Top Header -->
      <div
        class="bg-white/95 backdrop-blur-md rounded-2xl shadow-sm border border-slate-200/90 p-5 sm:p-6 flex flex-col md:flex-row md:items-center justify-between gap-4"
      >
        <div class="flex items-center gap-3.5">
          <div
            class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-indigo-600 to-violet-600 flex items-center justify-center text-white shadow-md shadow-indigo-200 text-xl font-black"
          >
            🗺️
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight">
                Delivery Zones Configuration
              </h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200"
              >
                Ops Hub • Port 8083
              </span>
            </div>
            <p class="text-xs sm:text-sm text-slate-500 font-medium">
              Configure coverage boundaries, served delivery areas, and base fees
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2.5">
          <div class="px-4 py-2 rounded-xl bg-indigo-50/80 border border-indigo-200/80 text-center">
            <p class="text-[11px] uppercase font-bold text-indigo-600 tracking-wider">Configured Zones</p>
            <p class="text-lg font-black text-indigo-800">{{ zones().length }}</p>
          </div>
        </div>
      </div>

      <!-- Dispatcher Navigation Tabs -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="inline-flex rounded-xl bg-slate-100 p-1.5 border border-slate-200 text-sm font-semibold">
          <a
            routerLink="/dispatch"
            id="tab-queue-link"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2 text-slate-600 hover:text-indigo-600"
          >
            <span>📋</span>
            <span>Unassigned Queue</span>
          </a>

          <a
            routerLink="/dispatch"
            [queryParams]="{ tab: 'workload' }"
            id="tab-workload-link"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2 text-slate-600 hover:text-indigo-600"
          >
            <span>📊</span>
            <span>Workload Board</span>
          </a>

          <a
            routerLink="/dispatch/zones"
            id="tab-zones-btn"
            class="px-4 py-2 rounded-lg bg-white shadow-sm text-indigo-600 transition-all flex items-center gap-2"
          >
            <span>🗺️</span>
            <span>Delivery Zones</span>
          </a>

          <a
            routerLink="/dispatch/pricing"
            id="tab-pricing-link"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2 text-slate-600 hover:text-indigo-600"
          >
            <span>🏷️</span>
            <span>Pricing Rules</span>
          </a>
        </div>
      </div>

      <!-- Main Zone Grid -->
      <div class="grid gap-6 xl:grid-cols-[1.2fr_1fr]">
        <ui-card title="Delivery zones" id="zones">
          <div class="space-y-3">
            @if (dispatchService.isLoadingZones()) {
              <p class="text-sm text-slate-500">Loading zones…</p>
            } @else if (dispatchService.zonesError()) {
              <p class="text-sm text-rose-600">{{ dispatchService.zonesError() }}</p>
            } @else if (zones().length === 0) {
              <p class="text-sm text-slate-500">No delivery zones configured.</p>
            }
            @for (zone of zones(); track zone.name) {
              <div class="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-slate-50 p-4 border border-slate-200/60">
                <div>
                  <p class="font-semibold text-slate-800">{{ zone.name }}</p>
                  <p class="text-sm text-slate-500">{{ zone.districts.join(', ') }}</p>
                </div>
                <div class="text-right">
                  <p class="font-medium text-slate-800">LKR {{ zone.baseFeeLkr | number: '1.2-2' }}</p>
                  <span class="text-xs font-semibold" [class]="zone.served ? 'text-emerald-600' : 'text-slate-500'">
                    {{ zone.served ? '● Served' : '○ Paused' }}
                  </span>
                  <div class="mt-2 flex gap-2">
                    <button type="button" class="text-xs text-indigo-600" (click)="editZone(zone)">Edit</button>
                    <button type="button" class="text-xs text-rose-600" (click)="removeZone(zone)">Delete</button>
                  </div>
                </div>
              </div>
            }
          </div>
        </ui-card>

        <ui-card title="Add zone">
          <form class="space-y-4" [formGroup]="zoneForm" (ngSubmit)="saveZone()">
            @if (mutationError()) {
              <p class="text-sm text-rose-600">{{ mutationError() }}</p>
            }
            <div>
              <label class="block text-sm font-medium text-slate-700">
                Zone name
                <input
                  id="zone-name-input"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  formControlName="name"
                  placeholder="e.g. KANDY_CENTRAL"
                />
              </label>
              @if (zoneForm.get('name')?.invalid && zoneForm.get('name')?.touched) {
                <p id="zone-name-error" class="mt-1 text-xs text-rose-600 font-medium">Zone name is required</p>
              }
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Districts
                <select
                  id="zone-districts-select"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                  multiple
                  formControlName="districts"
                >
                  @for (district of districts; track district) {
                    <option [value]="district">{{ district }}</option>
                  }
                </select>
              </label>
              @if (zoneForm.get('districts')?.invalid && zoneForm.get('districts')?.touched) {
                <p id="zone-districts-error" class="mt-1 text-xs text-rose-600 font-medium">At least one district must be selected</p>
              }
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Base fee (LKR)
                <input
                  id="zone-fee-input"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  type="number"
                  formControlName="baseFeeLkr"
                  placeholder="500"
                />
              </label>
              @if (zoneForm.get('baseFeeLkr')?.invalid && zoneForm.get('baseFeeLkr')?.touched) {
                <p id="zone-fee-error" class="mt-1 text-xs text-rose-600 font-medium">Base fee must be 0 or greater</p>
              }
            </div>

            <label class="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
              <input
                id="zone-served-checkbox"
                type="checkbox"
                formControlName="served"
                class="rounded text-indigo-600 focus:ring-indigo-500"
              />
              <span>Active served zone</span>
            </label>

            <ui-button
              id="add-zone-submit-btn"
              type="submit"
              [disabled]="zoneForm.invalid"
            >
              {{ editingZoneId ? 'Save changes' : 'Add zone' }}
            </ui-button>
          </form>
        </ui-card>
      </div>
    </div>
  `,
})
export class DispatchZonesComponent {
  readonly dispatchService = inject(DispatchService);
  readonly mutationError = signal<string | null>(null);
  editingZoneId: string | null = null;
  readonly districts = [
    'Ampara',
    'Anuradhapura',
    'Badulla',
    'Batticaloa',
    'Colombo',
    'Galle',
    'Gampaha',
    'Hambantota',
    'Jaffna',
    'Kalutara',
    'Kandy',
    'Kegalle',
    'Kilinochchi',
    'Kurunegala',
    'Mannar',
    'Matale',
    'Matara',
    'Monaragala',
    'Mullaitivu',
    'Nuwara Eliya',
    'Polonnaruwa',
    'Puttalam',
    'Ratnapura',
    'Trincomalee',
    'Vavuniya',
  ];

  readonly zones = signal<Zone[]>([]);

  readonly zoneForm: FormGroup;

  constructor(private readonly formBuilder: FormBuilder) {
    this.zoneForm = this.formBuilder.nonNullable.group({
      name: ['', [Validators.required]],
      districts: [<string[]>[], [Validators.required, Validators.minLength(1)]],
      served: [true],
      baseFeeLkr: [0, [Validators.required, Validators.min(0)]],
    });
    this.loadZones();
  }

  private loadZones(): void {
    this.dispatchService.listZones().subscribe({
      next: (zones) => this.zones.set(zones),
      error: () => undefined,
    });
  }

  saveZone(): void {
    if (this.zoneForm.invalid) {
      this.zoneForm.markAllAsTouched();
      return;
    }
    this.mutationError.set(null);
    const request = this.zoneForm.getRawValue();
    const operation = this.editingZoneId
      ? this.dispatchService.updateZone(this.editingZoneId, request)
      : this.dispatchService.createZone(request);
    operation.subscribe({
      next: () => {
        this.editingZoneId = null;
        this.zoneForm.reset({ name: '', districts: [], served: true, baseFeeLkr: 0 });
        this.loadZones();
      },
      error: (error) => this.mutationError.set(
        error?.error?.message || error?.message || 'Unable to save the delivery zone.'
      ),
    });
  }

  editZone(zone: Zone): void {
    this.editingZoneId = zone.id;
    this.zoneForm.setValue({
      name: zone.name,
      districts: zone.districts,
      served: zone.served,
      baseFeeLkr: zone.baseFeeLkr,
    });
  }

  removeZone(zone: Zone): void {
    if (!window.confirm(`Delete zone ${zone.name}?`)) {
      return;
    }
    this.dispatchService.deleteZone(zone.id).subscribe({
      next: () => this.loadZones(),
      error: (error) => this.mutationError.set(
        error?.error?.message || error?.message || 'Unable to delete the delivery zone.'
      ),
    });
  }
}
