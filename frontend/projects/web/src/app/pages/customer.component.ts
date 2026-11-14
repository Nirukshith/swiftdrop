import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AddressDto,
  CreateCustomerParcelRequest,
  CustomerAddress,
  CustomerParcel,
  CustomerParcelPage,
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
    <div class="customer-portal-wrap max-w-7xl mx-auto space-y-6 pb-16">
      <!-- Portal Header -->
      <div
        class="portal-header bg-white rounded-3xl p-6 sm:p-7 border border-[#e8e2dc] shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-5"
      >
        <div class="flex items-center gap-4">
          <div
            class="brand-badge w-12 h-12 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 flex-shrink-0"
          >
            <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
              <line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <span class="portal-pill">Customer Portal</span>
              <span class="text-xs font-semibold text-[#6f6d85]">Islandwide Delivery</span>
            </div>
            <h1 class="portal-title text-2xl font-black text-[#1d1b2c] tracking-tight mt-0.5">
              Express Parcel Booking
            </h1>
          </div>
        </div>

        <!-- Navigation Tabs (Segmented Pill Style) -->
        <div
          class="tab-pill-bar inline-flex rounded-full bg-[#f1f5f9] p-1.5 border border-[#e2e8f0]/80 text-sm font-bold"
        >
          <button
            type="button"
            id="tab-deliveries-btn"
            (click)="activeTab.set('deliveries')"
            [class.active]="activeTab() === 'deliveries'"
            class="tab-pill px-5 py-2.5 rounded-full transition-all flex items-center gap-2 font-bold text-sm"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
              <line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
            <span>My Deliveries</span>
            <span
              class="count-badge ml-1 text-xs px-2 py-0.5 rounded-full"
              [class.active-badge]="activeTab() === 'deliveries'"
            >
              {{ parcels().length }}
            </span>
          </button>

          <button
            type="button"
            id="tab-wizard-btn"
            (click)="activeTab.set('wizard')"
            [class.active]="activeTab() === 'wizard'"
            class="tab-pill px-5 py-2.5 rounded-full transition-all flex items-center gap-2 font-bold text-sm"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <span>New Delivery</span>
          </button>

          <button
            type="button"
            id="tab-addresses-btn"
            (click)="activeTab.set('addresses')"
            [class.active]="activeTab() === 'addresses'"
            class="tab-pill px-5 py-2.5 rounded-full transition-all flex items-center gap-2 font-bold text-sm"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
              <circle cx="12" cy="10" r="3"></circle>
            </svg>
            <span>Address Book</span>
            <span
              class="count-badge ml-1 text-xs px-2 py-0.5 rounded-full"
              [class.active-badge]="activeTab() === 'addresses'"
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
        class="toast-alert bg-[#faf6f0] border border-[#a53a26]/20 text-[#1d1b2c] px-5 py-3.5 rounded-2xl flex items-center justify-between text-sm shadow-sm transition-all"
      >
        <div class="flex items-center gap-3 font-semibold">
          <div class="w-7 h-7 rounded-full bg-[#a53a26]/10 text-[#a53a26] flex items-center justify-center flex-shrink-0">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          </div>
          <span>{{ notification() }}</span>
        </div>
        <button
          type="button"
          (click)="notification.set(null)"
          class="text-[#6f6d85] hover:text-[#1d1b2c] font-bold p-1 rounded-lg transition-colors"
          aria-label="Dismiss notification"
        >
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
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
          class="bg-white rounded-3xl p-5 border border-[#e8e2dc] shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-xs font-bold text-[#6f6d85] uppercase tracking-wider mr-1">
              Filter Status:
            </span>
            @for (st of statusFilters; track $index) {
            <button
              type="button"
              (click)="selectedStatusFilter.set(st.value); selectedParcelPage.set(0); loadParcels()"
              [class.filter-active]="selectedStatusFilter() === st.value"
              class="filter-pill px-4 py-2 rounded-full text-xs font-bold transition-all"
            >
              {{ st.label }}
            </button>
            }
          </div>

          <button
            type="button"
            (click)="startNewDelivery()"
            class="action-pill-btn inline-flex items-center gap-2 px-5 py-2.5 bg-[#1d1b2c] hover:bg-black text-white text-sm font-bold rounded-full shadow-sm transition-all"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <span>Book Delivery</span>
          </button>
        </div>

        <!-- Parcels Table / Card List -->
        <div class="bg-white rounded-3xl border border-[#e8e2dc] shadow-sm overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm text-[#1d1b2c]">
              <thead
                class="bg-[#faf8f6] border-b border-[#eee7e0] text-xs font-bold text-[#6f6d85] uppercase tracking-wider"
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
              <tbody class="divide-y divide-[#f2ede8] font-medium">
                @if (parcels().length === 0) {
                <tr>
                  <td colspan="7" class="px-6 py-14 text-center">
                    <div class="w-12 h-12 rounded-2xl bg-[#faf6f0] text-[#a53a26] flex items-center justify-center mx-auto mb-3">
                      <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                        <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                        <line x1="12" y1="22.08" x2="12" y2="12"/>
                      </svg>
                    </div>
                    <p class="text-base font-bold text-[#1d1b2c]">No deliveries found</p>
                    <p class="text-xs text-[#6f6d85] mt-1 font-medium">
                      Use the New Delivery Wizard to send your first parcel!
                    </p>
                  </td>
                </tr>
                } @for (parcel of parcels(); track parcel.id) {
                <tr class="hover:bg-[#faf8f6]/80 transition-colors">
                  <td class="px-6 py-4">
                    <span class="font-mono font-bold text-xs px-2.5 py-1 rounded-lg bg-[#faf6f0] text-[#a53a26] border border-[#a53a26]/15">
                      {{ parcel.trackingNumber }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    <span
                      class="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold"
                      [ngClass]="getStatusBadgeClass(parcel.status)"
                    >
                      {{ parcel.status }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    <div class="text-xs font-bold text-[#1d1b2c] flex items-center gap-1.5">
                      <span>{{ parcel.pickupAddress.district }}</span>
                      <span class="text-[#a53a26]">→</span>
                      <span>{{ parcel.dropoffAddress.district }}</span>
                    </div>
                    <div class="text-xs text-[#6f6d85] truncate max-w-xs mt-0.5">
                      {{ parcel.dropoffAddress.line1 }}, {{ parcel.dropoffAddress.city }}
                    </div>
                  </td>
                  <td class="px-6 py-4">
                    <div class="text-xs font-bold text-[#1d1b2c]">
                      {{ parcel.recipientName }}
                    </div>
                    <div class="text-xs text-[#6f6d85]">
                      {{ parcel.recipientPhone }}
                    </div>
                  </td>
                  <td class="px-6 py-4 text-xs font-semibold text-[#1d1b2c]">
                    {{ parcel.weightKg }} kg ·
                    <span class="text-[#a53a26] font-bold">{{ parcel.size }}</span>
                  </td>
                  <td class="px-6 py-4 font-black text-[#1d1b2c] text-sm font-mono">
                    Rs. {{ parcel.fee.toFixed(2) }}
                  </td>
                  <td class="px-6 py-4 text-right">
                    @if (parcel.status === 'CREATED') {
                    <button
                      type="button"
                      (click)="cancelParcel(parcel.id)"
                      class="px-3.5 py-1.5 bg-rose-50 text-rose-600 hover:bg-rose-100 rounded-full text-xs font-bold border border-rose-200 transition-colors"
                    >
                      Cancel
                    </button>
                    } @else {
                    <span class="text-xs font-semibold text-[#a09eaf]">Immutable</span>
                    }
                  </td>
                </tr>
                }
              </tbody>
            </table>
          </div>
          @if (parcelPage().totalPages > 1) {
          <div class="flex items-center justify-between border-t border-[#eee7e0] px-6 py-4 bg-[#faf8f6]/50">
            <span class="text-xs font-bold text-[#6f6d85]">
              Page {{ parcelPage().page + 1 }} of {{ parcelPage().totalPages }}
            </span>
            <div class="flex items-center gap-2">
              <button
                type="button"
                (click)="changeParcelPage(parcelPage().page - 1)"
                [disabled]="parcelPage().page === 0"
                class="px-4 py-1.5 rounded-full text-xs font-bold border border-[#e2e8f0] bg-white text-[#1d1b2c] hover:bg-[#f1f5f9] disabled:opacity-40 transition-all"
              >
                Previous
              </button>
              <button
                type="button"
                (click)="changeParcelPage(parcelPage().page + 1)"
                [disabled]="parcelPage().page >= parcelPage().totalPages - 1"
                class="px-4 py-1.5 rounded-full text-xs font-bold border border-[#e2e8f0] bg-white text-[#1d1b2c] hover:bg-[#f1f5f9] disabled:opacity-40 transition-all"
              >
                Next
              </button>
            </div>
          </div>
          }
        </div>
      </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 2: NEW DELIVERY WIZARD                                        -->
      <!-- ================================================================= -->
      @if (activeTab() === 'wizard') {
      <div class="bg-white rounded-3xl border border-[#e8e2dc] shadow-sm p-6 sm:p-8 space-y-8">
        <!-- Wizard Progress Bar -->
        <div class="wizard-stepper space-y-3">
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs font-bold text-[#6f6d85]">
            <div class="step-label flex items-center gap-2" [class.step-active]="wizardStep() >= 1">
              <span class="step-num w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black" [class.num-active]="wizardStep() >= 1">1</span>
              <span>Route & Addresses</span>
            </div>
            <div class="step-label flex items-center gap-2" [class.step-active]="wizardStep() >= 2">
              <span class="step-num w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black" [class.num-active]="wizardStep() >= 2">2</span>
              <span>Parcel & Contact</span>
            </div>
            <div class="step-label flex items-center gap-2" [class.step-active]="wizardStep() >= 3">
              <span class="step-num w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black" [class.num-active]="wizardStep() >= 3">3</span>
              <span>Instant Quote</span>
            </div>
            <div class="step-label flex items-center gap-2" [class.step-active]="wizardStep() >= 4">
              <span class="step-num w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black" [class.num-active]="wizardStep() >= 4">4</span>
              <span>Confirmation</span>
            </div>
          </div>
          <div class="h-2 w-full bg-[#f1f5f9] rounded-full overflow-hidden">
            <div
              class="h-full bg-[#a53a26] transition-all duration-300 rounded-full"
              [style.width.%]="(wizardStep() / 4) * 100"
            ></div>
          </div>
        </div>

        <!-- STEP 1: ROUTE & ADDRESSES -->
        @if (wizardStep() === 1) {
        <div class="space-y-6">
          <div class="border-b border-[#eee7e0] pb-4">
            <span class="card-pill">Step 1 of 4</span>
            <h2 class="text-xl font-black text-[#1d1b2c] tracking-tight mt-1">Origin & Destination</h2>
            <p class="text-xs text-[#6f6d85] mt-1 font-medium">
              Specify pickup origin and delivery dropoff. Sri Lanka district routing enforces ADR 002.
            </p>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
            <!-- Pickup Address -->
            <div class="space-y-4 p-6 rounded-2xl bg-[#faf8f6] border border-[#eee7e0]">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-black text-[#1d1b2c] flex items-center gap-2">
                  <span class="w-7 h-7 rounded-xl bg-[#a53a26] text-white flex items-center justify-center text-xs font-black">
                    A
                  </span>
                  Pickup Origin (Sender)
                </h3>
                @if (addresses().length > 0) {
                <select
                  (change)="selectSavedAddress('pickup', $any($event.target).value)"
                  class="text-xs bg-white border border-[#e2e8f0] rounded-xl px-3 py-1.5 font-bold text-[#a53a26] shadow-sm focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20"
                >
                  <option value="">Quick fill from Address Book...</option>
                  @for (a of addresses(); track a.id) {
                  <option [value]="a.id">{{ a.label }} ({{ a.district }})</option>
                  }
                </select>
                }
              </div>

              <div class="space-y-3.5 text-xs font-bold text-[#1d1b2c]">
                <div>
                  <label class="block mb-1 text-[#3a384e]">Street Address Line 1 *</label>
                  <input
                    type="text"
                    id="pickup-line1"
                    [(ngModel)]="wizardData.pickupAddress.line1"
                    placeholder="e.g. 142 Havelock Road"
                    class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                  />
                </div>

                <div>
                  <label class="block mb-1 text-[#3a384e]">Street Address Line 2 (Optional)</label>
                  <input
                    type="text"
                    [(ngModel)]="wizardData.pickupAddress.line2"
                    placeholder="Apartment, suite, unit, etc."
                    class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block mb-1 text-[#3a384e]">City *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.pickupAddress.city"
                      placeholder="e.g. Colombo 05"
                      class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                    />
                  </div>
                  <div>
                    <label class="block mb-1 text-[#3a384e]">Postal Code *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.pickupAddress.postalCode"
                      placeholder="e.g. 00500"
                      class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                    />
                  </div>
                </div>

                <div>
                  <label class="block mb-1 text-[#3a384e]">District (ADR 002) *</label>
                  <select
                    id="pickup-district"
                    [(ngModel)]="wizardData.pickupAddress.district"
                    (change)="updateFeePreview()"
                    class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-bold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                  >
                    @for (d of districts; track d) {
                    <option [value]="d">{{ d }}</option>
                    }
                  </select>
                </div>
              </div>
            </div>

            <!-- Dropoff Address -->
            <div class="space-y-4 p-6 rounded-2xl bg-[#faf8f6] border border-[#eee7e0]">
              <div class="flex items-center justify-between">
                <h3 class="text-sm font-black text-[#1d1b2c] flex items-center gap-2">
                  <span class="w-7 h-7 rounded-xl bg-[#2e7d32] text-white flex items-center justify-center text-xs font-black">
                    B
                  </span>
                  Dropoff Destination (Recipient)
                </h3>
                @if (addresses().length > 0) {
                <select
                  (change)="selectSavedAddress('dropoff', $any($event.target).value)"
                  class="text-xs bg-white border border-[#e2e8f0] rounded-xl px-3 py-1.5 font-bold text-[#a53a26] shadow-sm focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20"
                >
                  <option value="">Quick fill from Address Book...</option>
                  @for (a of addresses(); track a.id) {
                  <option [value]="a.id">{{ a.label }} ({{ a.district }})</option>
                  }
                </select>
                }
              </div>

              <div class="space-y-3.5 text-xs font-bold text-[#1d1b2c]">
                <div>
                  <label class="block mb-1 text-[#3a384e]">Street Address Line 1 *</label>
                  <input
                    type="text"
                    id="dropoff-line1"
                    [(ngModel)]="wizardData.dropoffAddress.line1"
                    placeholder="e.g. 88 Kandy Road"
                    class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                  />
                </div>

                <div>
                  <label class="block mb-1 text-[#3a384e]">Street Address Line 2 (Optional)</label>
                  <input
                    type="text"
                    [(ngModel)]="wizardData.dropoffAddress.line2"
                    placeholder="Apartment, building, gate, etc."
                    class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block mb-1 text-[#3a384e]">City *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.dropoffAddress.city"
                      placeholder="e.g. Kadugannawa"
                      class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                    />
                  </div>
                  <div>
                    <label class="block mb-1 text-[#3a384e]">Postal Code *</label>
                    <input
                      type="text"
                      [(ngModel)]="wizardData.dropoffAddress.postalCode"
                      placeholder="e.g. 20300"
                      class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                    />
                  </div>
                </div>

                <div>
                  <label class="block mb-1 text-[#3a384e]">District (ADR 002) *</label>
                  <select
                    id="dropoff-district"
                    [(ngModel)]="wizardData.dropoffAddress.district"
                    (change)="updateFeePreview()"
                    class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-bold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
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
              class="action-pill-btn px-7 py-3 bg-[#1d1b2c] hover:bg-black disabled:opacity-40 text-white font-bold text-sm rounded-full shadow-sm transition-all"
            >
              Continue to Parcel Details →
            </button>
          </div>
        </div>
        }

        <!-- STEP 2: PARCEL DETAILS -->
        @if (wizardStep() === 2) {
        <div class="space-y-6">
          <div class="border-b border-[#eee7e0] pb-4">
            <span class="card-pill">Step 2 of 4</span>
            <h2 class="text-xl font-black text-[#1d1b2c] tracking-tight mt-1">Package & Recipient Info</h2>
            <p class="text-xs text-[#6f6d85] mt-1 font-medium">
              Select package size tier, weight, and enter recipient contact numbers for courier coordination.
            </p>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
            <!-- Package Spec -->
            <div class="space-y-5">
              <div>
                <label class="block text-xs font-bold text-[#1d1b2c] mb-2.5">Package Size Tier *</label>
                <div class="grid grid-cols-3 gap-3">
                  @for (tier of sizeTiers; track tier.size) {
                  <button
                    type="button"
                    (click)="wizardData.size = tier.size; updateFeePreview()"
                    [class.size-tier-active]="wizardData.size === tier.size"
                    class="size-tier-card p-3.5 text-left border border-[#e2e8f0] rounded-2xl bg-white hover:border-[#a53a26]/50 transition-all cursor-pointer"
                  >
                    <div class="font-black text-xs text-[#1d1b2c]">{{ tier.label }}</div>
                    <div class="text-[10px] text-[#6f6d85] mt-1 leading-snug">{{ tier.desc }}</div>
                    <div class="text-[10px] font-bold text-[#a53a26] mt-2.5">{{ tier.surcharge }}</div>
                  </button>
                  }
                </div>
              </div>

              <div class="p-4 rounded-2xl bg-[#faf8f6] border border-[#eee7e0] space-y-2">
                <div class="flex items-center justify-between text-xs font-bold text-[#1d1b2c]">
                  <span>Package Weight:</span>
                  <span class="text-[#a53a26] font-mono text-sm">{{ wizardData.weightKg }} kg</span>
                </div>
                <input
                  type="range"
                  min="0.1"
                  max="50"
                  step="0.1"
                  [(ngModel)]="wizardData.weightKg"
                  (input)="updateFeePreview()"
                  class="w-full accent-[#a53a26]"
                />
                <div class="flex justify-between text-[10px] text-[#6f6d85] font-bold">
                  <span>0.1 kg</span>
                  <span>25 kg</span>
                  <span>50 kg</span>
                </div>
              </div>
            </div>

            <!-- Recipient Spec -->
            <div class="space-y-3.5 text-xs font-bold text-[#1d1b2c]">
              <div>
                <label class="block mb-1 text-[#3a384e]">Recipient Full Name *</label>
                <input
                  type="text"
                  id="recipient-name"
                  [(ngModel)]="wizardData.recipientName"
                  placeholder="e.g. Sunil Silva"
                  class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                />
              </div>

              <div>
                <label class="block mb-1 text-[#3a384e]">Recipient Mobile Phone *</label>
                <input
                  type="text"
                  id="recipient-phone"
                  [(ngModel)]="wizardData.recipientPhone"
                  placeholder="e.g. +94771234567"
                  class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                />
              </div>

              <div>
                <label class="block mb-1 text-[#3a384e]">Special Delivery Instructions (Optional)</label>
                <textarea
                  rows="2"
                  [(ngModel)]="wizardData.notes"
                  placeholder="e.g. Gate code #1234, ring bell twice"
                  class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-between pt-4">
            <button
              type="button"
              (click)="goToStep(1)"
              class="px-5 py-2.5 bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#1d1b2c] font-bold text-sm rounded-full transition-all"
            >
              ← Back
            </button>
            <button
              type="button"
              id="wizard-step2-next"
              (click)="goToStep(3)"
              [disabled]="!isStep2Valid()"
              class="action-pill-btn px-7 py-3 bg-[#1d1b2c] hover:bg-black disabled:opacity-40 text-white font-bold text-sm rounded-full shadow-sm transition-all"
            >
              Review Fee Preview →
            </button>
          </div>
        </div>
        }

        <!-- STEP 3: FEE PREVIEW -->
        @if (wizardStep() === 3) {
        <div class="space-y-6">
          <div class="border-b border-[#eee7e0] pb-4">
            <span class="card-pill">Step 3 of 4</span>
            <h2 class="text-xl font-black text-[#1d1b2c] tracking-tight mt-1">Instant Pricing Quote</h2>
            <p class="text-xs text-[#6f6d85] mt-1 font-medium">
              Guaranteed frozen delivery fee calculated via ADR 003 formula before booking commitment.
            </p>
          </div>

          @if (isLoadingQuote()) {
          <div class="p-10 text-center bg-[#faf8f6] rounded-3xl border border-[#eee7e0] space-y-3">
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-4 border-[#a53a26] border-t-transparent"></div>
            <p class="text-sm font-bold text-[#1d1b2c]">Calculating instant delivery fee quote from backend...</p>
          </div>
          } @else if (quoteError()) {
          <div class="p-6 bg-rose-50 border border-rose-200 rounded-3xl space-y-3">
            <div class="flex items-center gap-2 text-rose-800 font-bold text-sm">
              <svg class="w-4 h-4 text-rose-600 flex-shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
              </svg>
              <span>Quote Calculation Notice</span>
            </div>
            <p class="text-xs text-rose-700 font-medium">{{ quoteError() }}</p>
            <button
              type="button"
              id="retry-quote-btn"
              (click)="updateFeePreview()"
              class="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs rounded-full transition-all"
            >
              Retry Quote
            </button>
          </div>
          } @else if (feePreview(); as quote) {
          <div class="grid grid-cols-1 md:grid-cols-2 gap-8 items-start">
            <!-- Route Card -->
            <div class="bg-[#faf8f6] p-6 rounded-3xl border border-[#eee7e0] space-y-5">
              <span class="card-pill">Route Overview</span>

              <div class="flex items-center gap-4">
                <div class="flex flex-col items-center">
                  <div class="w-3.5 h-3.5 rounded-full bg-[#a53a26] ring-4 ring-[#a53a26]/20"></div>
                  <div class="w-0.5 h-12 bg-[#e2e8f0] my-1"></div>
                  <div class="w-3.5 h-3.5 rounded-full bg-[#2e7d32] ring-4 ring-[#2e7d32]/20"></div>
                </div>

                <div class="space-y-4 text-xs">
                  <div>
                    <div class="font-black text-[#1d1b2c] text-sm">
                      {{ quote.pickupDistrict }} District
                    </div>
                    <div class="text-[#6f6d85] font-medium mt-0.5">
                      {{ wizardData.pickupAddress.line1 }}, {{ wizardData.pickupAddress.city }}
                    </div>
                  </div>
                  <div>
                    <div class="font-black text-[#1d1b2c] text-sm">
                      {{ quote.dropoffDistrict }} District
                    </div>
                    <div class="text-[#6f6d85] font-medium mt-0.5">
                      {{ wizardData.dropoffAddress.line1 }}, {{ wizardData.dropoffAddress.city }}
                    </div>
                  </div>
                </div>
              </div>

              @if (quote.pickupZone || quote.dropoffZone) {
              <div class="pt-3 border-t border-[#eee7e0] flex items-center justify-between text-xs text-[#6f6d85] font-medium">
                <span>Zone Routing:</span>
                <span class="font-bold text-[#1d1b2c]">
                  {{ quote.pickupZone || quote.pickupDistrict }} → {{ quote.dropoffZone || quote.dropoffDistrict }}
                </span>
              </div>
              }
            </div>

            <!-- Price Breakdown Card (Tactile Dark Ink Card) -->
            <div
              class="bg-[#1d1b2c] text-white p-6 sm:p-7 rounded-3xl shadow-xl shadow-[#1d1b2c]/20 space-y-5"
            >
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-[#a09eaf] uppercase tracking-wider">
                  Price Calculation
                </span>
                <span class="text-xs font-bold bg-[#a53a26] text-white px-3 py-1 rounded-full">
                  ADR 003 Frozen
                </span>
              </div>

              <div class="space-y-3 text-xs">
                <div class="flex justify-between text-[#c4c2d4] font-medium">
                  <span>Drop-off Zone Base Fee</span>
                  <span class="font-mono text-white">{{ quote.currency }} {{ quote.breakdown.dropoffZoneBaseFee.toFixed(2) }}</span>
                </div>
                <div class="flex justify-between text-[#c4c2d4] font-medium">
                  <span>Weight Band Charge ({{ quote.weightKg }} kg)</span>
                  <span class="font-mono text-white">{{ quote.currency }} {{ quote.breakdown.weightBandCharge.toFixed(2) }}</span>
                </div>
                <div class="flex justify-between text-[#c4c2d4] font-medium">
                  <span>Size Tier Charge ({{ quote.size }})</span>
                  <span class="font-mono text-white">{{ quote.currency }} {{ quote.breakdown.sizeCharge.toFixed(2) }}</span>
                </div>

                <div class="pt-4 border-t border-white/10 flex items-center justify-between">
                  <span class="text-sm font-bold text-white">Total Delivery Fee</span>
                  <span id="wizard-total-fee" class="text-2xl font-black text-white font-mono">
                    {{ quote.currency }} {{ quote.totalFee.toFixed(2) }}
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
              class="px-5 py-2.5 bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#1d1b2c] font-bold text-sm rounded-full transition-all"
            >
              ← Back
            </button>
            <button
              type="button"
              id="wizard-step3-next"
              (click)="goToStep(4)"
              [disabled]="isLoadingQuote() || !feePreview() || !!quoteError()"
              class="action-pill-btn px-7 py-3 bg-[#1d1b2c] hover:bg-black disabled:opacity-40 text-white font-bold text-sm rounded-full shadow-sm transition-all"
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
          <div class="border-b border-[#eee7e0] pb-4">
            <span class="card-pill">Step 4 of 4</span>
            <h2 class="text-xl font-black text-[#1d1b2c] tracking-tight mt-1">Review & Place Delivery Order</h2>
            <p class="text-xs text-[#6f6d85] mt-1 font-medium">
              Please inspect the final details below before issuing the parcel order to dispatch.
            </p>
          </div>

          <div class="bg-[#faf8f6] p-6 rounded-3xl border border-[#eee7e0] grid grid-cols-1 md:grid-cols-2 gap-6 text-xs">
            <div>
              <span class="font-bold text-[#6f6d85] uppercase tracking-wider block mb-1">
                Pickup Address
              </span>
              <p class="font-black text-[#1d1b2c] text-sm">
                {{ wizardData.pickupAddress.line1 }}
              </p>
              <p class="text-[#6f6d85] font-medium mt-0.5">
                {{ wizardData.pickupAddress.city }}, {{ wizardData.pickupAddress.district }} {{ wizardData.pickupAddress.postalCode }}
              </p>
            </div>

            <div>
              <span class="font-bold text-[#6f6d85] uppercase tracking-wider block mb-1">
                Dropoff Address
              </span>
              <p class="font-black text-[#1d1b2c] text-sm">
                {{ wizardData.dropoffAddress.line1 }}
              </p>
              <p class="text-[#6f6d85] font-medium mt-0.5">
                {{ wizardData.dropoffAddress.city }}, {{ wizardData.dropoffAddress.district }} {{ wizardData.dropoffAddress.postalCode }}
              </p>
            </div>

            <div>
              <span class="font-bold text-[#6f6d85] uppercase tracking-wider block mb-1">
                Recipient Contact
              </span>
              <p class="font-black text-[#1d1b2c] text-sm">
                {{ wizardData.recipientName }}
              </p>
              <p class="text-[#6f6d85] font-medium mt-0.5">{{ wizardData.recipientPhone }}</p>
            </div>

            <div>
              <span class="font-bold text-[#6f6d85] uppercase tracking-wider block mb-1">
                Parcel Specifications & Fee
              </span>
              <p class="font-black text-[#1d1b2c] text-sm">
                {{ wizardData.weightKg }} kg · {{ wizardData.size }}
              </p>
              <p class="text-[#a53a26] font-black font-mono text-base mt-0.5">
                {{ feePreview()?.currency || 'LKR' }} {{ feePreview()?.totalFee?.toFixed(2) }}
              </p>
            </div>
          </div>

          <div class="flex items-center justify-between pt-4">
            <button
              type="button"
              (click)="goToStep(3)"
              class="px-5 py-2.5 bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#1d1b2c] font-bold text-sm rounded-full transition-all"
            >
              ← Back
            </button>
            <button
              type="button"
              id="confirm-booking-btn"
              (click)="submitDeliveryOrder()"
              [disabled]="isSubmitting() || !feePreview()"
              class="action-pill-btn px-8 py-3.5 bg-[#a53a26] hover:bg-[#7e2615] disabled:opacity-50 text-white font-bold text-sm rounded-full shadow-lg shadow-[#a53a26]/20 transition-all flex items-center gap-2"
            >
              @if (isSubmitting()) {
              <span>Placing Order...</span>
              } @else {
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <span>Confirm & Place Order</span>
              }
            </button>
          </div>
          } @else {
          <!-- Celebratory Order Success View -->
          <div class="text-center py-10 space-y-5">
            <div class="w-16 h-16 rounded-3xl bg-[#faf6f0] text-[#a53a26] border border-[#a53a26]/20 flex items-center justify-center mx-auto shadow-sm">
              <svg class="w-8 h-8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
            </div>
            <h2 class="text-2xl font-black text-[#1d1b2c] tracking-tight">
              Order Confirmed & Booked!
            </h2>
            <p class="text-xs text-[#6f6d85] max-w-md mx-auto font-medium">
              Your parcel delivery request has been registered in status CREATED and added to the dispatch queue.
            </p>

            <div class="bg-[#faf6f0] border border-[#a53a26]/20 rounded-3xl p-6 max-w-sm mx-auto space-y-1.5 shadow-sm">
              <span class="text-xs font-bold text-[#a53a26] uppercase tracking-wider">
                Official Tracking Number
              </span>
              <div id="confirmed-tracking-number" class="text-xl font-black text-[#1d1b2c] font-mono tracking-wider">
                {{ confirmedParcel()?.trackingNumber }}
              </div>
              @if (confirmedParcel()?.id) {
              <div id="confirmed-parcel-id" class="text-[10px] text-[#6f6d85] font-mono">
                Order ID: {{ confirmedParcel()?.id }}
              </div>
              }
            </div>

            <div class="flex items-center justify-center gap-3 pt-3">
              <button
                type="button"
                (click)="activeTab.set('deliveries')"
                class="action-pill-btn px-6 py-2.5 bg-[#1d1b2c] text-white font-bold text-xs rounded-full hover:bg-black transition-all"
              >
                View in My Deliveries
              </button>
              <button
                type="button"
                (click)="startNewDelivery()"
                class="px-6 py-2.5 bg-[#f1f5f9] text-[#1d1b2c] font-bold text-xs rounded-full hover:bg-[#e2e8f0] transition-all"
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
            <h2 class="text-xl font-black text-[#1d1b2c] tracking-tight">Saved Address Book</h2>
            <p class="text-xs text-[#6f6d85] font-medium mt-0.5">
              Manage frequently used pickup and dropoff locations for one-click delivery booking.
            </p>
          </div>
          <button
            type="button"
            id="open-add-address-modal"
            (click)="openAddressModal()"
            class="action-pill-btn px-5 py-2.5 bg-[#1d1b2c] hover:bg-black text-white text-xs font-bold rounded-full shadow-sm transition-all flex items-center gap-1.5"
          >
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <span>Add New Address</span>
          </button>
        </div>

        <!-- Addresses Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          @for (addr of addresses(); track addr.id) {
          <div
            class="bg-white rounded-3xl p-6 border border-[#e8e2dc] shadow-sm flex flex-col justify-between space-y-4 hover:border-[#a53a26]/40 transition-all"
          >
            <div class="space-y-2.5">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold px-3 py-1 rounded-full bg-[#f1f5f9] text-[#1d1b2c]">
                  {{ addr.label }}
                </span>
                @if (addr.isDefault) {
                <span class="text-[10px] font-bold px-2.5 py-0.5 rounded-full bg-[#faf6f0] text-[#a53a26] border border-[#a53a26]/20">
                  Default
                </span>
                }
              </div>

              <div class="text-sm font-black text-[#1d1b2c]">
                {{ addr.line1 }}
              </div>
              @if (addr.line2) {
              <div class="text-xs text-[#6f6d85]">
                {{ addr.line2 }}
              </div>
              }
              <div class="text-xs font-semibold text-[#6f6d85]">
                {{ addr.city }}, {{ addr.district }} {{ addr.postalCode }}
              </div>
            </div>

            <div class="pt-3.5 border-t border-[#eee7e0] flex items-center justify-between text-xs">
              @if (!addr.isDefault) {
              <button
                type="button"
                (click)="setDefaultAddress(addr.id)"
                class="text-[#a53a26] hover:text-[#7e2615] font-bold"
              >
                Set Default
              </button>
              } @else {
              <span class="text-[#a09eaf] font-semibold">Primary address</span>
              }

              <button
                type="button"
                (click)="openAddressModal(addr)"
                class="text-[#1d1b2c] hover:text-[#a53a26] font-bold"
              >
                Edit
              </button>

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
        class="fixed inset-0 bg-[#1d1b2c]/40 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      >
        <div
          class="bg-white rounded-3xl border border-[#e8e2dc] shadow-2xl max-w-md w-full p-6 sm:p-7 space-y-4 animate-in fade-in zoom-in-95 duration-150"
        >
          <div class="flex items-center justify-between border-b border-[#eee7e0] pb-3.5">
            <h3 class="text-lg font-black text-[#1d1b2c]">
              {{ editingAddressId() ? 'Edit Address' : 'Add New Address' }}
            </h3>
            <button
              type="button"
              (click)="showAddressModal.set(false)"
              class="text-[#6f6d85] hover:text-[#1d1b2c] font-bold p-1 rounded-lg"
              aria-label="Close modal"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>

          <div class="space-y-3.5 text-xs font-bold text-[#1d1b2c]">
            <div>
              <label class="block mb-1 text-[#3a384e]">Label *</label>
              <input
                type="text"
                id="modal-label"
                [(ngModel)]="newAddressForm.label"
                placeholder="e.g. Home, Office, Warehouse"
                class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
              />
            </div>

            <div>
              <label class="block mb-1 text-[#3a384e]">Street Address Line 1 *</label>
              <input
                type="text"
                id="modal-line1"
                [(ngModel)]="newAddressForm.line1"
                placeholder="e.g. 12 Flower Road"
                class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
              />
            </div>

            <div>
              <label class="block mb-1 text-[#3a384e]">Line 2 (Optional)</label>
              <input
                type="text"
                [(ngModel)]="newAddressForm.line2"
                placeholder="Apartment, suite, etc."
                class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
              />
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block mb-1 text-[#3a384e]">City *</label>
                <input
                  type="text"
                  id="modal-city"
                  [(ngModel)]="newAddressForm.city"
                  placeholder="e.g. Colombo 07"
                  class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                />
              </div>
              <div>
                <label class="block mb-1 text-[#3a384e]">Postal Code *</label>
                <input
                  type="text"
                  id="modal-postal-code"
                  [(ngModel)]="newAddressForm.postalCode"
                  placeholder="e.g. 00700"
                  class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-semibold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
                />
              </div>
            </div>

            <div>
              <label class="block mb-1 text-[#3a384e]">District (ADR 002) *</label>
              <select
                id="modal-district"
                [(ngModel)]="newAddressForm.district"
                class="w-full px-3.5 py-2.5 bg-white border border-[#e2e8f0] rounded-xl text-sm font-bold text-[#1d1b2c] focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] focus:outline-none transition-all"
              >
                @for (d of districts; track d) {
                <option [value]="d">{{ d }}</option>
                }
              </select>
            </div>

            <div class="flex items-center gap-2.5 pt-2">
              <input
                type="checkbox"
                id="modal-is-default"
                [(ngModel)]="newAddressForm.isDefault"
                class="rounded-lg border-[#e2e8f0] text-[#a53a26] focus:ring-[#a53a26] w-4 h-4 cursor-pointer"
              />
              <label for="modal-is-default" class="text-xs font-semibold text-[#3a384e] cursor-pointer">
                Set as my default delivery address
              </label>
            </div>
          </div>

          <div class="flex items-center justify-end gap-3 pt-4 border-t border-[#eee7e0]">
            <button
              type="button"
              (click)="showAddressModal.set(false)"
              class="px-5 py-2 bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#1d1b2c] font-bold text-xs rounded-full transition-all"
            >
              Cancel
            </button>
            <button
              type="button"
              id="save-address-btn"
              (click)="saveAddress()"
              [disabled]="!isAddressFormValid()"
              class="action-pill-btn px-6 py-2 bg-[#a53a26] hover:bg-[#7e2615] disabled:opacity-40 text-white font-bold text-xs rounded-full transition-all shadow-sm"
            >
              Save Address
            </button>
          </div>
        </div>
      </div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        --terracotta: #a53a26;
        --terracotta-dark: #7e2615;
        --coral: #f2533a;
        --ink: #1d1b2c;
        --ink-2: #3a384e;
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
      .card-pill {
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
      .tab-pill {
        color: var(--ink-3);
        background: transparent;
        border: none;
        cursor: pointer;
      }
      .tab-pill:hover {
        color: var(--ink);
      }
      .tab-pill.active {
        background: #fff;
        color: var(--ink);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      }
      .count-badge {
        background: #e2e8f0;
        color: var(--ink-3);
        font-weight: 700;
      }
      .count-badge.active-badge {
        background: rgba(165, 58, 38, 0.12);
        color: var(--terracotta);
      }
      .filter-pill {
        background: #f1f5f9;
        color: var(--ink-3);
        border: 1px solid transparent;
        cursor: pointer;
      }
      .filter-pill:hover {
        background: #e2e8f0;
        color: var(--ink);
      }
      .filter-pill.filter-active {
        background: var(--terracotta);
        color: #fff;
        box-shadow: 0 4px 12px rgba(165, 58, 38, 0.25);
      }
      .action-pill-btn {
        transition: all 0.2s ease;
      }
      .action-pill-btn:hover:not(:disabled) {
        transform: translateY(-1px);
      }
      .size-tier-card.size-tier-active {
        border-color: var(--terracotta);
        background: #faf6f0;
        box-shadow: 0 0 0 2px var(--terracotta);
      }
      .step-label.step-active {
        color: var(--terracotta);
      }
      .step-num {
        background: #e2e8f0;
        color: var(--ink-3);
      }
      .step-num.num-active {
        background: var(--terracotta);
        color: #fff;
      }
    `,
  ],
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
  parcelPage = signal<CustomerParcelPage>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 20,
  });
  addresses = signal<CustomerAddress[]>([]);
  selectedParcelPage = signal(0);
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
  isLoadingQuote = signal<boolean>(false);
  quoteError = signal<string | null>(null);
  isSubmitting = signal<boolean>(false);
  orderConfirmed = signal<boolean>(false);
  confirmedParcel = signal<CustomerParcel | null>(null);

  // Address modal state
  showAddressModal = signal<boolean>(false);
  editingAddressId = signal<string | null>(null);
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
    this.customerService
      .listParcels(this.selectedStatusFilter() || undefined, this.selectedParcelPage())
      .subscribe({
        next: (page) => {
          this.parcelPage.set(page);
          this.parcels.set(page.content);
        },
        error: (err) => this.notification.set(this.apiErrorMessage(err, 'Unable to load parcels.')),
      });
  }

  changeParcelPage(page: number): void {
    if (page < 0 || page >= this.parcelPage().totalPages) return;
    this.selectedParcelPage.set(page);
    this.loadParcels();
  }

  loadAddresses(): void {
    this.customerService.listAddresses().subscribe({
      next: (addrs) => this.addresses.set(addrs),
      error: (err) => this.notification.set(this.apiErrorMessage(err, 'Unable to load addresses.')),
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
    const pickup = this.wizardData.pickupAddress.district;
    const dropoff = this.wizardData.dropoffAddress.district;
    const weight = Number(this.wizardData.weightKg);
    const size = this.wizardData.size;

    if (!pickup || !dropoff || !weight || weight <= 0 || !size) {
      this.feePreview.set(null);
      this.quoteError.set(null);
      return;
    }

    this.isLoadingQuote.set(true);
    this.quoteError.set(null);

    this.customerService.getFeeQuote(pickup, dropoff, weight, size).subscribe({
      next: (quote) => {
        this.feePreview.set({
          currency: quote.currency || 'LKR',
          totalFee: quote.totalFee,
          breakdown: quote.breakdown,
          pickupZone: quote.pickupZone,
          dropoffZone: quote.dropoffZone,
          pickupDistrict: pickup,
          dropoffDistrict: dropoff,
          weightKg: weight,
          size,
        });
        this.isLoadingQuote.set(false);
      },
      error: (err) => {
        this.feePreview.set(null);
        this.isLoadingQuote.set(false);
        const message =
          err.error?.message ||
          err.message ||
          'Unable to calculate delivery fee. Please verify your addresses and package details.';
        this.quoteError.set(message);
      },
    });
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
    if (this.isSubmitting() || !this.feePreview()) return;
    this.isSubmitting.set(true);
    this.customerService.createParcel(this.wizardData).subscribe({
      next: (created) => {
        this.isSubmitting.set(false);
        this.confirmedParcel.set(created);
        this.orderConfirmed.set(true);
        this.loadParcels();
        this.notification.set(`Parcel booked successfully! Tracking: ${created.trackingNumber}`);
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const message =
          err?.error?.message ||
          err?.message ||
          'Failed to place delivery order. Please try again.';
        this.notification.set(message);
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
        this.notification.set(this.apiErrorMessage(err, 'Failed to cancel parcel.'));
      },
    });
  }

  openAddressModal(address?: CustomerAddress): void {
    this.editingAddressId.set(address?.id || null);
    this.newAddressForm = address
      ? {
          label: address.label,
          line1: address.line1,
          line2: address.line2 || '',
          city: address.city,
          district: address.district,
          postalCode: address.postalCode,
          isDefault: address.isDefault,
        }
      : {
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

  saveAddress(): void {
    if (!this.isAddressFormValid()) return;
    const isEditing = !!this.editingAddressId();
    const request = {
      label: this.newAddressForm.label.trim(),
      line1: this.newAddressForm.line1.trim(),
      line2: this.newAddressForm.line2.trim() || null,
      city: this.newAddressForm.city.trim(),
      district: this.newAddressForm.district,
      postalCode: this.newAddressForm.postalCode.trim(),
      isDefault: this.newAddressForm.isDefault,
    };
    const operation = this.editingAddressId()
      ? this.customerService.updateAddress(this.editingAddressId()!, request)
      : this.customerService.createAddress(request);
    operation.subscribe({
      next: (saved) => {
        this.loadAddresses();
        this.showAddressModal.set(false);
        this.editingAddressId.set(null);
        this.notification.set(
          `Address '${saved.label}' ${isEditing ? 'updated' : 'added to address book'}.`
        );
      },
      error: (err) => this.notification.set(this.apiErrorMessage(err, 'Unable to save address.')),
    });
  }

  saveNewAddress(): void {
    this.saveAddress();
  }

  setDefaultAddress(id: string): void {
    this.customerService.setDefaultAddress(id).subscribe({
      next: (updated) => {
        this.loadAddresses();
        this.notification.set(`'${updated.label}' is now your default address.`);
      },
      error: (err) => this.notification.set(this.apiErrorMessage(err, 'Unable to set default address.')),
    });
  }

  deleteAddress(id: string): void {
    this.customerService.deleteAddress(id).subscribe({
      next: () => {
        this.loadAddresses();
        this.notification.set('Address deleted.');
      },
      error: (err) => this.notification.set(this.apiErrorMessage(err, 'Unable to delete address.')),
    });
  }

  private apiErrorMessage(error: { error?: { message?: string }; message?: string }, fallback: string): string {
    return error?.error?.message || error?.message || fallback;
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
