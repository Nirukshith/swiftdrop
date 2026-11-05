import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonComponent, CardComponent } from 'ui';
import { DispatchService } from './dispatch.service';
import { PricingRule } from './dispatch.model';

@Component({
  selector: 'app-dispatch-pricing',
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
            🏷️
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight">
                Pricing Rules Configuration
              </h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200"
              >
                Ops Hub • Port 8083
              </span>
            </div>
            <p class="text-xs sm:text-sm text-slate-500 font-medium">
              Configure delivery pricing models, weight tiers, and parcel size rules
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2.5">
          <div class="px-4 py-2 rounded-xl bg-indigo-50/80 border border-indigo-200/80 text-center">
            <p class="text-[11px] uppercase font-bold text-indigo-600 tracking-wider">Active Rules</p>
            <p class="text-lg font-black text-indigo-800">{{ pricingRules().length }}</p>
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
            id="tab-zones-link"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2 text-slate-600 hover:text-indigo-600"
          >
            <span>🗺️</span>
            <span>Delivery Zones</span>
          </a>

          <a
            routerLink="/dispatch/pricing"
            id="tab-pricing-btn"
            class="px-4 py-2 rounded-lg bg-white shadow-sm text-indigo-600 transition-all flex items-center gap-2"
          >
            <span>🏷️</span>
            <span>Pricing Rules</span>
          </a>
        </div>
      </div>

      <!-- Main Pricing Grid -->
      <div id="pricing" class="grid gap-6 xl:grid-cols-[1.2fr_1fr]">
        <ui-card title="Pricing rules">
          <div class="space-y-3">
            @if (dispatchService.isLoadingPricingRules()) {
              <p class="text-sm text-slate-500">Loading pricing rules...</p>
            } @else if (dispatchService.pricingRulesError()) {
              <p class="text-sm text-rose-600">{{ dispatchService.pricingRulesError() }}</p>
            } @else if (pricingRules().length === 0) {
              <p class="text-sm text-slate-500">No pricing rules configured.</p>
            }
            @for (rule of pricingRules(); track rule.key) {
              <div class="rounded-lg bg-slate-50 p-4 border border-slate-200/60">
                <div class="flex items-center justify-between gap-3">
                  <p class="font-semibold text-slate-800">{{ rule.key }}</p>
                  <span class="text-sm font-medium text-slate-700">LKR {{ rule.chargeLkr | number: '1.2-2' }}</span>
                </div>
                <p class="mt-1 text-sm text-slate-500">
                  {{ rule.kind === 'SIZE' ? 'Size charge' : (rule.minimumWeightKg + '–' + rule.maximumWeightKg + ' kg weight band') }}
                </p>
                <p class="text-xs" [class]="rule.active ? 'text-emerald-600' : 'text-slate-500'">
                  {{ rule.active ? 'Active' : 'Inactive' }}
                </p>
                <div class="mt-2 flex gap-2">
                  <button type="button" class="text-xs text-indigo-600" (click)="editPricingRule(rule)">Edit</button>
                  <button type="button" class="text-xs text-rose-600" (click)="removePricingRule(rule)">Delete</button>
                </div>
              </div>
            }
          </div>
        </ui-card>

        <ui-card title="Add pricing rule">
          <form class="space-y-4" [formGroup]="pricingForm" (ngSubmit)="savePricingRule()">
            @if (mutationError()) {
              <p class="text-sm text-rose-600">{{ mutationError() }}</p>
            }
            <div>
              <label class="block text-sm font-medium text-slate-700">
                Rule type
                <select
                  id="pricing-kind-select"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                  formControlName="kind"
                >
                  <option value="SIZE">Size</option>
                  <option value="WEIGHT_BAND">Weight band</option>
                </select>
              </label>
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Key
                <input
                  id="pricing-key-input"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  formControlName="key"
                  placeholder="LARGE or UP_TO_5KG"
                />
              </label>
              @if (pricingForm.get('key')?.invalid && pricingForm.get('key')?.touched) {
                <p id="pricing-key-error" class="mt-1 text-xs text-rose-600 font-medium">Key identifier is required</p>
              }
            </div>

            @if (pricingForm.get('kind')?.value === 'WEIGHT_BAND') {
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-sm font-medium text-slate-700">
                    Minimum kg
                    <input
                      id="pricing-min-kg-input"
                      class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      type="number"
                      formControlName="minimumWeightKg"
                    />
                  </label>
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">
                    Maximum kg
                    <input
                      id="pricing-max-kg-input"
                      class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      type="number"
                      formControlName="maximumWeightKg"
                    />
                  </label>
                </div>
              </div>
            }

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Charge (LKR)
                <input
                  id="pricing-charge-input"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  type="number"
                  formControlName="chargeLkr"
                  placeholder="250"
                />
              </label>
              @if (pricingForm.get('chargeLkr')?.invalid && pricingForm.get('chargeLkr')?.touched) {
                <p id="pricing-charge-error" class="mt-1 text-xs text-rose-600 font-medium">Charge must be 0 or greater</p>
              }
            </div>

            <ui-button
              id="add-pricing-submit-btn"
              type="submit"
              [disabled]="pricingForm.invalid"
            >
              {{ editingRuleId ? 'Save changes' : 'Add pricing rule' }}
            </ui-button>
          </form>
        </ui-card>
      </div>
    </div>
  `,
})
export class DispatchPricingComponent {
  readonly dispatchService = inject(DispatchService);
  readonly mutationError = signal<string | null>(null);
  readonly pricingRules = signal<PricingRule[]>([]);
  editingRuleId: string | null = null;

  readonly pricingForm: FormGroup;

  constructor(private readonly formBuilder: FormBuilder) {
    this.pricingForm = this.formBuilder.nonNullable.group({
      kind: ['SIZE' as PricingRule['kind'], [Validators.required]],
      key: ['', [Validators.required]],
      minimumWeightKg: [0],
      maximumWeightKg: [0],
      chargeLkr: [0, [Validators.required, Validators.min(0)]],
      active: [true],
    });
    this.loadPricingRules();
  }
  private loadPricingRules(): void {
    this.dispatchService.listPricingRules().subscribe({
      next: (rules) => this.pricingRules.set(rules),
      error: () => undefined,
    });
  }

  savePricingRule(): void {
    if (this.pricingForm.invalid) {
      this.pricingForm.markAllAsTouched();
      return;
    }
    this.mutationError.set(null);
    const raw = this.pricingForm.getRawValue();
    const request = raw.kind === 'SIZE'
      ? { kind: raw.kind, key: raw.key, chargeLkr: raw.chargeLkr, active: raw.active }
      : raw;
    const operation = this.editingRuleId
      ? this.dispatchService.updatePricingRule(this.editingRuleId, request)
      : this.dispatchService.createPricingRule(request);
    operation.subscribe({
      next: () => {
        this.editingRuleId = null;
        this.pricingForm.reset({
          kind: 'SIZE', key: '', minimumWeightKg: 0, maximumWeightKg: 0, chargeLkr: 0, active: true,
        });
        this.loadPricingRules();
      },
      error: (error) => this.mutationError.set(
        error?.error?.message || error?.message || 'Unable to save the pricing rule.'
      ),
    });
  }

  editPricingRule(rule: PricingRule): void {
    this.editingRuleId = rule.id;
    this.pricingForm.setValue({
      kind: rule.kind,
      key: rule.key,
      minimumWeightKg: rule.minimumWeightKg ?? 0,
      maximumWeightKg: rule.maximumWeightKg ?? 0,
      chargeLkr: rule.chargeLkr,
      active: rule.active,
    });
  }

  removePricingRule(rule: PricingRule): void {
    if (!window.confirm(`Delete pricing rule ${rule.key}?`)) {
      return;
    }
    this.dispatchService.deletePricingRule(rule.id).subscribe({
      next: () => this.loadPricingRules(),
      error: (error) => this.mutationError.set(
        error?.error?.message || error?.message || 'Unable to delete the pricing rule.'
      ),
    });
  }
}
