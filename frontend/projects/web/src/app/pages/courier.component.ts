import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CourierDelivery,
  CourierStats,
  DeliveryStatus,
  FailureReasonCode,
} from './courier/courier.model';
import { CourierService } from './courier/courier.service';

type FilterTab = 'ALL' | 'ACTIVE' | 'DELIVERED' | 'FAILED';

@Component({
  selector: 'app-courier',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-6xl mx-auto space-y-6 pb-16 px-4 sm:px-6">
      <!-- Courier Top Bar Header -->
      <div
        class="bg-white/90 backdrop-blur-md rounded-2xl shadow-sm border border-slate-200/80 p-5 sm:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
      >
        <div class="flex items-center gap-3.5">
          <div
            class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-500 to-indigo-600 flex items-center justify-center text-white shadow-md shadow-indigo-200 text-xl font-black"
          >
            🚚
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight">
                Courier Run Sheet
              </h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200"
              >
                ● Online
              </span>
            </div>
            <p class="text-xs sm:text-sm text-slate-500 font-medium">
              {{ profile().name }} (ID: {{ profile().userId }}) • {{ profile().zone || 'Assigned Zone' }}
            </p>
          </div>
        </div>

        <!-- Quick Summary Chips -->
        <div class="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <div
            class="px-3.5 py-2 rounded-xl bg-slate-50 border border-slate-200 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-slate-400">Total Run</p>
            <p class="text-base font-extrabold text-slate-800">{{ stats().total }}</p>
          </div>
          <div
            class="px-3.5 py-2 rounded-xl bg-amber-50 border border-amber-200 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-amber-600">Pending</p>
            <p class="text-base font-extrabold text-amber-700">
              {{ stats().created + stats().pickedUp + stats().inTransit + stats().outForDelivery }}
            </p>
          </div>
          <div
            class="px-3.5 py-2 rounded-xl bg-emerald-50 border border-emerald-200 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-emerald-600">Delivered</p>
            <p class="text-base font-extrabold text-emerald-700">{{ stats().delivered }}</p>
          </div>
          <button
            type="button"
            id="refresh-btn"
            (click)="refreshDeliveries()"
            [disabled]="isLoading()"
            title="Refresh Deliveries"
            class="p-2.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 hover:text-indigo-600 transition-colors shadow-xs"
          >
            <span [class.animate-spin]="isLoading()" class="inline-block">🔄</span>
          </button>
        </div>
      </div>

      <!-- Action & Search Toolbar -->
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <!-- Filter Tabs -->
        <div
          class="inline-flex rounded-xl bg-slate-100 p-1 border border-slate-200 text-xs sm:text-sm font-semibold overflow-x-auto"
        >
          <button
            type="button"
            id="tab-filter-all"
            (click)="activeFilter.set('ALL')"
            [class.bg-white]="activeFilter() === 'ALL'"
            [class.shadow-sm]="activeFilter() === 'ALL'"
            [class.text-indigo-600]="activeFilter() === 'ALL'"
            [class.text-slate-600]="activeFilter() !== 'ALL'"
            class="px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 whitespace-nowrap"
          >
            All ({{ stats().total }})
          </button>
          <button
            type="button"
            id="tab-filter-active"
            (click)="activeFilter.set('ACTIVE')"
            [class.bg-white]="activeFilter() === 'ACTIVE'"
            [class.shadow-sm]="activeFilter() === 'ACTIVE'"
            [class.text-indigo-600]="activeFilter() === 'ACTIVE'"
            [class.text-slate-600]="activeFilter() !== 'ACTIVE'"
            class="px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 whitespace-nowrap"
          >
            Active ({{ stats().created + stats().pickedUp + stats().inTransit + stats().outForDelivery }})
          </button>
          <button
            type="button"
            id="tab-filter-delivered"
            (click)="activeFilter.set('DELIVERED')"
            [class.bg-white]="activeFilter() === 'DELIVERED'"
            [class.shadow-sm]="activeFilter() === 'DELIVERED'"
            [class.text-emerald-600]="activeFilter() === 'DELIVERED'"
            [class.text-slate-600]="activeFilter() !== 'DELIVERED'"
            class="px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 whitespace-nowrap"
          >
            Delivered ({{ stats().delivered }})
          </button>
          <button
            type="button"
            id="tab-filter-failed"
            (click)="activeFilter.set('FAILED')"
            [class.bg-white]="activeFilter() === 'FAILED'"
            [class.shadow-sm]="activeFilter() === 'FAILED'"
            [class.text-rose-600]="activeFilter() === 'FAILED'"
            [class.text-slate-600]="activeFilter() !== 'FAILED'"
            class="px-3.5 py-1.5 rounded-lg transition-all flex items-center gap-1.5 whitespace-nowrap"
          >
            Failed ({{ stats().failed }})
          </button>
        </div>

        <!-- Search Input -->
        <div class="relative w-full md:w-72">
          <input
            type="text"
            id="search-input"
            [ngModel]="searchQuery()"
            (ngModelChange)="searchQuery.set($event)"
            placeholder="Search tracking, recipient, address..."
            class="w-full pl-9 pr-4 py-2 bg-white rounded-xl border border-slate-200 text-xs sm:text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-sm transition-all"
          />
          <span class="absolute left-3 top-2.5 text-slate-400 text-sm">🔍</span>
          <button
            *ngIf="searchQuery()"
            type="button"
            (click)="searchQuery.set('')"
            class="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 text-xs"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- Toast Notification Alert -->
      <div
        *ngIf="toastMessage()"
        id="toast-notification"
        class="rounded-xl p-4 bg-indigo-50 border border-indigo-200 text-indigo-900 text-sm flex items-center justify-between shadow-sm animate-fade-in"
      >
        <div class="flex items-center gap-2">
          <span>✨</span>
          <span class="font-medium">{{ toastMessage() }}</span>
        </div>
        <button
          type="button"
          (click)="toastMessage.set('')"
          class="text-indigo-400 hover:text-indigo-700 font-bold"
        >
          ✕
        </button>
      </div>

      <!-- Error State Alert -->
      <div
        *ngIf="errorMessage()"
        id="courier-error-banner"
        class="rounded-xl p-4 bg-rose-50 border border-rose-200 text-rose-900 text-sm flex items-center justify-between shadow-sm"
      >
        <div class="flex items-center gap-2">
          <span>⚠️</span>
          <span class="font-medium">{{ errorMessage() }}</span>
        </div>
        <button
          type="button"
          (click)="refreshDeliveries()"
          class="px-3 py-1 bg-white border border-rose-200 rounded-lg text-xs font-bold text-rose-700 hover:bg-rose-100"
        >
          Retry
        </button>
      </div>

      <!-- Loading State Indicator -->
      <div
        *ngIf="isLoading() && filteredDeliveries().length === 0"
        id="courier-loading-spinner"
        class="bg-white rounded-2xl border border-slate-200 p-12 text-center"
      >
        <div class="text-4xl animate-bounce mb-3">🚚</div>
        <h3 class="text-base font-bold text-slate-800">Loading deliveries...</h3>
        <p class="text-xs text-slate-500 mt-1">Fetching your assigned parcels from operations service.</p>
      </div>

      <!-- Deliveries List (Cards) -->
      <div *ngIf="!isLoading() || filteredDeliveries().length > 0" class="space-y-4">
        <div
          *ngFor="let delivery of filteredDeliveries()"
          [id]="'delivery-card-' + delivery.parcelId"
          class="bg-white rounded-2xl shadow-sm border border-slate-200/90 hover:border-indigo-300 hover:shadow-md transition-all overflow-hidden"
        >
          <!-- Card Header -->
          <div class="p-4 sm:p-5 flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 bg-slate-50/50">
            <div class="flex items-center gap-3">
              <span class="text-xs font-mono font-bold px-2.5 py-1 bg-white border border-slate-200 rounded-lg text-slate-700 shadow-2xs">
                {{ delivery.trackingNumber }}
              </span>
              <span
                [ngClass]="getStatusBadgeClass(delivery.status)"
                class="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider flex items-center gap-1"
              >
                {{ getStatusLabel(delivery.status) }}
              </span>
            </div>

            <div class="flex items-center gap-2 text-xs text-slate-500">
              <span *ngIf="delivery.attemptCount !== undefined" class="font-medium bg-slate-100 px-2 py-0.5 rounded-md">
                Attempt: {{ delivery.attemptCount }}
              </span>
              <span *ngIf="delivery.weightKg" class="font-medium bg-slate-100 px-2 py-0.5 rounded-md">
                ⚖️ {{ delivery.weightKg }} kg
              </span>
            </div>
          </div>

          <!-- Card Body -->
          <div class="p-4 sm:p-5 grid grid-cols-1 md:grid-cols-12 gap-4">
            <!-- Recipient & Destination -->
            <div class="md:col-span-7 space-y-2">
              <div class="flex items-baseline justify-between">
                <h3 class="text-base font-bold text-slate-900 flex items-center gap-1.5">
                  👤 {{ delivery.recipientName }}
                </h3>
                <a
                  *ngIf="delivery.recipientPhone"
                  [href]="'tel:' + delivery.recipientPhone"
                  class="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800 bg-indigo-50 hover:bg-indigo-100 px-2.5 py-1 rounded-lg transition-colors"
                >
                  📞 Call ({{ delivery.recipientPhone }})
                </a>
              </div>

              <!-- Address -->
              <div class="text-xs sm:text-sm text-slate-600 flex items-start gap-1.5">
                <span class="text-slate-400 mt-0.5">📍</span>
                <div>
                  <p class="font-medium text-slate-800">
                    {{ delivery.dropoffAddress || (delivery.recipientAddress?.line1) }}
                    <span *ngIf="delivery.recipientAddress?.city">, {{ delivery.recipientAddress?.city }}</span>
                  </p>
                  <a
                    *ngIf="delivery.googleMapsUrl"
                    [href]="delivery.googleMapsUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="text-xs font-medium text-indigo-600 hover:underline inline-flex items-center gap-1 mt-0.5"
                  >
                    🗺️ Open in Google Maps
                  </a>
                </div>
              </div>

              <!-- Special Instructions -->
              <div
                *ngIf="delivery.specialInstructions"
                class="p-2.5 bg-amber-50/70 border border-amber-200/70 rounded-xl text-xs text-amber-900 flex items-start gap-1.5"
              >
                <span class="text-amber-500">📝</span>
                <p><span class="font-bold">Instructions:</span> {{ delivery.specialInstructions }}</p>
              </div>

              <!-- Failure Reason if any -->
              <div
                *ngIf="(delivery.status === 'FAILED' || delivery.status === 'FAILED_DELIVERY') && delivery.failureReason"
                class="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-900 flex items-start gap-1.5"
              >
                <span class="text-rose-500">⚠️</span>
                <p><span class="font-bold">Failure Reason:</span> {{ delivery.failureReason }}</p>
              </div>

              <!-- Proof Thumbnail if delivered -->
              <div
                *ngIf="delivery.status === 'DELIVERED' && delivery.proof"
                class="p-2.5 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-900 flex items-center justify-between"
              >
                <div class="flex items-center gap-2">
                  <span class="text-emerald-600 text-lg">📸</span>
                  <div>
                    <p class="font-bold">Proof of Delivery Captured</p>
                    <p class="text-[11px] text-emerald-700">
                      Received by {{ delivery.proof.recipientName }}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  (click)="viewProofDetail(delivery)"
                  class="px-2.5 py-1 bg-white rounded-lg border border-emerald-300 font-semibold text-emerald-800 hover:bg-emerald-100 text-[11px] transition-colors"
                >
                  View Details
                </button>
              </div>
            </div>

            <!-- Sender / Origin Info & Stepper -->
            <div class="md:col-span-5 flex flex-col justify-between border-t md:border-t-0 md:border-l border-slate-100 md:pl-4 pt-3 md:pt-0 space-y-3">
              <div class="text-xs text-slate-500">
                <p class="text-[11px] uppercase font-bold text-slate-400">Pickup Origin</p>
                <p class="font-semibold text-slate-700 mt-0.5">{{ delivery.senderName || 'SwiftDrop Merchant' }}</p>
                <p>{{ delivery.pickupAddress || (delivery.senderAddress?.line1) }}</p>
              </div>

              <!-- Action Buttons Depending on State -->
              <div class="pt-2 space-y-2">
                <!-- If CREATED or ASSIGNED -> Can Confirm Picked Up -->
                <button
                  *ngIf="delivery.status === 'CREATED' || delivery.status === 'ASSIGNED'"
                  type="button"
                  [id]="'pickup-btn-' + delivery.parcelId"
                  [disabled]="isActionPending(delivery.parcelId)"
                  (click)="advanceStatus(delivery, 'PICKED_UP')"
                  class="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-xs sm:text-sm shadow-sm shadow-indigo-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                >
                  <span>📦</span> Confirm Picked Up
                </button>

                <!-- If PICKED_UP -> Can Start Delivery (In Transit) -->
                <button
                  *ngIf="delivery.status === 'PICKED_UP'"
                  type="button"
                  [id]="'transit-btn-' + delivery.parcelId"
                  [disabled]="isActionPending(delivery.parcelId)"
                  (click)="advanceStatus(delivery, 'IN_TRANSIT')"
                  class="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-bold text-xs sm:text-sm shadow-sm shadow-blue-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                >
                  <span>🚚</span> Start Delivery (In Transit)
                </button>

                <!-- If IN_TRANSIT -> Can Advance to OUT_FOR_DELIVERY -->
                <button
                  *ngIf="delivery.status === 'IN_TRANSIT'"
                  type="button"
                  [id]="'out-btn-' + delivery.parcelId"
                  [disabled]="isActionPending(delivery.parcelId)"
                  (click)="advanceStatus(delivery, 'OUT_FOR_DELIVERY')"
                  class="w-full py-2.5 px-4 rounded-xl bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white font-bold text-xs sm:text-sm shadow-sm shadow-amber-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                >
                  <span>📍</span> Out for Final Delivery
                </button>

                <!-- If OUT_FOR_DELIVERY -> Can Complete (with Proof) or Fail -->
                <div *ngIf="delivery.status === 'OUT_FOR_DELIVERY'" class="flex flex-col gap-2">
                  <button
                    type="button"
                    [id]="'deliver-btn-' + delivery.parcelId"
                    [disabled]="isActionPending(delivery.parcelId)"
                    (click)="openProofModal(delivery)"
                    class="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-bold text-xs sm:text-sm shadow-sm shadow-emerald-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                  >
                    <span>📸</span> Complete & Upload Proof
                  </button>
                  <button
                    type="button"
                    [id]="'fail-btn-' + delivery.parcelId"
                    [disabled]="isActionPending(delivery.parcelId)"
                    (click)="openFailModal(delivery)"
                    class="w-full py-2 px-3 rounded-xl bg-slate-100 hover:bg-rose-50 disabled:opacity-50 text-slate-700 hover:text-rose-700 font-semibold text-xs border border-slate-200 hover:border-rose-200 transition-colors flex items-center justify-center gap-1"
                  >
                    <span>⚠️</span> Mark Delivery Attempt Failed
                  </button>
                </div>

                <!-- If DELIVERED -> Done Badge -->
                <div
                  *ngIf="delivery.status === 'DELIVERED'"
                  class="w-full py-2 px-3 rounded-xl bg-emerald-50 text-emerald-800 text-xs font-bold text-center border border-emerald-200"
                >
                  ✅ Completed & Confirmed
                </div>

                <!-- If FAILED -> Attempt Recorded -->
                <div
                  *ngIf="delivery.status === 'FAILED' || delivery.status === 'FAILED_DELIVERY'"
                  class="w-full py-2 px-3 rounded-xl bg-rose-50 text-rose-800 text-xs font-bold text-center border border-rose-200"
                >
                  ❌ Delivery Attempt Failed
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div
          *ngIf="filteredDeliveries().length === 0 && !isLoading()"
          class="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center"
        >
          <div class="text-4xl mb-3">📦</div>
          <h3 class="text-base font-bold text-slate-800">No deliveries found</h3>
          <p class="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
            No parcels match the current filter or search criteria.
          </p>
          <button
            type="button"
            (click)="activeFilter.set('ALL'); searchQuery.set('')"
            class="mt-4 px-4 py-2 bg-indigo-50 text-indigo-600 font-semibold text-xs rounded-xl hover:bg-indigo-100 transition-colors"
          >
            Clear Filters
          </button>
        </div>
      </div>

      <!-- Proof of Delivery Modal Dialog -->
      <div
        *ngIf="selectedDeliveryForProof()"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in"
      >
        <div
          class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden transform transition-all"
        >
          <!-- Modal Header -->
          <div class="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
            <div>
              <h2 class="text-lg font-bold text-slate-900 flex items-center gap-2">
                <span>📸</span> Proof of Delivery
              </h2>
              <p class="text-xs text-slate-500 font-mono">
                Tracking: {{ selectedDeliveryForProof()?.trackingNumber }}
              </p>
            </div>
            <button
              type="button"
              id="close-proof-modal"
              (click)="closeProofModal()"
              class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 hover:text-slate-800 flex items-center justify-center text-sm font-bold transition-colors"
            >
              ✕
            </button>
          </div>

          <!-- Modal Body Form -->
          <div class="p-5 space-y-4">
            <!-- Recipient Name -->
            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Received By (Full Name) *
              </label>
              <input
                type="text"
                id="proof-recipient-name"
                [(ngModel)]="proofRecipientName"
                placeholder="Enter recipient or signatory name"
                class="w-full px-3.5 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              />
            </div>

            <!-- Notes -->
            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Delivery Notes / Remarks
              </label>
              <textarea
                id="proof-notes"
                [(ngModel)]="proofDeliveryNotes"
                rows="2"
                placeholder="e.g. Left with security, signed at front door"
                class="w-full px-3.5 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              ></textarea>
            </div>

            <!-- Photo Upload Area -->
            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Photo Proof (JPEG, PNG, WEBP - Max 5MB)
              </label>

              <div
                *ngIf="!proofImagePreview"
                class="border-2 border-dashed border-slate-200 hover:border-indigo-400 rounded-xl p-6 text-center cursor-pointer transition-colors bg-slate-50/50"
                (click)="fileInput.click()"
              >
                <div class="text-3xl mb-1">📷</div>
                <p class="text-xs font-bold text-indigo-600">Click or tap to capture/upload photo</p>
                <p class="text-[11px] text-slate-400 mt-0.5">JPEG, PNG or WEBP up to 5MB</p>
                <input
                  #fileInput
                  type="file"
                  id="proof-file-input"
                  accept="image/jpeg,image/png,image/webp"
                  (change)="onFileSelected($event)"
                  class="hidden"
                />
              </div>

              <!-- Image Preview -->
              <div *ngIf="proofImagePreview" class="relative rounded-xl overflow-hidden border border-slate-200 bg-slate-50">
                <img
                  [src]="proofImagePreview"
                  alt="Proof preview"
                  class="w-full h-48 object-cover"
                />
                <button
                  type="button"
                  (click)="clearProofFile()"
                  class="absolute top-2 right-2 px-2.5 py-1 bg-slate-900/70 hover:bg-slate-900 text-white rounded-lg text-xs font-bold transition-colors"
                >
                  ✕ Remove
                </button>
              </div>

              <p *ngIf="fileError()" class="text-xs text-rose-600 mt-1 font-medium">
                {{ fileError() }}
              </p>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center justify-end gap-2">
            <button
              type="button"
              (click)="closeProofModal()"
              class="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-100 text-xs font-semibold"
            >
              Cancel
            </button>
            <button
              type="button"
              id="submit-proof-btn"
              (click)="submitProof()"
              [disabled]="isSubmittingProof || !isProofFormValid()"
              class="px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-xs font-bold shadow-sm shadow-emerald-200 transition-all flex items-center gap-1.5"
            >
              <span *ngIf="isSubmittingProof" class="animate-spin">⏳</span>
              <span>Confirm & Deliver</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Failure Modal Dialog -->
      <div
        *ngIf="selectedDeliveryForFailure()"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in"
      >
        <div
          class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md overflow-hidden transform transition-all"
        >
          <!-- Modal Header -->
          <div class="p-5 border-b border-slate-100 flex items-center justify-between bg-rose-50/50">
            <div>
              <h2 class="text-lg font-bold text-slate-900 flex items-center gap-2">
                <span>⚠️</span> Record Failed Delivery Attempt
              </h2>
              <p class="text-xs text-slate-500 font-mono">
                Tracking: {{ selectedDeliveryForFailure()?.trackingNumber }}
              </p>
            </div>
            <button
              type="button"
              id="close-fail-modal"
              (click)="closeFailModal()"
              class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 hover:text-slate-800 flex items-center justify-center text-sm font-bold transition-colors"
            >
              ✕
            </button>
          </div>

          <!-- Modal Body -->
          <div class="p-5 space-y-4">
            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Reason Category *
              </label>
              <select
                id="fail-reason-select"
                [(ngModel)]="failureReasonCategory"
                class="w-full px-3.5 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 bg-white"
              >
                <option value="RECIPIENT_UNAVAILABLE">Recipient unavailable / phone unreachable</option>
                <option value="INCORRECT_ADDRESS">Incorrect address / premises not found</option>
                <option value="CUSTOMER_REJECTED">Customer refused delivery</option>
                <option value="ACCESS_RESTRICTED">Premises closed / access restricted</option>
                <option value="OTHER">Other reason (describe below)</option>
              </select>
            </div>

            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Additional Notes
              </label>
              <textarea
                id="fail-notes"
                [(ngModel)]="failureNotes"
                rows="3"
                placeholder="Details of the delivery attempt..."
                class="w-full px-3.5 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
              ></textarea>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center justify-end gap-2">
            <button
              type="button"
              (click)="closeFailModal()"
              class="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-100 text-xs font-semibold"
            >
              Cancel
            </button>
            <button
              type="button"
              id="submit-fail-btn"
              (click)="submitFailure()"
              [disabled]="isSubmittingFailure || (failureReasonCategory === 'OTHER' && !failureNotes.trim())"
              class="px-5 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 disabled:opacity-50 text-white text-xs font-bold shadow-sm shadow-rose-200 transition-all flex items-center gap-1.5"
            >
              <span *ngIf="isSubmittingFailure" class="animate-spin">⏳</span>
              <span>Save Failed Attempt</span>
            </button>
          </div>
        </div>
      </div>

      <!-- View Proof Modal Dialog -->
      <div
        *ngIf="selectedDeliveryForProofView()"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in"
      >
        <div
          class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-lg overflow-hidden transform transition-all"
        >
          <!-- Modal Header -->
          <div class="p-5 border-b border-slate-100 flex items-center justify-between bg-emerald-50/50">
            <div>
              <h2 class="text-lg font-bold text-slate-900 flex items-center gap-2">
                <span>📸</span> Delivery Proof Details
              </h2>
              <p class="text-xs text-slate-500 font-mono">
                Tracking: {{ selectedDeliveryForProofView()?.trackingNumber }}
              </p>
            </div>
            <button
              type="button"
              id="close-proof-view-modal"
              (click)="closeProofViewModal()"
              class="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 hover:text-slate-800 flex items-center justify-center text-sm font-bold transition-colors"
            >
              ✕
            </button>
          </div>

          <!-- Modal Body -->
          <div class="p-5 space-y-4">
            <div class="bg-slate-50 rounded-xl p-3.5 border border-slate-200 text-xs space-y-1.5">
              <p><span class="font-bold text-slate-700">Recipient Name:</span> {{ selectedDeliveryForProofView()?.proof?.recipientName }}</p>
              <p><span class="font-bold text-slate-700">Uploaded At:</span> {{ selectedDeliveryForProofView()?.proof?.uploadedAt | date:'medium' }}</p>
            </div>

            <div class="relative rounded-xl overflow-hidden border border-slate-200 bg-slate-100 min-h-[160px] flex items-center justify-center">
              <div *ngIf="isLoadingProofImage()" class="text-xs text-slate-500 font-semibold flex items-center gap-2">
                <span class="animate-spin">🔄</span> Loading authorized proof photo...
              </div>
              <img
                *ngIf="!isLoadingProofImage() && viewProofImageUrl()"
                [src]="viewProofImageUrl()"
                alt="Delivery proof"
                id="view-proof-image"
                class="w-full max-h-72 object-contain bg-slate-950"
              />
              <p *ngIf="!isLoadingProofImage() && !viewProofImageUrl()" class="text-xs text-slate-400">
                Photo preview not available.
              </p>
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center justify-end">
            <button
              type="button"
              (click)="closeProofViewModal()"
              class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-900 text-white text-xs font-semibold"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class CourierComponent implements OnInit {
  private courierService = inject(CourierService);

  readonly stats = this.courierService.stats;
  readonly profile = this.courierService.profile;
  readonly isLoading = this.courierService.isLoading;
  readonly errorMessage = this.courierService.error;

  activeFilter = signal<FilterTab>('ALL');
  searchQuery = signal<string>('');
  toastMessage = signal<string>('');

  // Pending actions state per parcelId
  private pendingActions = signal<Set<string>>(new Set<string>());

  // Modals state
  selectedDeliveryForProof = signal<CourierDelivery | null>(null);
  proofRecipientName = '';
  proofDeliveryNotes = '';
  selectedFile: File | null = null;
  proofImagePreview: string | null = null;
  fileError = signal<string>('');
  isSubmittingProof = false;

  selectedDeliveryForFailure = signal<CourierDelivery | null>(null);
  failureReasonCategory: FailureReasonCode = 'RECIPIENT_UNAVAILABLE';
  failureNotes = '';
  isSubmittingFailure = false;

  selectedDeliveryForProofView = signal<CourierDelivery | null>(null);
  viewProofImageUrl = signal<string | null>(null);
  isLoadingProofImage = signal<boolean>(false);

  ngOnInit(): void {
    this.refreshDeliveries();
  }

  refreshDeliveries(): void {
    this.courierService.loadDeliveries().subscribe({
      error: (err) => {
        // Handled in service signal
      },
    });
  }

  isActionPending(parcelId: string): boolean {
    return this.pendingActions().has(parcelId);
  }

  private setActionPending(parcelId: string, pending: boolean): void {
    this.pendingActions.update((set) => {
      const copy = new Set(set);
      if (pending) {
        copy.add(parcelId);
      } else {
        copy.delete(parcelId);
      }
      return copy;
    });
  }

  // Filtered list
  readonly filteredDeliveries = computed(() => {
    let list = this.courierService.deliveries();
    const filter = this.activeFilter();

    if (filter === 'ACTIVE') {
      list = list.filter((d) =>
        ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(d.status)
      );
    } else if (filter === 'DELIVERED') {
      list = list.filter((d) => d.status === 'DELIVERED');
    } else if (filter === 'FAILED') {
      list = list.filter((d) => d.status === 'FAILED' || d.status === 'FAILED_DELIVERY');
    }

    const q = this.searchQuery().toLowerCase().trim();
    if (q) {
      list = list.filter(
        (d) =>
          d.trackingNumber.toLowerCase().includes(q) ||
          d.recipientName.toLowerCase().includes(q) ||
          (d.dropoffAddress && d.dropoffAddress.toLowerCase().includes(q)) ||
          (d.recipientAddress?.city && d.recipientAddress.city.toLowerCase().includes(q)) ||
          (d.recipientAddress?.line1 && d.recipientAddress.line1.toLowerCase().includes(q))
      );
    }

    return list;
  });

  getStatusLabel(status: DeliveryStatus): string {
    switch (status) {
      case 'CREATED':
      case 'ASSIGNED':
        return 'To Pick Up';
      case 'PICKED_UP':
        return 'Picked Up';
      case 'IN_TRANSIT':
        return 'In Transit';
      case 'OUT_FOR_DELIVERY':
        return 'Out for Delivery';
      case 'DELIVERED':
        return 'Delivered';
      case 'FAILED':
      case 'FAILED_DELIVERY':
        return 'Failed';
      default:
        return status;
    }
  }

  getStatusBadgeClass(status: DeliveryStatus): string {
    switch (status) {
      case 'CREATED':
      case 'ASSIGNED':
        return 'bg-amber-100 text-amber-800 border border-amber-200';
      case 'PICKED_UP':
        return 'bg-blue-100 text-blue-800 border border-blue-200';
      case 'IN_TRANSIT':
        return 'bg-indigo-100 text-indigo-800 border border-indigo-200';
      case 'OUT_FOR_DELIVERY':
        return 'bg-purple-100 text-purple-800 border border-purple-200';
      case 'DELIVERED':
        return 'bg-emerald-100 text-emerald-800 border border-emerald-200';
      case 'FAILED':
      case 'FAILED_DELIVERY':
        return 'bg-rose-100 text-rose-800 border border-rose-200';
      default:
        return 'bg-slate-100 text-slate-800 border border-slate-200';
    }
  }

  advanceStatus(delivery: CourierDelivery, nextStatus: DeliveryStatus): void {
    if (this.isActionPending(delivery.parcelId)) return;

    this.setActionPending(delivery.parcelId, true);
    const idempotencyKey = `ui-${delivery.parcelId}-${nextStatus}-${Date.now()}`;

    this.courierService.updateStatus(delivery.parcelId, { status: nextStatus, idempotencyKey }).subscribe({
      next: (res) => {
        this.setActionPending(delivery.parcelId, false);
        this.showToast(`Updated ${delivery.trackingNumber} to ${this.getStatusLabel(nextStatus)}`);
      },
      error: (err) => {
        this.setActionPending(delivery.parcelId, false);
        const msg = err?.error?.message || err?.message || 'Status update failed';
        this.showToast(`Error: ${msg}`);
      },
    });
  }

  openProofModal(delivery: CourierDelivery): void {
    this.selectedDeliveryForProof.set(delivery);
    this.proofRecipientName = delivery.recipientName;
    this.proofDeliveryNotes = '';
    this.selectedFile = null;
    this.proofImagePreview = null;
    this.fileError.set('');
  }

  closeProofModal(): void {
    this.selectedDeliveryForProof.set(null);
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files.length > 0) {
      const file = target.files[0];
      const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];

      if (!allowedTypes.includes(file.type)) {
        this.fileError.set('Only JPEG, PNG, and WEBP images are supported.');
        return;
      }

      if (file.size > 5 * 1024 * 1024) {
        this.fileError.set('File size exceeds 5MB limit.');
        return;
      }

      this.fileError.set('');
      this.selectedFile = file;

      const reader = new FileReader();
      reader.onload = () => {
        this.proofImagePreview = reader.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  clearProofFile(): void {
    this.selectedFile = null;
    this.proofImagePreview = null;
    this.fileError.set('');
  }

  isProofFormValid(): boolean {
    return !!this.proofRecipientName.trim() || !!this.selectedFile;
  }

  submitProof(): void {
    const delivery = this.selectedDeliveryForProof();
    if (!delivery) return;

    this.isSubmittingProof = true;
    const parcelId = delivery.parcelId;
    const recipientName = this.proofRecipientName.trim();
    const notes = this.proofDeliveryNotes.trim();

    // If file is attached, upload proof first, then mark DELIVERED
    if (this.selectedFile) {
      this.courierService
        .uploadProof(parcelId, {
          file: this.selectedFile,
          recipientName,
          notes,
        })
        .subscribe({
          next: () => {
            this.completeDeliveryStatus(parcelId, delivery.trackingNumber, recipientName);
          },
          error: (err) => {
            this.isSubmittingProof = false;
            const msg = err?.error?.message || err?.message || 'Proof upload failed';
            this.fileError.set(msg);
          },
        });
    } else {
      // Recipient name proof only
      this.completeDeliveryStatus(parcelId, delivery.trackingNumber, recipientName);
    }
  }

  private completeDeliveryStatus(parcelId: string, trackingNumber: string, recipientName: string): void {
    const idempotencyKey = `proof-del-${parcelId}-${Date.now()}`;
    this.courierService
      .updateStatus(parcelId, {
        status: 'DELIVERED',
        recipientName,
        idempotencyKey,
      })
      .subscribe({
        next: () => {
          this.isSubmittingProof = false;
          this.closeProofModal();
          this.showToast(`Delivery completed & proof recorded for ${trackingNumber}!`);
        },
        error: (err) => {
          this.isSubmittingProof = false;
          const msg = err?.error?.message || err?.message || 'Failed to complete delivery status';
          this.fileError.set(msg);
        },
      });
  }

  openFailModal(delivery: CourierDelivery): void {
    this.selectedDeliveryForFailure.set(delivery);
    this.failureReasonCategory = 'RECIPIENT_UNAVAILABLE';
    this.failureNotes = '';
  }

  closeFailModal(): void {
    this.selectedDeliveryForFailure.set(null);
  }

  submitFailure(): void {
    const delivery = this.selectedDeliveryForFailure();
    if (!delivery || this.isSubmittingFailure) return;

    if (this.failureReasonCategory === 'OTHER' && !this.failureNotes.trim()) {
      this.showToast('Notes are required when the reason is Other.');
      return;
    }

    this.isSubmittingFailure = true;
    this.courierService
      .reportFailedDelivery(delivery.parcelId, {
        reasonCode: this.failureReasonCategory,
        note: this.failureNotes.trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.isSubmittingFailure = false;
          this.closeFailModal();
          this.showToast(`Marked ${delivery.trackingNumber} as delivery attempt failed.`);
        },
        error: (err) => {
          this.isSubmittingFailure = false;
          const msg = err?.error?.message || err?.message || 'Failed to update failure status';
          this.showToast(`Error: ${msg}`);
        },
      });
  }

  viewProofDetail(delivery: CourierDelivery): void {
    if (!delivery.proof) return;
    this.selectedDeliveryForProofView.set(delivery);
    this.viewProofImageUrl.set(null);

    const proof = delivery.proof as any;
    const proofId = proof.id || proof.proofId;
    if (proofId) {
      this.isLoadingProofImage.set(true);
      this.courierService.getProofPhoto(proofId).subscribe({
        next: (blob) => {
          this.isLoadingProofImage.set(false);
          if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
            try {
              this.viewProofImageUrl.set(URL.createObjectURL(blob));
              return;
            } catch {
              // fallback to reader or proofUrl
            }
          }
          const reader = new FileReader();
          reader.onload = () => {
            this.viewProofImageUrl.set(reader.result as string);
          };
          reader.readAsDataURL(blob);
          if (proof.proofUrl) {
            this.viewProofImageUrl.set(proof.proofUrl);
          }
        },
        error: () => {
          this.isLoadingProofImage.set(false);
          if (proof.proofUrl) {
            this.viewProofImageUrl.set(proof.proofUrl);
          }
        },
      });
    } else if (proof.proofUrl) {
      this.viewProofImageUrl.set(proof.proofUrl);
    }
  }

  closeProofViewModal(): void {
    const url = this.viewProofImageUrl();
    if (url && url.startsWith('blob:') && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(url);
    }
    this.selectedDeliveryForProofView.set(null);
    this.viewProofImageUrl.set(null);
  }

  private showToast(msg: string): void {
    this.toastMessage.set(msg);
    setTimeout(() => {
      if (this.toastMessage() === msg) {
        this.toastMessage.set('');
      }
    }, 4000);
  }
}
