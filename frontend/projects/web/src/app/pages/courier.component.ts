import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CourierDelivery,
  CourierStats,
  DeliveryStatus,
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
              Harsha Kumarasamy (ID: courier-harsha-01) • Colombo Central Zone
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
              {{ stats().assigned + stats().pickedUp + stats().inTransit }}
            </p>
          </div>
          <div
            class="px-3.5 py-2 rounded-xl bg-emerald-50 border border-emerald-200 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-emerald-600">Delivered</p>
            <p class="text-base font-extrabold text-emerald-700">{{ stats().delivered }}</p>
          </div>
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
            Active ({{ stats().assigned + stats().pickedUp + stats().inTransit }})
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
            [(ngModel)]="searchQuery"
            placeholder="Search tracking, recipient, city..."
            class="w-full pl-9 pr-4 py-2 bg-white rounded-xl border border-slate-200 text-xs sm:text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-sm transition-all"
          />
          <span class="absolute left-3 top-2.5 text-slate-400 text-sm">🔍</span>
          <button
            *ngIf="searchQuery"
            type="button"
            (click)="searchQuery = ''"
            class="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 text-xs"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- Toast Notification Alert -->
      <div
        *ngIf="toastMessage()"
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

      <!-- Deliveries List (Cards) -->
      <div class="space-y-4">
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
              <span *ngIf="delivery.timeSlot" class="font-medium bg-slate-100 px-2 py-0.5 rounded-md">
                ⏰ {{ delivery.timeSlot }}
              </span>
              <span class="font-medium bg-slate-100 px-2 py-0.5 rounded-md">
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
                    {{ delivery.recipientAddress.line1 }}
                    <span *ngIf="delivery.recipientAddress.line2">, {{ delivery.recipientAddress.line2 }}</span>
                  </p>
                  <p class="text-xs text-slate-500">
                    {{ delivery.recipientAddress.city }}, {{ delivery.recipientAddress.district }}
                    <span *ngIf="delivery.recipientAddress.postalCode">({{ delivery.recipientAddress.postalCode }})</span>
                  </p>
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
                *ngIf="delivery.status === 'FAILED_DELIVERY' && delivery.failureReason"
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
                      Received by {{ delivery.proof.recipientName }} • {{ delivery.proof.fileName }}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  (click)="viewProofDetail(delivery)"
                  class="px-2.5 py-1 bg-white rounded-lg border border-emerald-300 font-semibold text-emerald-800 hover:bg-emerald-100 text-[11px] transition-colors"
                >
                  View Proof
                </button>
              </div>
            </div>

            <!-- Sender / Origin Info & Stepper -->
            <div class="md:col-span-5 flex flex-col justify-between border-t md:border-t-0 md:border-l border-slate-100 md:pl-4 pt-3 md:pt-0 space-y-3">
              <div class="text-xs text-slate-500">
                <p class="text-[11px] uppercase font-bold text-slate-400">Pickup Origin</p>
                <p class="font-semibold text-slate-700 mt-0.5">{{ delivery.senderName }}</p>
                <p>{{ delivery.senderAddress.line1 }}, {{ delivery.senderAddress.city }}</p>
              </div>

              <!-- Action Buttons Depending on State -->
              <div class="pt-2 space-y-2">
                <!-- If ASSIGNED -> Can Mark as Picked Up -->
                <button
                  *ngIf="delivery.status === 'ASSIGNED'"
                  type="button"
                  [id]="'pickup-btn-' + delivery.parcelId"
                  (click)="advanceStatus(delivery, 'PICKED_UP')"
                  class="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs sm:text-sm shadow-sm shadow-indigo-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                >
                  <span>📦</span> Confirm Picked Up
                </button>

                <!-- If PICKED_UP -> Can Start Delivery (In Transit) -->
                <button
                  *ngIf="delivery.status === 'PICKED_UP'"
                  type="button"
                  [id]="'transit-btn-' + delivery.parcelId"
                  (click)="advanceStatus(delivery, 'IN_TRANSIT')"
                  class="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs sm:text-sm shadow-sm shadow-blue-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                >
                  <span>🚚</span> Start Delivery (In Transit)
                </button>

                <!-- If IN_TRANSIT -> Can Deliver (with Proof) or Fail -->
                <div *ngIf="delivery.status === 'IN_TRANSIT'" class="flex flex-col gap-2">
                  <button
                    type="button"
                    [id]="'deliver-btn-' + delivery.parcelId"
                    (click)="openProofModal(delivery)"
                    class="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs sm:text-sm shadow-sm shadow-emerald-200 hover:shadow-md transition-all flex items-center justify-center gap-1.5"
                  >
                    <span>📸</span> Complete & Upload Proof
                  </button>
                  <button
                    type="button"
                    [id]="'fail-btn-' + delivery.parcelId"
                    (click)="openFailModal(delivery)"
                    class="w-full py-2 px-3 rounded-xl bg-slate-100 hover:bg-rose-50 text-slate-700 hover:text-rose-700 font-semibold text-xs border border-slate-200 hover:border-rose-200 transition-colors flex items-center justify-center gap-1"
                  >
                    <span>⚠️</span> Mark Delivery Attempt Failed
                  </button>
                </div>

                <!-- If FAILED -> Can Retry In Transit -->
                <button
                  *ngIf="delivery.status === 'FAILED_DELIVERY'"
                  type="button"
                  [id]="'retry-btn-' + delivery.parcelId"
                  (click)="advanceStatus(delivery, 'IN_TRANSIT')"
                  class="w-full py-2 px-3 rounded-xl bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs shadow-sm transition-all flex items-center justify-center gap-1.5"
                >
                  <span>🔄</span> Re-attempt Delivery
                </button>

                <!-- If DELIVERED -> Done Badge -->
                <div
                  *ngIf="delivery.status === 'DELIVERED'"
                  class="w-full py-2 px-3 rounded-xl bg-emerald-50 text-emerald-800 text-xs font-bold text-center border border-emerald-200"
                >
                  ✅ Completed & Confirmed
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div
          *ngIf="filteredDeliveries().length === 0"
          class="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center"
        >
          <div class="text-4xl mb-3">📦</div>
          <h3 class="text-base font-bold text-slate-800">No deliveries found</h3>
          <p class="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
            No parcels match the current filter or search criteria.
          </p>
          <button
            type="button"
            (click)="activeFilter.set('ALL'); searchQuery = ''"
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
                Photo Proof (JPEG, PNG, WEBP - Max 5MB) *
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
                  alt="Delivery Proof Preview"
                  class="w-full h-44 object-cover"
                />
                <div class="absolute inset-x-0 bottom-0 p-2 bg-gradient-to-t from-slate-900/80 to-transparent flex items-center justify-between text-white text-xs">
                  <span class="truncate">{{ selectedFile?.name }} ({{ formatFileSize(selectedFile?.size || 0) }})</span>
                  <button
                    type="button"
                    (click)="clearProofFile()"
                    class="bg-rose-600 hover:bg-rose-700 px-2 py-0.5 rounded text-[11px] font-bold"
                  >
                    Remove
                  </button>
                </div>
              </div>

              <p *ngIf="fileError()" class="text-xs text-rose-600 font-semibold mt-1">
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
              [disabled]="isSubmittingProof || !isProofFormValid()"
              (click)="submitProof()"
              class="px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-xs font-bold shadow-sm shadow-emerald-200 flex items-center gap-1.5 transition-all"
            >
              <span *ngIf="isSubmittingProof">Uploading...</span>
              <span *ngIf="!isSubmittingProof">Confirm & Deliver</span>
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
              <h2 class="text-lg font-bold text-rose-900 flex items-center gap-2">
                <span>⚠️</span> Delivery Attempt Failed
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

          <!-- Modal Body Form -->
          <div class="p-5 space-y-4">
            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Select Reason *
              </label>
              <select
                id="failure-reason-select"
                [(ngModel)]="failureReasonCategory"
                class="w-full px-3.5 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
              >
                <option value="RECIPIENT_UNAVAILABLE">Recipient unavailable</option>
                <option value="INCORRECT_ADDRESS">Incorrect address</option>
                <option value="CUSTOMER_REJECTED">Customer rejected</option>
                <option value="ACCESS_RESTRICTED">Access restricted</option>
                <option value="OTHER">Other</option>
              </select>
            </div>

            <div>
              <label class="block text-xs font-bold uppercase text-slate-600 mb-1">
                Additional Notes {{ failureReasonCategory === 'OTHER' ? '*' : '' }}
              </label>
              <textarea
                id="failure-notes"
                [(ngModel)]="failureNotes"
                rows="2"
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
              [disabled]="failureReasonCategory === 'OTHER' && !failureNotes.trim()"
              class="px-5 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold shadow-sm shadow-rose-200 transition-all"
            >
              Save Failed Attempt
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class CourierComponent {
  private courierService = inject(CourierService);

  readonly stats = this.courierService.stats;
  activeFilter = signal<FilterTab>('ALL');
  searchQuery = '';
  toastMessage = signal<string>('');

  // Modals state
  selectedDeliveryForProof = signal<CourierDelivery | null>(null);
  proofRecipientName = '';
  proofDeliveryNotes = '';
  selectedFile: File | null = null;
  proofImagePreview: string | null = null;
  fileError = signal<string>('');
  isSubmittingProof = false;

  selectedDeliveryForFailure = signal<CourierDelivery | null>(null);
  failureReasonCategory = 'RECIPIENT_UNAVAILABLE';
  failureNotes = '';

  // Filtered list
  readonly filteredDeliveries = computed(() => {
    let list = this.courierService.deliveries();
    const filter = this.activeFilter();

    if (filter === 'ACTIVE') {
      list = list.filter((d) => ['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'].includes(d.status));
    } else if (filter === 'DELIVERED') {
      list = list.filter((d) => d.status === 'DELIVERED');
    } else if (filter === 'FAILED') {
      list = list.filter((d) => d.status === 'FAILED_DELIVERY');
    }

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase().trim();
      list = list.filter(
        (d) =>
          d.trackingNumber.toLowerCase().includes(q) ||
          d.recipientName.toLowerCase().includes(q) ||
          d.recipientAddress.city.toLowerCase().includes(q) ||
          d.recipientAddress.line1.toLowerCase().includes(q)
      );
    }

    return list;
  });

  getStatusLabel(status: DeliveryStatus): string {
    switch (status) {
      case 'ASSIGNED':
        return 'To Pick Up';
      case 'PICKED_UP':
        return 'Picked Up';
      case 'IN_TRANSIT':
        return 'In Transit';
      case 'DELIVERED':
        return 'Delivered';
      case 'FAILED_DELIVERY':
        return 'Failed';
      default:
        return status;
    }
  }

  getStatusBadgeClass(status: DeliveryStatus): string {
    switch (status) {
      case 'ASSIGNED':
        return 'bg-amber-100 text-amber-800 border border-amber-200';
      case 'PICKED_UP':
        return 'bg-blue-100 text-blue-800 border border-blue-200';
      case 'IN_TRANSIT':
        return 'bg-indigo-100 text-indigo-800 border border-indigo-200';
      case 'DELIVERED':
        return 'bg-emerald-100 text-emerald-800 border border-emerald-200';
      case 'FAILED_DELIVERY':
        return 'bg-rose-100 text-rose-800 border border-rose-200';
      default:
        return 'bg-slate-100 text-slate-800 border border-slate-200';
    }
  }

  advanceStatus(delivery: CourierDelivery, nextStatus: DeliveryStatus): void {
    const idempotencyKey = `ui-${delivery.parcelId}-${nextStatus}-${Date.now()}`;
    this.courierService.updateStatus(delivery.parcelId, { status: nextStatus, idempotencyKey }).subscribe({
      next: (res) => {
        this.showToast(`Updated ${delivery.trackingNumber} to ${this.getStatusLabel(nextStatus)}`);
      },
      error: (err) => {
        this.showToast(`Error: ${err.message}`);
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

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  isProofFormValid(): boolean {
    return !!this.proofRecipientName.trim() && !!this.selectedFile;
  }

  submitProof(): void {
    const delivery = this.selectedDeliveryForProof();
    if (!delivery || !this.selectedFile) return;

    this.isSubmittingProof = true;
    this.courierService
      .uploadProof(delivery.parcelId, {
        file: this.selectedFile,
        recipientName: this.proofRecipientName,
        deliveryNotes: this.proofDeliveryNotes,
        idempotencyKey: `proof-${delivery.parcelId}-${Date.now()}`,
      })
      .subscribe({
        next: () => {
          this.isSubmittingProof = false;
          this.closeProofModal();
          this.showToast(`Proof captured & delivery marked completed for ${delivery.trackingNumber}!`);
        },
        error: (err) => {
          this.isSubmittingProof = false;
          this.fileError.set(err.message);
        },
      });
  }

  openFailModal(delivery: CourierDelivery): void {
    this.selectedDeliveryForFailure.set(delivery);
    this.failureReasonCategory = 'Recipient unavailable / phone unreachable';
    this.failureNotes = '';
  }

  closeFailModal(): void {
    this.selectedDeliveryForFailure.set(null);
  }

  submitFailure(): void {
    const delivery = this.selectedDeliveryForFailure();
    if (!delivery) return;

    if (this.failureReasonCategory === 'OTHER' && !this.failureNotes.trim()) {
      this.showToast('Notes are required when the reason is Other.');
      return;
    }

    const fullReason = this.failureNotes.trim()
      ? `${this.failureReasonCategory}: ${this.failureNotes.trim()}`
      : this.failureReasonCategory;

    this.courierService
      .updateStatus(delivery.parcelId, {
        status: 'FAILED_DELIVERY',
        reason: fullReason,
        idempotencyKey: `fail-${delivery.parcelId}-${Date.now()}`,
      })
      .subscribe({
        next: () => {
          this.closeFailModal();
          this.showToast(`Marked ${delivery.trackingNumber} as delivery failed.`);
        },
        error: (err) => {
          this.showToast(`Error: ${err.message}`);
        },
      });
  }

  viewProofDetail(delivery: CourierDelivery): void {
    if (delivery.proof) {
      alert(
        `Delivery Proof for ${delivery.trackingNumber}\nRecipient: ${delivery.proof.recipientName}\nFile: ${delivery.proof.fileName}\nNotes: ${delivery.proof.deliveryNotes || 'None'}\nUploaded: ${new Date(delivery.proof.uploadedAt).toLocaleString()}`
      );
    }
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
