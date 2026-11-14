import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonComponent, CardComponent } from 'ui';
import { DispatchService } from './dispatch.service';
import { CANONICAL_DISTRICTS, ParcelSize, PricingRule, QuoteResponse } from './dispatch.model';

const weightBandBoundsValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  if (control.get('kind')?.value !== 'WEIGHT_BAND') {
    return null;
  }

  const minimum = control.get('minimumWeightKg')?.value;
  const maximum = control.get('maximumWeightKg')?.value;
  if (!Number.isFinite(minimum) || !Number.isFinite(maximum)
    || minimum < 0 || maximum <= 0 || maximum <= minimum) {
    return { weightBandBounds: true };
  }

  return null;
};

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

      <!-- Success Feedback Banner -->
      @if (successMessage(); as msg) {
        <div
          id="pricing-success-banner"
          class="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800 flex items-center justify-between shadow-sm"
        >
          <div class="flex items-center gap-2">
            <span>✅</span>
            <span>{{ msg }}</span>
          </div>
          <button
            type="button"
            class="text-xs font-bold text-emerald-700 hover:text-emerald-900 cursor-pointer"
            (click)="successMessage.set(null)"
          >
            ✕
          </button>
        </div>
      }

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
              @if (pricingForm.hasError('weightBandBounds')
                && (pricingForm.get('minimumWeightKg')?.touched
                  || pricingForm.get('maximumWeightKg')?.touched)) {
                <p id="pricing-weight-bounds-error" class="text-xs text-rose-600 font-medium">
                  Minimum weight must be at least 0, maximum weight must be greater than 0,
                  and maximum weight must be greater than minimum weight
                </p>
              }
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
              [disabled]="pricingForm.invalid || isSaving()"
            >
              {{ isSaving() ? 'Saving...' : (editingRuleId ? 'Save changes' : 'Add pricing rule') }}
            </ui-button>
          </form>
        </ui-card>
      </div>

      <!-- Try a Quote Live Fee Calculator (M5 Requirement) -->
      <ui-card title="Try a Quote calculator" id="quote-calculator">
        <div class="space-y-6">
          <p class="text-sm text-slate-500">
            Calculate estimated parcel delivery fees in real time using the backend pricing engine (ADR 003 formula: Drop-off Zone Base Fee + Weight Band Charge + Size Charge).
          </p>

          <form [formGroup]="quoteForm" (ngSubmit)="calculateQuote()" class="grid gap-4 md:grid-cols-4 items-end">
            <div>
              <label class="block text-sm font-medium text-slate-700">
                Pickup District
                <select
                  id="quote-pickup-select"
                  formControlName="pickupDistrict"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  @for (district of canonicalDistricts; track district) {
                    <option [value]="district">{{ district }}</option>
                  }
                </select>
              </label>
              @if (quoteForm.get('pickupDistrict')?.invalid && quoteForm.get('pickupDistrict')?.touched) {
                <p class="mt-1 text-xs text-rose-600 font-medium">Pickup district is required</p>
              }
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Drop-off District
                <select
                  id="quote-dropoff-select"
                  formControlName="dropoffDistrict"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  @for (district of canonicalDistricts; track district) {
                    <option [value]="district">{{ district }}</option>
                  }
                </select>
              </label>
              @if (quoteForm.get('dropoffDistrict')?.invalid && quoteForm.get('dropoffDistrict')?.touched) {
                <p class="mt-1 text-xs text-rose-600 font-medium">Drop-off district is required</p>
              }
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Weight (kg)
                <input
                  id="quote-weight-input"
                  type="number"
                  step="any"
                  min="0.0001"
                  formControlName="weightKg"
                  placeholder="e.g. 2.5"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </label>
              @if (quoteForm.get('weightKg')?.invalid && quoteForm.get('weightKg')?.touched) {
                <p class="mt-1 text-xs text-rose-600 font-medium">Weight must be greater than 0</p>
              }
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">
                Size Category
                <select
                  id="quote-size-select"
                  formControlName="size"
                  class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  <option value="SMALL">SMALL</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LARGE">LARGE</option>
                </select>
              </label>
              @if (quoteForm.get('size')?.invalid && quoteForm.get('size')?.touched) {
                <p class="mt-1 text-xs text-rose-600 font-medium">Size is required</p>
              }
            </div>

            <div class="md:col-span-4 flex items-center gap-3">
              <ui-button
                id="quote-calculate-btn"
                type="submit"
                [disabled]="quoteForm.invalid || isCalculatingQuote()"
              >
                {{ isCalculatingQuote() ? 'Calculating quote...' : 'Calculate Quote' }}
              </ui-button>
              <button
                type="button"
                id="quote-clear-btn"
                class="px-4 py-2 rounded-lg border border-slate-200 text-slate-600 hover:text-slate-900 hover:bg-slate-50 text-sm font-semibold transition-colors cursor-pointer"
                (click)="clearQuote()"
              >
                Clear
              </button>
            </div>
          </form>

          @if (quoteError()) {
            <div id="quote-error" class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800" role="alert">
              {{ quoteError() }}
            </div>
          }

          @if (quoteResult(); as result) {
            <div id="quote-result" class="rounded-2xl border border-indigo-100 bg-gradient-to-br from-indigo-50/60 to-violet-50/40 p-5 space-y-4">
              <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-indigo-200/60 pb-3">
                <div>
                  <p class="text-xs uppercase font-bold text-indigo-600 tracking-wider">Estimated Delivery Quote</p>
                  <div class="flex items-center gap-2 mt-1">
                    <span id="quote-total-fee" class="text-2xl font-black text-slate-900">
                      {{ result.currency || 'LKR' }} {{ result.totalFee | number: '1.2-2' }}
                    </span>
                    <span class="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                      Calculated via Backend
                    </span>
                  </div>
                </div>
                @if (result.pickupZone || result.dropoffZone) {
                  <div class="text-right">
                    <span class="text-xs text-slate-500 font-medium">Route Coverage</span>
                    <p class="text-sm font-bold text-slate-800">
                      {{ result.pickupZone || 'Zone' }} to {{ result.dropoffZone || 'Zone' }}
                    </p>
                  </div>
                }
              </div>

              <div class="grid gap-3 sm:grid-cols-3">
                <div class="p-3.5 rounded-xl bg-white/80 border border-slate-200/80 shadow-xs">
                  <span class="text-xs font-semibold text-slate-500 block">Drop-off Base Fee</span>
                  <span id="quote-base-fee" class="text-base font-bold text-slate-900 mt-1 block">
                    {{ result.currency || 'LKR' }} {{ result.breakdown.dropoffZoneBaseFee | number: '1.2-2' }}
                  </span>
                </div>

                <div class="p-3.5 rounded-xl bg-white/80 border border-slate-200/80 shadow-xs">
                  <span class="text-xs font-semibold text-slate-500 block">Weight Band Charge</span>
                  <span id="quote-weight-fee" class="text-base font-bold text-slate-900 mt-1 block">
                    {{ result.currency || 'LKR' }} {{ result.breakdown.weightBandCharge | number: '1.2-2' }}
                  </span>
                </div>

                <div class="p-3.5 rounded-xl bg-white/80 border border-slate-200/80 shadow-xs">
                  <span class="text-xs font-semibold text-slate-500 block">Size Charge</span>
                  <span id="quote-size-fee" class="text-base font-bold text-slate-900 mt-1 block">
                    {{ result.currency || 'LKR' }} {{ result.breakdown.sizeCharge | number: '1.2-2' }}
                  </span>
                </div>
              </div>

              <p class="text-xs text-slate-500 italic">
                Formula per ADR 003: Fee = Drop-off Zone Base Fee ({{ result.currency || 'LKR' }} {{ result.breakdown.dropoffZoneBaseFee }}) + Weight Band Charge ({{ result.currency || 'LKR' }} {{ result.breakdown.weightBandCharge }}) + Size Charge ({{ result.currency || 'LKR' }} {{ result.breakdown.sizeCharge }}).
              </p>
            </div>
          }
        </div>
      </ui-card>
    </div>
  `,
})
export class DispatchPricingComponent {
  readonly dispatchService = inject(DispatchService);
  readonly mutationError = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly isSaving = signal<boolean>(false);
  readonly pricingRules = signal<PricingRule[]>([]);
  editingRuleId: string | null = null;

  readonly canonicalDistricts = CANONICAL_DISTRICTS;
  readonly quoteResult = signal<QuoteResponse | null>(null);
  readonly isCalculatingQuote = signal<boolean>(false);
  readonly quoteError = signal<string | null>(null);

  readonly pricingForm: FormGroup;
  readonly quoteForm: FormGroup;

  constructor(private readonly formBuilder: FormBuilder) {
    this.pricingForm = this.formBuilder.nonNullable.group({
      kind: ['SIZE' as PricingRule['kind'], [Validators.required]],
      key: ['', [Validators.required]],
      minimumWeightKg: [0],
      maximumWeightKg: [0],
      chargeLkr: [0, [Validators.required, Validators.min(0)]],
      active: [true],
    }, { validators: weightBandBoundsValidator });

    this.quoteForm = this.formBuilder.nonNullable.group({
      pickupDistrict: ['Colombo', [Validators.required]],
      dropoffDistrict: ['Kandy', [Validators.required]],
      weightKg: [1.0, [Validators.required, Validators.min(0.0001)]],
      size: ['MEDIUM' as ParcelSize, [Validators.required]],
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
    if (this.pricingForm.invalid || this.isSaving()) {
      this.pricingForm.markAllAsTouched();
      return;
    }
    this.mutationError.set(null);
    this.isSaving.set(true);
    const raw = this.pricingForm.getRawValue();
    const request = raw.kind === 'SIZE'
      ? { kind: raw.kind, key: raw.key, chargeLkr: raw.chargeLkr, active: raw.active }
      : raw;
    const isEditing = !!this.editingRuleId;
    const ruleKey = request.key;

    const operation = isEditing
      ? this.dispatchService.updatePricingRule(this.editingRuleId!, request)
      : this.dispatchService.createPricingRule(request);

    operation.subscribe({
      next: () => {
        this.editingRuleId = null;
        this.isSaving.set(false);
        this.successMessage.set(
          isEditing
            ? `Pricing rule "${ruleKey}" updated successfully.`
            : `Pricing rule "${ruleKey}" created successfully.`
        );
        this.pricingForm.reset({
          kind: 'SIZE', key: '', minimumWeightKg: 0, maximumWeightKg: 0, chargeLkr: 0, active: true,
        });
        this.loadPricingRules();
      },
      error: (error) => {
        this.isSaving.set(false);
        this.mutationError.set(
          error?.error?.message || error?.message || 'Unable to save the pricing rule.'
        );
      },
    });
  }

  editPricingRule(rule: PricingRule): void {
    this.editingRuleId = rule.id;
    this.mutationError.set(null);
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
    this.mutationError.set(null);
    this.dispatchService.deletePricingRule(rule.id).subscribe({
      next: () => {
        this.successMessage.set(`Pricing rule "${rule.key}" deleted successfully.`);
        this.loadPricingRules();
      },
      error: (error) => this.mutationError.set(
        error?.error?.message || error?.message || 'Unable to delete the pricing rule.'
      ),
    });
  }

  calculateQuote(): void {
    if (this.quoteForm.invalid || this.isCalculatingQuote()) {
      this.quoteForm.markAllAsTouched();
      return;
    }

    const { pickupDistrict, dropoffDistrict, weightKg, size } = this.quoteForm.getRawValue();
    this.isCalculatingQuote.set(true);
    this.quoteError.set(null);
    this.quoteResult.set(null);

    this.dispatchService.calculateQuote(pickupDistrict, dropoffDistrict, Number(weightKg), size).subscribe({
      next: (result) => {
        this.isCalculatingQuote.set(false);
        if (!result || typeof result.totalFee !== 'number' || !result.breakdown) {
          this.quoteError.set('Pricing engine returned an incomplete quote response.');
          return;
        }
        this.quoteResult.set(result);
      },
      error: (error) => {
        this.isCalculatingQuote.set(false);
        if (error?.status === 422) {
          this.quoteError.set(`Pickup district "${pickupDistrict}" is not an active served area.`);
        } else if (error?.status === 404) {
          this.quoteError.set(error?.error?.message || 'Pricing rule or district not found in active configuration.');
        } else {
          this.quoteError.set(error?.error?.message || error?.message || 'Failed to calculate quote from pricing engine.');
        }
      },
    });
  }

  clearQuote(): void {
    this.quoteResult.set(null);
    this.quoteError.set(null);
    this.quoteForm.reset({
      pickupDistrict: 'Colombo',
      dropoffDistrict: 'Kandy',
      weightKg: 1.0,
      size: 'MEDIUM',
    });
    this.quoteForm.markAsPristine();
    this.quoteForm.markAsUntouched();
  }
}
