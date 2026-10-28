import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AddressDto,
  CreateCustomerParcelRequest,
  CustomerAddress,
  CustomerParcel,
  District,
  FeePreview,
  ParcelSize,
  ParcelStatus,
  SRI_LANKA_DISTRICTS,
} from './customer/customer.model';
import { CustomerService } from './customer/customer.service';

type CustomerTab = 'deliveries' | 'wizard' | 'addresses';

@Component({
  selector: 'app-customer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto space-y-6 pb-12">
      <!-- Portal Header -->
      <div
        class="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-6 flex flex-col md:flex-row md:items-center justify-between gap-4"
      >
        <div>
          <div class="flex items-center gap-3">
            <span
              class="inline-flex items-center justify-center w-10 h-10 rounded-xl bg-indigo-600 text-white font-bold text-lg shadow-sm shadow-indigo-200"
            >
              SD
            </span>
            <div>
              <h1 class="text-2xl font-bold text-slate-900 tracking-tight">
                Customer Delivery Portal
              </h1>
              <p class="text-xs text-slate-500 font-medium">
                SwiftDrop Express Parcel Booking & Address Book
              </p>
            </div>
          </div>
        </div>

        <!-- Navigation Tabs -->
        <div
          class="inline-flex rounded-xl bg-slate-100 p-1.5 border border-slate-200 text-sm font-semibold"
        >
          <button
            type="button"
            id="tab-deliveries-btn"
            (click)="activeTab.set('deliveries')"
            [class.bg-white]="activeTab() === 'deliveries'"
            [class.shadow-sm]="activeTab() === 'deliveries'"
            [class.text-indigo-600]="activeTab() === 'deliveries'"
            [class.text-slate-600]="activeTab() !== 'deliveries'"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2"
          >
            <span>📦</span> My Deliveries
            <span
              class="ml-1 text-xs px-2 py-0.5 rounded-full"
              [class.bg-indigo-100]="activeTab() === 'deliveries'"
              [class.text-indigo-700]="activeTab() === 'deliveries'"
              [class.bg-slate-200]="activeTab() !== 'deliveries'"
              [class.text-slate-600]="activeTab() !== 'deliveries'"
            >
              {{ parcels().length }}
            </span>
          </button>

          <button
            type="button"
            id="tab-wizard-btn"
            (click)="activeTab.set('wizard')"
            [class.bg-white]="activeTab() === 'wizard'"
            [class.shadow-sm]="activeTab() === 'wizard'"
            [class.text-indigo-600]="activeTab() === 'wizard'"
            [class.text-slate-600]="activeTab() !== 'wizard'"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2"
          >
            <span>✨</span> New Delivery
          </button>

          <button
            type="button"
            id="tab-addresses-btn"
            (click)="activeTab.set('addresses')"
            [class.bg-white]="activeTab() === 'addresses'"
            [class.shadow-sm]="activeTab() === 'addresses'"
            [class.text-indigo-600]="activeTab() === 'addresses'"
            [class.text-slate-600]="activeTab() !== 'addresses'"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2"
          >
            <span>📖</span> Address Book
            <span
              class="ml-1 text-xs px-2 py-0.5 rounded-full"
              [class.bg-indigo-100]="activeTab() === 'addresses'"
              [class.text-indigo-700]="activeTab() === 'addresses'"
              [class.bg-slate-200]="activeTab() !== 'addresses'"
              [class.text-slate-600]="activeTab() !== 'addresses'"
            >
              {{ addresses().length }}
            </span>
          </button>
        </div>
      </div>

      <!-- Toast Notification -->
      @if (notification()) {
      <div
        id="toast-notification"
        class="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-xl flex items-center justify-between text-sm shadow-sm transition-all"
      >
        <div class="flex items-center gap-2 font-medium">
          <span class="text-emerald-600 text-base">✓</span>
          <span>{{ notification() }}</span>
        </div>
        <button
          type="button"
          (click)="notification.set(null)"
          class="text-emerald-600 hover:text-emerald-900 font-bold"
        >
          ✕
        </button>
      </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 1: MY DELIVERIES                                              -->
      <!-- ================================================================= -->
      @if (activeTab() === 'deliveries') {
      <div class="space-y-6">
        <!-- Quick Filter & Actions -->
        <div
          class="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-xs font-semibold text-slate-500 uppercase tracking-wider mr-1">
              Filter Status:
            </span>
            @for (st of statusFilters; track st.value) {
            <button
              type="button"
              (click)="selectedStatusFilter.set(st.value); loadParcels()"
              [class.bg-indigo-600]="selectedStatusFilter() === st.value"
              [class.text-white]="selectedStatusFilter() === st.value"
              [class.bg-slate-100]="selectedStatusFilter() !== st.value"
              [class.text-slate-600]="selectedStatusFilter() !== st.value"
              class="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all hover:bg-slate-200"
            >
              {{ st.label }}
            </button>
            }
          </div>

          <button
            type="button"
            (click)="startNewDelivery()"
            class="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-sm transition-all"
          >
            <span>+</span> Book Delivery
          </button>
        </div>

        <!-- Parcels Table / Card List -->
        <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm text-slate-600">
              <thead
                class="bg-slate-50/75 border-b border-slate-200 text-xs font-bold text-slate-700 uppercase tracking-wider"
              >
                <tr>
                  <th class="px-6 py-4">Tracking Number</th>
                  <th class="px-6 py-4">Status</th>
                  <th class="px-6 py-4">Route</th>
                  <th class="px-6 py-4">Recipient</th>
                  <th class="px-6 py-4">Weight & Size</th>
                  <th class="px-6 py-4">Fee (LKR)</th>
                  <th class="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 font-medium">
                @if (parcels().length === 0) {
                <tr>
                  <td colspan="7" class="px-6 py-12 text-center text-slate-400">
                    <p class="text-base font-semibold">No deliveries found</p>
                    <p class="text-xs text-slate-400 mt-1">
                      Use the New Delivery Wizard to send your first parcel!
                    </p>
                  </td>
                </tr>
                } @for (parcel of parcels(); track parcel.id) {
                <tr class="hover:bg-slate-50/60 transition-colors">
                  <td class="px-6 py-4 font-mono font-bold text-indigo-600 text-xs">
                    {{ parcel.trackingNumber }}
                  </td>
                  <td class="px-6 py-4">
                    <span
                      class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold"
                      [ngClass]="getStatusBadgeClass(parcel.status)"
                    >
                      {{ parcel.status }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    <div class="text-xs font-semibold text-slate-900">
                      {{ parcel.pickupAddress.district }} → {{ parcel.dropoffAddress.district }}
                    </div>
                    <div class="text-xs text-slate-400 truncate max-w-xs">
                      {{ parcel.dropoffAddress.line1 }}, {{ parcel.dropoffAddress.city }}
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div class="text-xs font-bold text-slate-800">
                      {{ parcel.recipientName }}
                    </div>
                    <div class="text-xs text-slate-400">
                      {{ parcel.recipientPhone }}
                    </div>
                  </td>
                  <td class="px-6 py-4 text-xs text-slate-700">
                    {{ parcel.weightKg }} kg ·
                    <span class="font-semibold">{{ parcel.size }}</span>
                  </td>
                  <td class="px-6 py-4 font-bold text-slate-900 text-sm">
                    Rs. {{ parcel.fee.toFixed(2) }}
                  </td>
                  <td class="px-6 py-4 text-right">
                    @if (parcel.status === 'CREATED') {
                    <button
                      type="button"
                      (click)="cancelParcel(parcel.id)"
                      class="px-3 py-1 bg-rose-50 text-rose-600 hover:bg-rose-100 rounded-lg text-xs font-semibold border border-rose-200 transition-colors"
                    >
                      Cancel
                    </button>
                    } @else {
                    <span class="text-xs text-slate-400">Immutable</span>
                    }
                  </td>
                </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 2: NEW DELIVERY WIZARD                                        -->
      <!-- ================================================================= -->
      @if (activeTab() === 'wizard') {
      <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-6 sm:p-8 space-y-8">
        <!-- Wizard Progress Bar -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-xs font-bold text-slate-500 uppercase tracking-wider">
            <span [class.text-indigo-600]="wizardStep() >= 1">1. Route & Addresses</span>
            <span [class.text-indigo-600]="wizardStep() >= 2">2. Parcel & Recipient</span>
            <span [class.text-indigo-600]="wizardStep() >= 3">3. Fee Preview</span>
            <span [class.text-indigo-600]="wizardStep() >= 4">4. Confirmation</span>
          </div>
          <div class="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
            <div
              class="h-full bg-indigo-600 transition-all duration-300"
              [style.width.%]="(wizardStep() / 4) * 100"
            ></div>
          </div>
        </div>

        <!-- STEP 1: ROUTE & ADDRESSES -->
        @if (wizardStep() === 1) {
        <div class="space-y-6">
          <div class="border-b border-slate-100 pb-4">
            <h2 class="text-lg font-bold text-slate-900">Step 1: Origin & Destination</h2>
            <p class="text-xs text-slate-500 mt-1">
              Specify pickup and delivery addresses. Sri Lanka district selection enforces ADR 002.
            </p>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
            <!-- Pickup Address -->
            <div class="space-y-4 p-5 rounded-2xl bg-slate-50/70 border border-slate-200/60">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <span class="w-6 h-6 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center text-xs">
                    A
                  </span>
                  Pickup Origin (Sender)
                </h3>
                @if (addresses().length > 0) {
                <select
                  (change)="selectSavedAddress('pickup', $any($event.target).value)"
                  class="text-xs bg-white border border-slate-200 rounded-lg px-2 py-1 font-semibold text-indigo-600"
                >
                  <option value="">Quick fill from Address Book...</option>
                  @for (a of addresses(); track a.id) {
                  <option [value]="a.id">{{ a.label }} ({{ a.district }})</option>
                  }
                </select>
                }
              </div>

              <div class="space-y-3 text-xs font-semibold text-slate-700">
                <div>
                  <label class="block mb-1">Street Address Line 1 *</label>
                  <input
                    type="text"
                    id="pickup-line1"
                    [(ngModel)]="wizardData.pickupAddress.line1"
                    placeholder="e.g. 142 Havelock Road"
                    class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label class="block mb-1">Street Address Line 2 (Optional)</label>
                  <input
                    type="text"
                    [(ngModel)]="wizardData.pickupAddress.line2"
                    placeholder="Apartment, suite, unit, etc."
                    class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block mb-1">City *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.pickupAddress.city"
                      placeholder="e.g. Colombo 05"
                      class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                    />
                  </div>
                  <div>
                    <label class="block mb-1">Postal Code *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.pickupAddress.postalCode"
                      placeholder="e.g. 00500"
                      class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label class="block mb-1">District (ADR 002) *</label>
                  <select
                    id="pickup-district"
                    [(ngModel)]="wizardData.pickupAddress.district"
                    (change)="updateFeePreview()"
                    class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl font-bold text-slate-900 focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  >
                    @for (d of districts; track d) {
                    <option [value]="d">{{ d }}</option>
                    }
                  </select>
                </div>
              </div>
            </div>

            <!-- Dropoff Address -->
            <div class="space-y-4 p-5 rounded-2xl bg-slate-50/70 border border-slate-200/60">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-bold text-slate-800 flex items-center gap-2">
                  <span class="w-6 h-6 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center text-xs">
                    B
                  </span>
                  Dropoff Destination (Recipient)
                </h3>
                @if (addresses().length > 0) {
                <select
                  (change)="selectSavedAddress('dropoff', $any($event.target).value)"
                  class="text-xs bg-white border border-slate-200 rounded-lg px-2 py-1 font-semibold text-indigo-600"
                >
                  <option value="">Quick fill from Address Book...</option>
                  @for (a of addresses(); track a.id) {
                  <option [value]="a.id">{{ a.label }} ({{ a.district }})</option>
                  }
                </select>
                }
              </div>

              <div class="space-y-3 text-xs font-semibold text-slate-700">
                <div>
                  <label class="block mb-1">Street Address Line 1 *</label>
                  <input
                    type="text"
                    id="dropoff-line1"
                    [(ngModel)]="wizardData.dropoffAddress.line1"
                    placeholder="e.g. 88 Kandy Road"
                    class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label class="block mb-1">Street Address Line 2 (Optional)</label>
                  <input
                    type="text"
                    [(ngModel)]="wizardData.dropoffAddress.line2"
                    placeholder="Apartment, building, gate, etc."
                    class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block mb-1">City *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.dropoffAddress.city"
                      placeholder="e.g. Kadugannawa"
                      class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                    />
                  </div>
                  <div>
                    <label class="block mb-1">Postal Code *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.dropoffAddress.postalCode"
                      placeholder="e.g. 20300"
                      class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label class="block mb-1">District (ADR 002) *</label>
                  <select
                    id="dropoff-district"
                    [(ngModel)]="wizardData.dropoffAddress.district"
                    (change)="updateFeePreview()"
                    class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl font-bold text-slate-900 focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  >
                    @for (d of districts; track d) {
                    <option [value]="d">{{ d }}</option>
                    }
                  </select>
                </div>
              </div>
            </div>
          </div>

          <div class="flex justify-end pt-4">
            <button
              type="button"
              id="wizard-step1-next"
              (click)="goToStep(2)"
              [disabled]="!isStep1Valid()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-sm rounded-xl shadow-sm transition-all"
            >
              Continue to Parcel Details →
            </button>
          </div>
        </div>
        }

        <!-- STEP 2: PARCEL DETAILS -->
        @if (wizardStep() === 2) {
        <div class="space-y-6">
          <div class="border-b border-slate-100 pb-4">
            <h2 class="text-lg font-bold text-slate-900">Step 2: Package & Recipient Info</h2>
            <p class="text-xs text-slate-500 mt-1">
              Select tier, weight, and enter recipient contact numbers for courier coordination.
            </p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
            <!-- Package Spec -->
            <div class="space-y-4">
              <div>
                <label class="block text-xs font-bold text-slate-700 mb-2">Package Size Tier *</label>
                <div class="grid grid-cols-3 gap-3">
                  @for (tier of sizeTiers; track tier.size) {
                  <button
                    type="button"
                    (click)="wizardData.size = tier.size; updateFeePreview()"
                    [class.border-indigo-600]="wizardData.size === tier.size"
                    [class.bg-indigo-50/50]="wizardData.size === tier.size"
                    [class.ring-2]="wizardData.size === tier.size"
                    [class.ring-indigo-500/20]="wizardData.size === tier.size"
                    class="p-3 text-left border rounded-xl transition-all"
                  >
                    <div class="font-bold text-xs text-slate-900">{{ tier.label }}</div>
                    <div class="text-[10px] text-slate-500 mt-1">{{ tier.desc }}</div>
                    <div class="text-[10px] font-bold text-indigo-600 mt-2">{{ tier.surcharge }}</div>
                  </button>
                  }
                </div>
              </div>

              <div>
                <label class="block text-xs font-bold text-slate-700 mb-1">
                  Weight (kg): <span class="text-indigo-600">{{ wizardData.weightKg }} kg</span>
                </label>
                <input
                  type="range"
                  min="0.1"
                  max="50"
                  step="0.1"
                  [(ngModel)]="wizardData.weightKg"
                  (input)="updateFeePreview()"
                  class="w-full accent-indigo-600"
                />
                <div class="flex justify-between text-[10px] text-slate-400 font-bold">
                  <span>0.1 kg</span>
                  <span>25 kg</span>
                  <span>50 kg</span>
                </div>
              </div>
            </div>

            <!-- Recipient Spec -->
            <div class="space-y-3 text-xs font-semibold text-slate-700">
              <div>
                <label class="block mb-1">Recipient Full Name *</label>
                <input
                  type="text"
                  id="recipient-name"
                  [(ngModel)]="wizardData.recipientName"
                  placeholder="e.g. Sunil Silva"
                  class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <label class="block mb-1">Recipient Mobile Phone *</label>
                <input
                  type="text"
                  id="recipient-phone"
                  [(ngModel)]="wizardData.recipientPhone"
                  placeholder="e.g. +94771234567"
                  class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <label class="block mb-1">Special Delivery Instructions (Optional)</label>
                <textarea
                  rows="2"
                  [(ngModel)]="wizardData.notes"
                  placeholder="e.g. Gate code #1234, ring bell twice"
                  class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-between pt-4">
            <button
              type="button"
              (click)="goToStep(1)"
              class="px-5 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-sm rounded-xl transition-all"
            >
              ← Back
            </button>
            <button
              type="button"
              id="wizard-step2-next"
              (click)="goToStep(3)"
              [disabled]="!isStep2Valid()"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-sm rounded-xl shadow-sm transition-all"
            >
              Review Fee Preview →
            </button>
          </div>
        </div>
        }

        <!-- STEP 3: FEE PREVIEW -->
        @if (wizardStep() === 3) {
        <div class="space-y-6">
          <div class="border-b border-slate-100 pb-4">
            <h2 class="text-lg font-bold text-slate-900">Step 3: Instant Pricing Quote</h2>
            <p class="text-xs text-slate-500 mt-1">
              Guaranteed frozen fee calculated per ADR 003 formula before booking commitment.
            </p>
          </div>

          @if (feePreview(); as quote) {
          <div class="grid grid-cols-1 md:grid-cols-2 gap-8 items-start">
            <!-- Route Card -->
            <div class="bg-slate-50/70 p-6 rounded-2xl border border-slate-200 space-y-4">
              <h3 class="text-xs font-bold text-slate-500 uppercase tracking-wider">
                Delivery Route Overview
              </h3>

              <div class="flex items-center gap-4">
                <div class="flex flex-col items-center">
                  <div class="w-3 h-3 rounded-full bg-indigo-600 ring-4 ring-indigo-100"></div>
                  <div class="w-0.5 h-12 bg-slate-200 my-1"></div>
                  <div class="w-3 h-3 rounded-full bg-emerald-600 ring-4 ring-emerald-100"></div>
                </div>

                <div class="space-y-4 text-xs">
                  <div>
                    <div class="font-bold text-slate-900 text-sm">
                      {{ quote.pickupDistrict }} District
                    </div>
                    <div class="text-slate-500">
                      {{ wizardData.pickupAddress.line1 }}, {{ wizardData.pickupAddress.city }}
                    </div>
                  </div>
                  <div>
                    <div class="font-bold text-slate-900 text-sm">
                      {{ quote.dropoffDistrict }} District
                    </div>
                    <div class="text-slate-500">
                      {{ wizardData.dropoffAddress.line1 }}, {{ wizardData.dropoffAddress.city }}
                    </div>
                  </div>
                </div>
              </div>

              <div class="pt-2 border-t border-slate-200/80 flex items-center justify-between text-xs text-slate-600 font-medium">
                <span>Inter-District Transfer:</span>
                <span class="font-bold text-slate-800">
                  {{ quote.pickupDistrict === quote.dropoffDistrict ? 'Intra-District (Same District)' : 'Inter-District (+Rs. 150)' }}
                </span>
              </div>
            </div>

            <!-- Price Breakdown Card -->
            <div
              class="bg-indigo-900 text-white p-6 rounded-2xl shadow-xl shadow-indigo-900/10 space-y-5"
            >
              <div class="flex items-center justify-between">
                <span class="text-xs font-semibold text-indigo-300 uppercase tracking-wider">
                  Price Calculation
                </span>
                <span class="text-xs font-bold bg-indigo-800 text-indigo-200 px-2.5 py-1 rounded-full">
                  ADR 003 Frozen
                </span>
              </div>

              <div class="space-y-2.5 text-xs">
                <div class="flex justify-between text-indigo-200">
                  <span>Base Express Fare</span>
                  <span class="font-mono">Rs. {{ quote.baseFee.toFixed(2) }}</span>
                </div>
                <div class="flex justify-between text-indigo-200">
                  <span>Inter-District Surcharge</span>
                  <span class="font-mono">Rs. {{ quote.interDistrictFee.toFixed(2) }}</span>
                </div>
                <div class="flex justify-between text-indigo-200">
                  <span>Weight Surcharge ({{ quote.weightKg }} kg)</span>
                  <span class="font-mono">Rs. {{ quote.weightFee.toFixed(2) }}</span>
                </div>
                <div class="flex justify-between text-indigo-200">
                  <span>Size Tier Surcharge ({{ quote.size }})</span>
                  <span class="font-mono">Rs. {{ quote.sizeTierFee.toFixed(2) }}</span>
                </div>

                <div class="pt-4 border-t border-indigo-700/60 flex items-center justify-between">
                  <span class="text-sm font-bold text-white">Total Delivery Fee</span>
                  <span id="wizard-total-fee" class="text-2xl font-black text-white font-mono">
                    Rs. {{ quote.totalFee.toFixed(2) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
          }

          <div class="flex items-center justify-between pt-4">
            <button
              type="button"
              (click)="goToStep(2)"
              class="px-5 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-sm rounded-xl transition-all"
            >
              ← Back
            </button>
            <button
              type="button"
              id="wizard-step3-next"
              (click)="goToStep(4)"
              class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-sm rounded-xl shadow-sm transition-all"
            >
              Confirm Booking Details →
            </button>
          </div>
        </div>
        }

        <!-- STEP 4: CONFIRM & BOOK -->
        @if (wizardStep() === 4) {
        <div class="space-y-6">
          @if (!orderConfirmed()) {
          <div class="border-b border-slate-100 pb-4">
            <h2 class="text-lg font-bold text-slate-900">Step 4: Review & Place Delivery Order</h2>
            <p class="text-xs text-slate-500 mt-1">
              Please inspect the final details below before issuing the parcel order.
            </p>
          </div>

          <div class="bg-slate-50/70 p-6 rounded-2xl border border-slate-200 grid grid-cols-1 md:grid-cols-2 gap-6 text-xs">
            <div>
              <span class="font-bold text-slate-400 uppercase tracking-wider block mb-1">
                Pickup Address
              </span>
              <p class="font-bold text-slate-800 text-sm">
                {{ wizardData.pickupAddress.line1 }}
              </p>
              <p class="text-slate-600">
                {{ wizardData.pickupAddress.city }}, {{ wizardData.pickupAddress.district }} {{ wizardData.pickupAddress.postalCode }}
              </p>
            </div>

            <div>
              <span class="font-bold text-slate-400 uppercase tracking-wider block mb-1">
                Dropoff Address
              </span>
              <p class="font-bold text-slate-800 text-sm">
                {{ wizardData.dropoffAddress.line1 }}
              </p>
              <p class="text-slate-600">
                {{ wizardData.dropoffAddress.city }}, {{ wizardData.dropoffAddress.district }} {{ wizardData.dropoffAddress.postalCode }}
              </p>
            </div>

            <div>
              <span class="font-bold text-slate-400 uppercase tracking-wider block mb-1">
                Recipient Contact
              </span>
              <p class="font-bold text-slate-800 text-sm">
                {{ wizardData.recipientName }}
              </p>
              <p class="text-slate-600">{{ wizardData.recipientPhone }}</p>
            </div>

            <div>
              <span class="font-bold text-slate-400 uppercase tracking-wider block mb-1">
                Parcel Specifications & Fee
              </span>
              <p class="font-bold text-slate-800 text-sm">
                {{ wizardData.weightKg }} kg · {{ wizardData.size }}
              </p>
              <p class="text-indigo-600 font-bold font-mono">
                Rs. {{ feePreview()?.totalFee?.toFixed(2) }} LKR
              </p>
            </div>
          </div>

          <div class="flex items-center justify-between pt-4">
            <button
              type="button"
              (click)="goToStep(3)"
              class="px-5 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-sm rounded-xl transition-all"
            >
              ← Back
            </button>
            <button
              type="button"
              id="confirm-booking-btn"
              (click)="submitDeliveryOrder()"
              [disabled]="isSubmitting()"
              class="px-8 py-3 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-sm rounded-xl shadow-lg shadow-indigo-600/20 transition-all flex items-center gap-2"
            >
              @if (isSubmitting()) {
              <span>Placing Order...</span>
              } @else {
              <span>✓ Confirm & Place Order</span>
              }
            </button>
          </div>
          } @else {
          <!-- Celebratory Order Success View -->
          <div class="text-center py-10 space-y-4">
            <div class="w-16 h-16 rounded-2xl bg-emerald-100 text-emerald-600 flex items-center justify-center text-3xl mx-auto shadow-inner">
              ✓
            </div>
            <h2 class="text-2xl font-black text-slate-900 tracking-tight">
              Order Confirmed & Booked!
            </h2>
            <p class="text-xs text-slate-500 max-w-md mx-auto">
              Your parcel delivery request has been registered in status CREATED and added to the dispatch queue.
            </p>

            <div class="bg-indigo-50 border border-indigo-200 rounded-2xl p-6 max-w-sm mx-auto space-y-1">
              <span class="text-xs font-semibold text-indigo-500 uppercase tracking-wider">
                Official Tracking Number
              </span>
              <div id="confirmed-tracking-number" class="text-xl font-black text-indigo-700 font-mono tracking-wider">
                {{ confirmedParcel()?.trackingNumber }}
              </div>
            </div>

            <div class="flex items-center justify-center gap-3 pt-4">
              <button
                type="button"
                (click)="activeTab.set('deliveries')"
                class="px-5 py-2.5 bg-indigo-600 text-white font-bold text-xs rounded-xl hover:bg-indigo-700 transition-all"
              >
                View in My Deliveries
              </button>
              <button
                type="button"
                (click)="startNewDelivery()"
                class="px-5 py-2.5 bg-slate-100 text-slate-700 font-bold text-xs rounded-xl hover:bg-slate-200 transition-all"
              >
                Send Another Parcel
              </button>
            </div>
          </div>
          }
        </div>
        }
      </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 3: ADDRESS BOOK                                               -->
      <!-- ================================================================= -->
      @if (activeTab() === 'addresses') {
      <div class="space-y-6">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-bold text-slate-900">Saved Address Book</h2>
            <p class="text-xs text-slate-500">
              Manage frequently used pickup and dropoff locations for one-click delivery booking.
            </p>
          </div>
          <button
            type="button"
            id="open-add-address-modal"
            (click)="openAddressModal()"
            class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded-xl shadow-sm transition-all"
          >
            + Add New Address
          </button>
        </div>

        <!-- Addresses Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          @for (addr of addresses(); track addr.id) {
          <div
            class="bg-white rounded-2xl p-5 border border-slate-200/80 shadow-sm flex flex-col justify-between space-y-4 hover:border-slate-300 transition-all"
          >
            <div class="space-y-2">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold px-2.5 py-1 rounded-full bg-slate-100 text-slate-700">
                  {{ addr.label }}
                </span>
                @if (addr.isDefault) {
                <span class="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">
                  Default
                </span>
                }
              </div>

              <div class="text-sm font-bold text-slate-900">
                {{ addr.line1 }}
              </div>
              @if (addr.line2) {
              <div class="text-xs text-slate-500">
                {{ addr.line2 }}
              </div>
              }
              <div class="text-xs font-semibold text-slate-600">
                {{ addr.city }}, {{ addr.district }} {{ addr.postalCode }}
              </div>
            </div>

            <div class="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
              @if (!addr.isDefault) {
              <button
                type="button"
                (click)="setDefaultAddress(addr.id)"
                class="text-indigo-600 hover:text-indigo-800 font-bold"
              >
                Set Default
              </button>
              } @else {
              <span class="text-slate-400">Primary address</span>
              }

              <button
                type="button"
                (click)="deleteAddress(addr.id)"
                class="text-rose-500 hover:text-rose-700 font-bold"
              >
                Delete
              </button>
            </div>
          </div>
          }
        </div>
      </div>
      }

      <!-- Address Modal -->
      @if (showAddressModal()) {
      <div
        class="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      >
        <div
          class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95 duration-150"
        >
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 class="text-base font-bold text-slate-900">Add New Address</h3>
            <button
              type="button"
              (click)="showAddressModal.set(false)"
              class="text-slate-400 hover:text-slate-600 font-bold"
            >
              ✕
            </button>
          </div>

          <div class="space-y-3 text-xs font-semibold text-slate-700">
            <div>
              <label class="block mb-1">Label *</label>
              <input
                type="text"
                id="modal-label"
                [(ngModel)]="newAddressForm.label"
                placeholder="e.g. Home, Office, Warehouse"
                class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
              />
            </div>

            <div>
              <label class="block mb-1">Street Address Line 1 *</label>
              <input
                type="text"
                id="modal-line1"
                [(ngModel)]="newAddressForm.line1"
                placeholder="e.g. 12 Flower Road"
                class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
              />
            </div>

            <div>
              <label class="block mb-1">Line 2 (Optional)</label>
              <input
                type="text"
                [(ngModel)]="newAddressForm.line2"
                placeholder="Apartment, suite, etc."
                class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
              />
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block mb-1">City *</label>
                <input
                  type="text"
                  id="modal-city"
                  [(ngModel)]="newAddressForm.city"
                  placeholder="e.g. Colombo 07"
                  class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>
              <div>
                <label class="block mb-1">Postal Code *</label>
                <input
                  type="text"
                  id="modal-postal-code"
                  [(ngModel)]="newAddressForm.postalCode"
                  placeholder="e.g. 00700"
                  class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label class="block mb-1">District (ADR 002) *</label>
              <select
                id="modal-district"
                [(ngModel)]="newAddressForm.district"
                class="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl font-bold text-slate-900 focus:ring-2 focus:ring-indigo-500 focus:outline-none"
              >
                @for (d of districts; track d) {
                <option [value]="d">{{ d }}</option>
                }
              </select>
            </div>

            <div class="flex items-center gap-2 pt-2">
              <input
                type="checkbox"
                id="modal-is-default"
                [(ngModel)]="newAddressForm.isDefault"
                class="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
              />
              <label for="modal-is-default" class="text-xs text-slate-600 cursor-pointer">
                Set as my default delivery address
              </label>
            </div>
          </div>

          <div class="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button
              type="button"
              (click)="showAddressModal.set(false)"
              class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-xs rounded-xl transition-all"
            >
              Cancel
            </button>
            <button
              type="button"
              id="save-address-btn"
              (click)="saveNewAddress()"
              [disabled]="!isAddressFormValid()"
              class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-xs rounded-xl transition-all shadow-sm"
            >
              Save Address
            </button>
          </div>
        </div>
      </div>
      }
    </div>
  `,
})
export class CustomerComponent implements OnInit {
  private customerService = inject(CustomerService);

  readonly districts = SRI_LANKA_DISTRICTS;
  readonly sizeTiers: { size: ParcelSize; label: string; desc: string; surcharge: string }[] = [
    { size: 'SMALL', label: 'Small', desc: 'Up to 1kg (docs, accessories)', surcharge: 'Standard Rate' },
    { size: 'MEDIUM', label: 'Medium', desc: '1 - 5kg (shoes, apparel)', surcharge: '+Rs. 100' },
    { size: 'LARGE', label: 'Large', desc: '5 - 50kg (heavy goods, boxes)', surcharge: '+Rs. 250' },
  ];

  readonly statusFilters: { label: string; value: ParcelStatus | null }[] = [
    { label: 'All', value: null },
    { label: 'Created', value: 'CREATED' },
    { label: 'In Transit', value: 'IN_TRANSIT' },
    { label: 'Delivered', value: 'DELIVERED' },
  ];

  activeTab = signal<CustomerTab>('deliveries');
  parcels = signal<CustomerParcel[]>([]);
  addresses = signal<CustomerAddress[]>([]);
  selectedStatusFilter = signal<ParcelStatus | null>(null);
  notification = signal<string | null>(null);

  // Wizard state
  wizardStep = signal<number>(1);
  wizardData: CreateCustomerParcelRequest = {
    pickupAddress: {
      line1: '142 Havelock Road',
      line2: 'Apt 4B',
      city: 'Colombo 05',
      district: 'Colombo',
      postalCode: '00500',
    },
    dropoffAddress: {
      line1: '88 Kandy Road',
      line2: null,
      city: 'Kadugannawa',
      district: 'Kandy',
      postalCode: '20300',
    },
    weightKg: 2.5,
    size: 'MEDIUM',
    recipientName: 'Kamal Silva',
    recipientPhone: '+94771234567',
    notes: 'Please ring bell',
  };

  feePreview = signal<FeePreview | null>(null);
  isSubmitting = signal<boolean>(false);
  orderConfirmed = signal<boolean>(false);
  confirmedParcel = signal<CustomerParcel | null>(null);

  // Address modal state
  showAddressModal = signal<boolean>(false);
  newAddressForm = {
    label: '',
    line1: '',
    line2: '',
    city: '',
    district: 'Colombo' as District,
    postalCode: '',
    isDefault: false,
  };

  ngOnInit(): void {
    this.loadParcels();
    this.loadAddresses();
    this.updateFeePreview();
  }

  loadParcels(): void {
    this.customerService.listParcels(this.selectedStatusFilter() || undefined).subscribe((p) => {
      this.parcels.set(p);
    });
  }

  loadAddresses(): void {
    this.customerService.listAddresses().subscribe((addrs) => {
      this.addresses.set(addrs);
    });
  }

  startNewDelivery(): void {
    this.wizardStep.set(1);
    this.orderConfirmed.set(false);
    this.confirmedParcel.set(null);
    this.activeTab.set('wizard');
    this.updateFeePreview();
  }

  goToStep(step: number): void {
    this.wizardStep.set(step);
    if (step === 3) {
      this.updateFeePreview();
    }
  }

  updateFeePreview(): void {
    const quote = this.customerService.calculateFeePreview(
      this.wizardData.pickupAddress.district,
      this.wizardData.dropoffAddress.district,
      Number(this.wizardData.weightKg),
      this.wizardData.size
    );
    this.feePreview.set(quote);
  }

  selectSavedAddress(type: 'pickup' | 'dropoff', addressId: string): void {
    if (!addressId) return;
    const match = this.addresses().find((a) => a.id === addressId);
    if (match) {
      const target: AddressDto = {
        line1: match.line1,
        line2: match.line2,
        city: match.city,
        district: match.district,
        postalCode: match.postalCode,
      };
      if (type === 'pickup') {
        this.wizardData.pickupAddress = target;
      } else {
        this.wizardData.dropoffAddress = target;
      }
      this.updateFeePreview();
    }
  }

  isStep1Valid(): boolean {
    const p = this.wizardData.pickupAddress;
    const d = this.wizardData.dropoffAddress;
    return !!(p.line1 && p.city && p.district && p.postalCode && d.line1 && d.city && d.district && d.postalCode);
  }

  isStep2Valid(): boolean {
    return !!(
      this.wizardData.weightKg > 0 &&
      this.wizardData.size &&
      this.wizardData.recipientName?.trim() &&
      this.wizardData.recipientPhone?.trim()
    );
  }

  submitDeliveryOrder(): void {
    this.isSubmitting.set(true);
    this.customerService.createParcel(this.wizardData).subscribe({
      next: (created) => {
        this.isSubmitting.set(false);
        this.confirmedParcel.set(created);
        this.orderConfirmed.set(true);
        this.loadParcels();
        this.notification.set(`Parcel booked successfully! Tracking: ${created.trackingNumber}`);
      },
      error: () => {
        this.isSubmitting.set(false);
      },
    });
  }

  cancelParcel(id: string): void {
    this.customerService.cancelParcel(id, 'Cancelled by customer').subscribe({
      next: (cancelled) => {
        this.loadParcels();
        this.notification.set(`Delivery order ${cancelled.trackingNumber} has been cancelled.`);
      },
      error: (err) => {
        this.notification.set(err.message || 'Failed to cancel parcel');
      },
    });
  }

  openAddressModal(): void {
    this.newAddressForm = {
      label: '',
      line1: '',
      line2: '',
      city: '',
      district: 'Colombo',
      postalCode: '',
      isDefault: false,
    };
    this.showAddressModal.set(true);
  }

  isAddressFormValid(): boolean {
    const f = this.newAddressForm;
    return !!(f.label && f.line1 && f.city && f.district && f.postalCode);
  }

  saveNewAddress(): void {
    if (!this.isAddressFormValid()) return;
    this.customerService
      .createAddress({
        label: this.newAddressForm.label.trim(),
        line1: this.newAddressForm.line1.trim(),
        line2: this.newAddressForm.line2.trim() || null,
        city: this.newAddressForm.city.trim(),
        district: this.newAddressForm.district,
        postalCode: this.newAddressForm.postalCode.trim(),
        isDefault: this.newAddressForm.isDefault,
      })
      .subscribe((saved) => {
        this.loadAddresses();
        this.showAddressModal.set(false);
        this.notification.set(`Address '${saved.label}' added to address book.`);
      });
  }

  setDefaultAddress(id: string): void {
    this.customerService.setDefaultAddress(id).subscribe((updated) => {
      this.loadAddresses();
      this.notification.set(`'${updated.label}' is now your default address.`);
    });
  }

  deleteAddress(id: string): void {
    this.customerService.deleteAddress(id).subscribe(() => {
      this.loadAddresses();
      this.notification.set('Address deleted.');
    });
  }

  getStatusBadgeClass(status: ParcelStatus): string {
    switch (status) {
      case 'CREATED':
        return 'bg-blue-50 text-blue-700 border border-blue-200';
      case 'PICKED_UP':
      case 'IN_TRANSIT':
      case 'OUT_FOR_DELIVERY':
        return 'bg-amber-50 text-amber-700 border border-amber-200';
      case 'DELIVERED':
        return 'bg-emerald-50 text-emerald-700 border border-emerald-200';
      case 'FAILED':
      case 'CANCELLED':
        return 'bg-rose-50 text-rose-700 border border-rose-200';
      default:
        return 'bg-slate-100 text-slate-700';
    }
  }
}
