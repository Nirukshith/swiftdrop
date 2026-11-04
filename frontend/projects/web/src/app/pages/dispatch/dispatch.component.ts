import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CourierWorkloadItem,
  DispatchParcelSummary,
} from './dispatch.model';
import { DispatchService } from './dispatch.service';

type DispatchTab = 'queue' | 'workload';

@Component({
  selector: 'app-dispatch',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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
            ⚡
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight">
                Dispatcher Operations Portal
              </h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200"
              >
                Ops Hub • Port 8083
              </span>
            </div>
            <p class="text-xs sm:text-sm text-slate-500 font-medium">
              SwiftDrop Live Queue Management & Courier Workload Board
            </p>
          </div>
        </div>

        <!-- Live KPI Summary Chips -->
        <div class="flex items-center gap-2.5 overflow-x-auto pb-1 md:pb-0">
          <div
            class="px-4 py-2 rounded-xl bg-amber-50/80 border border-amber-200/80 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-amber-600 tracking-wider">Unassigned Queue</p>
            <p class="text-lg font-black text-amber-800">{{ dispatchService.unassignedCount() }}</p>
          </div>
          <div
            class="px-4 py-2 rounded-xl bg-emerald-50/80 border border-emerald-200/80 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-emerald-600 tracking-wider">Online Couriers</p>
            <p class="text-lg font-black text-emerald-800">
              {{ dispatchService.availableCouriersCount() }} / {{ dispatchService.couriers().length }}
            </p>
          </div>
          <div
            class="px-4 py-2 rounded-xl bg-indigo-50/80 border border-indigo-200/80 text-center flex-1 sm:flex-initial"
          >
            <p class="text-[11px] uppercase font-bold text-indigo-600 tracking-wider">Active Deliveries</p>
            <p class="text-lg font-black text-indigo-800">{{ dispatchService.totalWorkloadActive() }}</p>
          </div>
        </div>
      </div>

      <!-- Navigation Tabs -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div class="inline-flex rounded-xl bg-slate-100 p-1.5 border border-slate-200 text-sm font-semibold">
          <button
            type="button"
            id="tab-queue-btn"
            (click)="activeTab.set('queue')"
            [class.bg-white]="activeTab() === 'queue'"
            [class.shadow-sm]="activeTab() === 'queue'"
            [class.text-indigo-600]="activeTab() === 'queue'"
            [class.text-slate-600]="activeTab() !== 'queue'"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2"
          >
            <span>📋</span>
            <span>Unassigned Queue</span>
            <span
              class="ml-1 px-1.5 py-0.2 rounded-full text-xs font-bold"
              [class.bg-indigo-100]="activeTab() === 'queue'"
              [class.text-indigo-700]="activeTab() === 'queue'"
              [class.bg-slate-200]="activeTab() !== 'queue'"
              [class.text-slate-700]="activeTab() !== 'queue'"
            >
              {{ dispatchService.unassignedCount() }}
            </span>
          </button>

          <button
            type="button"
            id="tab-workload-btn"
            (click)="activeTab.set('workload')"
            [class.bg-white]="activeTab() === 'workload'"
            [class.shadow-sm]="activeTab() === 'workload'"
            [class.text-indigo-600]="activeTab() === 'workload'"
            [class.text-slate-600]="activeTab() !== 'workload'"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2"
          >
            <span>📊</span>
            <span>Workload Board</span>
          </button>

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
            id="tab-pricing-link"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2 text-slate-600 hover:text-indigo-600"
          >
            <span>🏷️</span>
            <span>Pricing Rules</span>
          </a>
        </div>
      </div>

      <!-- Toast Feedback -->
      <div
        *ngIf="toastMessage()"
        class="fixed bottom-6 right-6 z-50 flex items-center gap-3 px-5 py-3.5 rounded-xl shadow-lg border text-sm font-semibold transition-all transform animate-fade-in"
        [class.bg-emerald-50]="toastType() === 'success'"
        [class.border-emerald-300]="toastType() === 'success'"
        [class.text-emerald-800]="toastType() === 'success'"
        [class.bg-rose-50]="toastType() === 'error'"
        [class.border-rose-300]="toastType() === 'error'"
        [class.text-rose-800]="toastType() === 'error'"
      >
        <span>{{ toastType() === 'success' ? '✅' : '⚠️' }}</span>
        <span>{{ toastMessage() }}</span>
        <button
          type="button"
          (click)="toastMessage.set(null)"
          class="ml-2 text-slate-400 hover:text-slate-600"
        >
          ✕
        </button>
      </div>

      <!-- ================================================================= -->
      <!-- TAB 1: UNASSIGNED QUEUE -->
      <!-- ================================================================= -->
      <div *ngIf="activeTab() === 'queue'" class="space-y-4">
        <!-- Queue Filters & Search -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div class="flex items-center gap-3 flex-1">
            <div class="relative flex-1 max-w-sm">
              <input
                type="text"
                id="queue-search-input"
                [ngModel]="searchQuery()"
                (ngModelChange)="searchQuery.set($event)"
                placeholder="Search by tracking # or address..."
                class="w-full pl-9 pr-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <span class="absolute left-3 top-2.5 text-slate-400 text-sm">🔍</span>
            </div>

            <select
              id="queue-zone-filter"
              [ngModel]="selectedZone()"
              (ngModelChange)="onQueueZoneChange($event)"
              class="px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
            >
              <option value="">All Delivery Zones</option>
              <option value="ZONE-COLOMBO-NORTH">Colombo North</option>
              <option value="ZONE-COLOMBO-SOUTH">Colombo South</option>
              <option value="ZONE-KANDY">Kandy Central</option>
            </select>
          </div>

          <div class="flex items-center gap-3">
            <button
              type="button"
              id="refresh-queue-btn"
              (click)="retryLoadQueue()"
              class="px-3 py-1.5 rounded-xl border border-slate-200 text-slate-600 hover:text-indigo-600 hover:bg-slate-50 text-xs font-semibold transition-colors flex items-center gap-1.5"
            >
              <span>🔄</span>
              <span>Refresh</span>
            </button>
            <p class="text-xs text-slate-500 font-medium">
              Showing <strong class="text-slate-800">{{ filteredParcels().length }}</strong> unassigned parcel(s)
            </p>
          </div>
        </div>

        <!-- Loading State -->
        <div *ngIf="dispatchService.isLoadingParcels()" class="bg-white rounded-2xl border border-slate-200 p-12 text-center space-y-3">
          <div class="inline-block animate-spin text-3xl">⏳</div>
          <p class="text-sm font-semibold text-slate-600">Loading unassigned dispatch queue...</p>
        </div>

        <!-- Error State with Retry -->
        <div *ngIf="!dispatchService.isLoadingParcels() && dispatchService.parcelsError()" class="bg-rose-50 rounded-2xl border border-rose-200 p-6 text-center space-y-3">
          <div class="text-3xl">⚠️</div>
          <h3 class="text-sm font-bold text-rose-800">Failed to Load Dispatch Queue</h3>
          <p class="text-xs text-rose-600 max-w-md mx-auto">{{ dispatchService.parcelsError() }}</p>
          <button
            type="button"
            id="retry-queue-btn"
            (click)="retryLoadQueue()"
            class="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold shadow-sm transition-colors"
          >
            Retry Loading Queue
          </button>
        </div>

        <!-- Parcels Grid / Table -->
        <div *ngIf="!dispatchService.isLoadingParcels() && !dispatchService.parcelsError()">
          <div *ngIf="filteredParcels().length > 0; else emptyQueueTemplate" class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div
              *ngFor="let parcel of filteredParcels()"
              class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 hover:border-indigo-200 transition-all space-y-4 flex flex-col justify-between"
            >
              <div>
                <div class="flex items-center justify-between gap-2">
                  <span class="font-mono text-sm font-black text-indigo-700 bg-indigo-50 px-2.5 py-1 rounded-lg border border-indigo-100">
                    {{ parcel.trackingNumber }}
                  </span>
                  <span class="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200">
                    ● {{ parcel.status }} (Unassigned)
                  </span>
                </div>

                <!-- Pickup & Dropoff Routing -->
                <div class="mt-4 space-y-2.5 text-xs text-slate-600">
                  <div class="flex items-start gap-2">
                    <span class="text-emerald-600 font-bold mt-0.5">🟢 PICKUP:</span>
                    <div>
                      <p class="font-bold text-slate-800">{{ parcel.pickupAddress || 'Address on file' }}</p>
                      <p class="text-[11px] text-slate-500">{{ parcel.pickupZone }}</p>
                    </div>
                  </div>
                  <div class="flex items-start gap-2">
                    <span class="text-indigo-600 font-bold mt-0.5">📍 DROPOFF:</span>
                    <div>
                      <p class="font-bold text-slate-800">{{ parcel.dropoffAddress || 'Address on file' }}</p>
                      <p class="text-[11px] text-slate-500">
                        {{ parcel.dropoffZone }}
                        <span *ngIf="parcel.recipientName"> • Recipient: {{ parcel.recipientName }}</span>
                        <span *ngIf="parcel.recipientPhone"> ({{ parcel.recipientPhone }})</span>
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Footer Specs & Assign Trigger -->
              <div class="pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                <div class="flex items-center gap-3 text-xs text-slate-500 font-medium">
                  <span *ngIf="parcel.weightKg !== undefined">⚖️ {{ parcel.weightKg }} kg</span>
                  <span>•</span>
                  <span>Attempts: {{ parcel.attemptCount ?? 0 }}</span>
                </div>

                <button
                  type="button"
                  [attr.id]="'assign-parcel-' + parcel.parcelId + '-btn'"
                  (click)="openAssignModal(parcel)"
                  class="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-sm transition-colors flex items-center gap-1.5"
                >
                  <span>Assign Courier</span>
                  <span>→</span>
                </button>
              </div>
            </div>
          </div>

          <ng-template #emptyQueueTemplate>
            <div class="bg-white rounded-2xl border border-slate-200 p-12 text-center space-y-3">
              <div class="text-4xl">🎉</div>
              <h3 class="text-base font-bold text-slate-800">All Parcels Assigned!</h3>
              <p class="text-xs text-slate-500 max-w-sm mx-auto">
                There are currently no parcels in the unassigned queue awaiting courier assignment.
              </p>
            </div>
          </ng-template>
        </div>
      </div>

      <!-- ================================================================= -->
      <!-- TAB 2: COURIER WORKLOAD BOARD -->
      <!-- ================================================================= -->
      <div *ngIf="activeTab() === 'workload'" class="space-y-4">
        <!-- Workload Filters -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <select
              id="workload-zone-filter"
              [ngModel]="workloadZone()"
              (ngModelChange)="onWorkloadZoneChange($event)"
              class="px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
            >
              <option value="">All Delivery Zones</option>
              <option value="ZONE-COLOMBO-NORTH">Colombo North</option>
              <option value="ZONE-COLOMBO-SOUTH">Colombo South</option>
              <option value="ZONE-KANDY">Kandy Central</option>
            </select>

            <label class="inline-flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
              <input
                type="checkbox"
                id="available-only-toggle"
                [ngModel]="availableOnly()"
                (ngModelChange)="onAvailableOnlyChange($event)"
                class="rounded text-indigo-600 focus:ring-indigo-500"
              />
              <span>Online Only</span>
            </label>
          </div>

          <div class="flex items-center gap-3">
            <button
              type="button"
              id="refresh-workload-btn"
              (click)="retryLoadWorkload()"
              class="px-3 py-1.5 rounded-xl border border-slate-200 text-slate-600 hover:text-indigo-600 hover:bg-slate-50 text-xs font-semibold transition-colors flex items-center gap-1.5"
            >
              <span>🔄</span>
              <span>Refresh</span>
            </button>
            <p class="text-xs text-slate-500 font-medium">
              Active Couriers: <strong class="text-slate-800">{{ filteredCouriers().length }}</strong>
            </p>
          </div>
        </div>

        <!-- Workload Loading State -->
        <div *ngIf="dispatchService.isLoadingCouriers()" class="bg-white rounded-2xl border border-slate-200 p-12 text-center space-y-3">
          <div class="inline-block animate-spin text-3xl">⏳</div>
          <p class="text-sm font-semibold text-slate-600">Loading courier workload board...</p>
        </div>

        <!-- Workload Error State -->
        <div *ngIf="!dispatchService.isLoadingCouriers() && dispatchService.couriersError()" class="bg-rose-50 rounded-2xl border border-rose-200 p-6 text-center space-y-3">
          <div class="text-3xl">⚠️</div>
          <h3 class="text-sm font-bold text-rose-800">Failed to Load Workload Board</h3>
          <p class="text-xs text-rose-600 max-w-md mx-auto">{{ dispatchService.couriersError() }}</p>
          <button
            type="button"
            id="retry-workload-btn"
            (click)="retryLoadWorkload()"
            class="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold shadow-sm transition-colors"
          >
            Retry Loading Workload
          </button>
        </div>

        <!-- Couriers Grid -->
        <div *ngIf="!dispatchService.isLoadingCouriers() && !dispatchService.couriersError()">
          <div *ngIf="filteredCouriers().length > 0; else emptyWorkloadTemplate" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div
              *ngFor="let courier of filteredCouriers()"
              class="bg-white rounded-2xl border border-slate-200/90 shadow-sm p-5 space-y-4 hover:border-slate-300 transition-all flex flex-col justify-between"
            >
              <div>
                <div class="flex items-start justify-between gap-2">
                  <div class="flex items-center gap-3">
                    <div
                      class="w-10 h-10 rounded-xl flex items-center justify-center font-bold text-base"
                      [class.bg-emerald-100]="courier.available"
                      [class.text-emerald-700]="courier.available"
                      [class.bg-slate-100]="!courier.available"
                      [class.text-slate-500]="!courier.available"
                    >
                      {{ courier.vehicleType === 'VAN' ? '🚐' : courier.vehicleType === 'THREE_WHEELER' ? '🛺' : '🏍️' }}
                    </div>
                    <div>
                      <h3 class="text-sm font-extrabold text-slate-900">{{ courier.name }}</h3>
                      <p class="text-[11px] text-slate-500 font-medium">{{ courier.phoneNumber || 'No phone' }}</p>
                    </div>
                  </div>

                  <span
                    class="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-bold"
                    [class.bg-emerald-50]="courier.available"
                    [class.text-emerald-700]="courier.available"
                    [class.border]="courier.available"
                    [class.border-emerald-200]="courier.available"
                    [class.bg-slate-100]="!courier.available"
                    [class.text-slate-600]="!courier.available"
                  >
                    {{ courier.available ? '● Online' : '○ Offline' }}
                  </span>
                </div>

                <!-- Zone & Vehicle Info -->
                <div class="mt-4 pt-3 border-t border-slate-100 space-y-1 text-xs text-slate-600">
                  <p><span class="font-semibold text-slate-400">Zone:</span> {{ courier.zone || 'Unassigned' }}</p>
                  <p><span class="font-semibold text-slate-400">Vehicle:</span> {{ courier.vehicleType || 'Standard' }}</p>
                </div>
              </div>

              <!-- Workload Counter Display & Toggle -->
              <div class="pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                <div>
                  <p class="text-[10px] uppercase font-bold text-slate-400">Active Load</p>
                  <div class="flex items-center gap-1.5 mt-0.5">
                    <span
                      class="inline-flex items-center justify-center px-2.5 py-0.5 rounded-lg text-xs font-black"
                      [class.bg-emerald-100]="courier.activeCount <= 2"
                      [class.text-emerald-800]="courier.activeCount <= 2"
                      [class.bg-amber-100]="courier.activeCount > 2 && courier.activeCount <= 4"
                      [class.text-amber-800]="courier.activeCount > 2 && courier.activeCount <= 4"
                      [class.bg-rose-100]="courier.activeCount > 4"
                      [class.text-rose-800]="courier.activeCount > 4"
                    >
                      {{ courier.activeCount }} active
                    </span>
                    <span class="text-[11px] text-slate-400 font-medium">
                      {{ courier.activeCount <= 2 ? 'Light' : courier.activeCount <= 4 ? 'Moderate' : 'Heavy' }}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  [attr.id]="'toggle-courier-' + courier.courierId + '-btn'"
                  (click)="toggleCourierAvailability(courier)"
                  class="px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors"
                  [class.border-slate-200]="courier.available"
                  [class.text-slate-600]="courier.available"
                  [class.hover:bg-slate-50]="courier.available"
                  [class.bg-emerald-600]="!courier.available"
                  [class.text-white]="!courier.available"
                  [class.hover:bg-emerald-700]="!courier.available"
                >
                  {{ courier.available ? 'Set Offline' : 'Set Online' }}
                </button>
              </div>
            </div>
          </div>

          <ng-template #emptyWorkloadTemplate>
            <div class="bg-white rounded-2xl border border-slate-200 p-12 text-center space-y-3">
              <div class="text-4xl">👷</div>
              <h3 class="text-base font-bold text-slate-800">No Couriers Found</h3>
              <p class="text-xs text-slate-500 max-w-sm mx-auto">
                No active couriers match the selected zone or availability criteria.
              </p>
            </div>
          </ng-template>
        </div>
      </div>

      <!-- ================================================================= -->
      <!-- MODAL: ASSIGN PARCEL DIALOG -->
      <!-- ================================================================= -->
      <div
        *ngIf="selectedParcelForAssign()"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-fade-in"
      >
        <div class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-lg p-6 space-y-5">
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h3 class="text-base font-extrabold text-slate-900">Assign Courier to Parcel</h3>
              <p class="text-xs text-slate-500 font-mono">{{ selectedParcelForAssign()?.trackingNumber }}</p>
            </div>
            <button
              type="button"
              [disabled]="isSubmittingAssignment()"
              (click)="selectedParcelForAssign.set(null)"
              class="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-600 hover:bg-slate-100 text-sm disabled:opacity-50"
            >
              ✕
            </button>
          </div>

          <!-- Parcel Route Card -->
          <div class="bg-slate-50 rounded-xl p-3.5 border border-slate-200/80 text-xs space-y-1.5 text-slate-700">
            <p><strong>Route:</strong> {{ selectedParcelForAssign()?.pickupZone }} → {{ selectedParcelForAssign()?.dropoffZone }}</p>
            <p><strong>Dropoff Address:</strong> {{ selectedParcelForAssign()?.dropoffAddress }}</p>
            <p *ngIf="selectedParcelForAssign()?.weightKg !== undefined"><strong>Weight:</strong> {{ selectedParcelForAssign()?.weightKg }} kg</p>
          </div>

          <!-- Select Courier -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-700 uppercase tracking-wider">
              Select Available Courier
            </label>
            <div class="max-h-56 overflow-y-auto space-y-2 pr-1">
              <div
                *ngFor="let courier of dispatchService.couriers()"
                (click)="courier.available && !isSubmittingAssignment() ? selectedCourierId.set(courier.courierId) : null"
                class="p-3 rounded-xl border flex items-center justify-between gap-3 transition-all"
                [class.cursor-pointer]="courier.available && !isSubmittingAssignment()"
                [class.cursor-not-allowed]="!courier.available || isSubmittingAssignment()"
                [class.opacity-50]="!courier.available"
                [class.border-indigo-600]="selectedCourierId() === courier.courierId"
                [class.bg-indigo-50/50]="selectedCourierId() === courier.courierId"
                [class.border-slate-200]="selectedCourierId() !== courier.courierId"
              >
                <div class="flex items-center gap-3">
                  <input
                    type="radio"
                    name="courierSelection"
                    [value]="courier.courierId"
                    [checked]="selectedCourierId() === courier.courierId"
                    [disabled]="!courier.available || isSubmittingAssignment()"
                    class="text-indigo-600 focus:ring-indigo-500"
                  />
                  <div>
                    <p class="text-xs font-bold text-slate-900">{{ courier.name }}</p>
                    <p class="text-[11px] text-slate-500">{{ courier.zone || 'Zone N/A' }} • {{ courier.vehicleType || 'Standard' }}</p>
                  </div>
                </div>

                <div class="text-right">
                  <span
                    class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-bold"
                    [class.bg-emerald-100]="courier.activeCount <= 2"
                    [class.text-emerald-800]="courier.activeCount <= 2"
                    [class.bg-amber-100]="courier.activeCount > 2"
                    [class.text-amber-800]="courier.activeCount > 2"
                  >
                    {{ courier.activeCount }} active
                  </span>
                  <p class="text-[10px] text-slate-400 mt-0.5">
                    {{ courier.available ? '● Available' : '○ Offline' }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- Dialog Actions -->
          <div class="pt-3 border-t border-slate-100 flex items-center justify-end gap-2">
            <button
              type="button"
              [disabled]="isSubmittingAssignment()"
              (click)="selectedParcelForAssign.set(null)"
              class="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100 transition-colors disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              id="confirm-assignment-btn"
              [disabled]="!selectedCourierId() || isSubmittingAssignment()"
              (click)="confirmAssignment()"
              class="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              <span *ngIf="isSubmittingAssignment()" class="animate-spin text-xs">⏳</span>
              <span>{{ isSubmittingAssignment() ? 'Assigning...' : 'Confirm Assignment' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class DispatchComponent implements OnInit {
  public dispatchService = inject(DispatchService);
  private readonly route = inject(ActivatedRoute, { optional: true });

  public activeTab = signal<DispatchTab>('queue');

  // Search & Filter Signals
  public searchQuery = signal('');
  public selectedZone = signal('');
  public workloadZone = signal('');
  public availableOnly = signal(false);

  // Modals & Submitting state
  public selectedParcelForAssign = signal<DispatchParcelSummary | null>(null);
  public selectedCourierId = signal<string>('');
  public isSubmittingAssignment = signal(false);

  // Toast state
  public toastMessage = signal<string | null>(null);
  public toastType = signal<'success' | 'error'>('success');

  constructor() {
    this.route?.queryParamMap.subscribe((params) => {
      const tab = params.get('tab');
      if (tab === 'workload') {
        this.activeTab.set('workload');
      } else {
        this.activeTab.set('queue');
      }
    });
  }

  public ngOnInit(): void {
    this.retryLoadQueue();
    this.retryLoadWorkload();
  }

  public retryLoadQueue(): void {
    this.dispatchService.loadUnassignedParcels(this.selectedZone()).subscribe({
      error: (err) => {
        // Error state signal is already updated in DispatchService
      },
    });
  }

  public retryLoadWorkload(): void {
    this.dispatchService.loadCourierWorkload(this.workloadZone(), this.availableOnly()).subscribe({
      error: (err) => {
        // Error state signal is already updated in DispatchService
      },
    });
  }

  public onQueueZoneChange(zone: string): void {
    this.selectedZone.set(zone);
    this.retryLoadQueue();
  }

  public onWorkloadZoneChange(zone: string): void {
    this.workloadZone.set(zone);
    this.retryLoadWorkload();
  }

  public onAvailableOnlyChange(availableOnly: boolean): void {
    this.availableOnly.set(availableOnly);
    this.retryLoadWorkload();
  }

  public filteredParcels = computed(() => {
    let list = this.dispatchService.unassignedParcels();
    const zone = this.selectedZone();
    if (zone) {
      list = list.filter(
        (p) => p.pickupZone === zone || p.dropoffZone === zone
      );
    }
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      list = list.filter(
        (p) =>
          p.trackingNumber.toLowerCase().includes(q) ||
          (p.pickupAddress && p.pickupAddress.toLowerCase().includes(q)) ||
          (p.dropoffAddress && p.dropoffAddress.toLowerCase().includes(q)) ||
          (p.recipientName && p.recipientName.toLowerCase().includes(q))
      );
    }
    return list;
  });

  public filteredCouriers = computed(() => {
    let list = this.dispatchService.couriers();
    const zone = this.workloadZone();
    if (zone) {
      list = list.filter((c) => c.zone === zone);
    }
    if (this.availableOnly()) {
      list = list.filter((c) => c.available);
    }
    return list;
  });

  public openAssignModal(parcel: DispatchParcelSummary): void {
    this.selectedParcelForAssign.set(parcel);
    const firstAvailable = this.dispatchService.availableCouriers()[0];
    this.selectedCourierId.set(firstAvailable ? firstAvailable.courierId : '');
    this.isSubmittingAssignment.set(false);
  }

  public confirmAssignment(): void {
    const parcel = this.selectedParcelForAssign();
    const courierId = this.selectedCourierId();
    if (!parcel || !courierId || this.isSubmittingAssignment()) {
      return;
    }

    this.isSubmittingAssignment.set(true);

    this.dispatchService.assignParcel(parcel.parcelId, courierId).subscribe({
      next: (result) => {
        const courier = this.dispatchService
          .couriers()
          .find((c) => c.courierId === courierId);
        this.showToast(
          `Parcel ${parcel.trackingNumber} assigned to ${courier?.name || 'Courier'}. Workload count updated!`,
          'success'
        );
        this.selectedParcelForAssign.set(null);
        this.selectedCourierId.set('');
        this.isSubmittingAssignment.set(false);
      },
      error: (err: any) => {
        this.isSubmittingAssignment.set(false);
        const errorMsg =
          err?.error?.message ||
          err?.message ||
          'Failed to assign parcel. Please try again.';
        this.showToast(errorMsg, 'error');

        // On 409 Conflict (e.g. parcel was already assigned by another dispatcher),
        // refresh the queue so stale records are cleaned up
        if (err?.status === 409) {
          this.retryLoadQueue();
        }
      },
    });
  }

  public toggleCourierAvailability(courier: CourierWorkloadItem): void {
    const nextStatus = !courier.available;
    this.dispatchService.updateCourierAvailability(courier.courierId, nextStatus).subscribe({
      next: () => {
        this.showToast(
          `${courier.name} is now ${nextStatus ? 'Online' : 'Offline'}.`,
          'success'
        );
      },
      error: (err: any) => {
        const msg = err?.error?.message || err?.message || 'Failed to update courier availability.';
        this.showToast(msg, 'error');
      },
    });
  }

  private showToast(message: string, type: 'success' | 'error'): void {
    this.toastMessage.set(message);
    this.toastType.set(type);
    setTimeout(() => {
      this.toastMessage.set(null);
    }, 4500);
  }
}
