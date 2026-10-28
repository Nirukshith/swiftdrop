import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CourierWorkload,
  CreateStaffPayload,
  StaffUser,
  UnassignedParcel,
} from './dispatch.model';
import { DispatchService } from './dispatch.service';

type DispatchTab = 'queue' | 'workload' | 'users';

@Component({
  selector: 'app-dispatch',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
                Dispatcher Operations & Staff Portal
              </h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200"
              >
                Ops Hub • Port 8083 / 8081
              </span>
            </div>
            <p class="text-xs sm:text-sm text-slate-500 font-medium">
              SwiftDrop Live Queue Management, Courier Workload Board & User Administration
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

      <!-- Navigation Tabs & Actions Bar -->
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

          <button
            type="button"
            id="tab-users-btn"
            (click)="activeTab.set('users')"
            [class.bg-white]="activeTab() === 'users'"
            [class.shadow-sm]="activeTab() === 'users'"
            [class.text-indigo-600]="activeTab() === 'users'"
            [class.text-slate-600]="activeTab() !== 'users'"
            class="px-4 py-2 rounded-lg transition-all flex items-center gap-2"
          >
            <span>👥</span>
            <span>User Management</span>
          </button>
        </div>

        <!-- Right Quick Action -->
        <div *ngIf="activeTab() === 'users'">
          <button
            type="button"
            id="open-create-user-modal-btn"
            (click)="isCreateUserModalOpen.set(true)"
            class="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 text-white font-semibold shadow-sm hover:bg-indigo-700 transition-colors"
          >
            <span>+</span>
            <span>Provision Staff Account</span>
          </button>
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
              (ngModelChange)="selectedZone.set($event)"
              class="px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
            >
              <option value="">All Delivery Zones</option>
              <option value="ZONE-COLOMBO-NORTH">Colombo North</option>
              <option value="ZONE-COLOMBO-SOUTH">Colombo South</option>
              <option value="ZONE-KANDY">Kandy Central</option>
            </select>
          </div>

          <p class="text-xs text-slate-500 font-medium">
            Showing <strong class="text-slate-800">{{ filteredParcels().length }}</strong> unassigned parcel(s)
          </p>
        </div>

        <!-- Parcels Grid / Table -->
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
                  ● CREATED (Unassigned)
                </span>
              </div>

              <!-- Pickup & Dropoff Routing -->
              <div class="mt-4 space-y-2.5 text-xs text-slate-600">
                <div class="flex items-start gap-2">
                  <span class="text-emerald-600 font-bold mt-0.5">🟢 PICKUP:</span>
                  <div>
                    <p class="font-bold text-slate-800">{{ parcel.pickupAddress }}</p>
                    <p class="text-[11px] text-slate-500">{{ parcel.pickupZone }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-2">
                  <span class="text-indigo-600 font-bold mt-0.5">📍 DROPOFF:</span>
                  <div>
                    <p class="font-bold text-slate-800">{{ parcel.dropoffAddress }}</p>
                    <p class="text-[11px] text-slate-500">{{ parcel.dropoffZone }} • Recipient: {{ parcel.recipientName }} ({{ parcel.recipientPhone }})</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- Footer Specs & Assign Trigger -->
            <div class="pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
              <div class="flex items-center gap-3 text-xs text-slate-500 font-medium">
                <span>⚖️ {{ parcel.weightKg }} kg</span>
                <span>•</span>
                <span>Attempts: {{ parcel.attemptCount }}</span>
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
              (ngModelChange)="workloadZone.set($event)"
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
                (ngModelChange)="availableOnly.set($event)"
                class="rounded text-indigo-600 focus:ring-indigo-500"
              />
              <span>Online Only</span>
            </label>
          </div>

          <p class="text-xs text-slate-500 font-medium">
            Active Couriers: <strong class="text-slate-800">{{ filteredCouriers().length }}</strong>
          </p>
        </div>

        <!-- Couriers Grid -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
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
                    <p class="text-[11px] text-slate-500 font-medium">{{ courier.phoneNumber }}</p>
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
                <p><span class="font-semibold text-slate-400">Zone:</span> {{ courier.zone }}</p>
                <p><span class="font-semibold text-slate-400">Vehicle:</span> {{ courier.vehicleType }}</p>
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
      </div>

      <!-- ================================================================= -->
      <!-- TAB 3: USER MANAGEMENT -->
      <!-- ================================================================= -->
      <div *ngIf="activeTab() === 'users'" class="space-y-4">
        <!-- User Filters -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div class="flex items-center gap-3 flex-1">
            <select
              id="user-role-filter"
              [ngModel]="userRoleFilter()"
              (ngModelChange)="userRoleFilter.set($event)"
              class="px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
            >
              <option value="">All Roles</option>
              <option value="ADMIN">ADMIN</option>
              <option value="DISPATCHER">DISPATCHER</option>
              <option value="COURIER">COURIER</option>
            </select>

            <select
              id="user-status-filter"
              [ngModel]="userStatusFilter()"
              (ngModelChange)="userStatusFilter.set($event)"
              class="px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
            >
              <option value="">All Statuses</option>
              <option value="true">Active Accounts Only</option>
              <option value="false">Disabled Accounts Only</option>
            </select>
          </div>

          <p class="text-xs text-slate-500 font-medium">
            Total Staff & Users: <strong class="text-slate-800">{{ filteredUsers().length }}</strong>
          </p>
        </div>

        <!-- Users Table -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs sm:text-sm">
              <thead class="bg-slate-50 border-b border-slate-200 text-slate-600 font-bold uppercase tracking-wider text-[11px]">
                <tr>
                  <th class="px-5 py-3.5">User</th>
                  <th class="px-5 py-3.5">Phone Number</th>
                  <th class="px-5 py-3.5">Role</th>
                  <th class="px-5 py-3.5">Status</th>
                  <th class="px-5 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 text-slate-800">
                <tr *ngFor="let user of filteredUsers()" class="hover:bg-slate-50/80 transition-colors">
                  <td class="px-5 py-4">
                    <div class="font-extrabold text-slate-900">{{ user.fullName }}</div>
                    <div class="text-xs text-slate-400 font-medium">{{ user.email }}</div>
                  </td>
                  <td class="px-5 py-4 font-mono text-xs text-slate-600">
                    {{ user.phoneNumber }}
                  </td>
                  <td class="px-5 py-4">
                    <span
                      class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold"
                      [class.bg-purple-50]="user.role === 'ADMIN'"
                      [class.text-purple-700]="user.role === 'ADMIN'"
                      [class.border-purple-200]="user.role === 'ADMIN'"
                      [class.bg-blue-50]="user.role === 'DISPATCHER'"
                      [class.text-blue-700]="user.role === 'DISPATCHER'"
                      [class.border-blue-200]="user.role === 'DISPATCHER'"
                      [class.bg-emerald-50]="user.role === 'COURIER'"
                      [class.text-emerald-700]="user.role === 'COURIER'"
                      [class.border-emerald-200]="user.role === 'COURIER'"
                      class="border"
                    >
                      {{ user.role }}
                    </span>
                  </td>
                  <td class="px-5 py-4">
                    <span
                      class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold"
                      [class.bg-emerald-50]="user.enabled"
                      [class.text-emerald-700]="user.enabled"
                      [class.bg-rose-50]="!user.enabled"
                      [class.text-rose-700]="!user.enabled"
                    >
                      <span>{{ user.enabled ? '●' : '○' }}</span>
                      <span>{{ user.enabled ? 'Active' : 'Disabled' }}</span>
                    </span>
                  </td>
                  <td class="px-5 py-4 text-right">
                    <button
                      type="button"
                      [attr.id]="'toggle-user-' + user.id + '-btn'"
                      (click)="toggleUserStatus(user)"
                      class="px-3 py-1.5 rounded-lg text-xs font-semibold border transition-colors"
                      [class.border-rose-200]="user.enabled"
                      [class.text-rose-600]="user.enabled"
                      [class.hover:bg-rose-50]="user.enabled"
                      [class.border-emerald-200]="!user.enabled"
                      [class.text-emerald-600]="!user.enabled"
                      [class.hover:bg-emerald-50]="!user.enabled"
                    >
                      {{ user.enabled ? 'Disable Account' : 'Enable Account' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
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
              (click)="selectedParcelForAssign.set(null)"
              class="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-600 hover:bg-slate-100 text-sm"
            >
              ✕
            </button>
          </div>

          <!-- Parcel Route Card -->
          <div class="bg-slate-50 rounded-xl p-3.5 border border-slate-200/80 text-xs space-y-1.5 text-slate-700">
            <p><strong>Route:</strong> {{ selectedParcelForAssign()?.pickupZone }} → {{ selectedParcelForAssign()?.dropoffZone }}</p>
            <p><strong>Dropoff Address:</strong> {{ selectedParcelForAssign()?.dropoffAddress }}</p>
            <p><strong>Weight:</strong> {{ selectedParcelForAssign()?.weightKg }} kg</p>
          </div>

          <!-- Select Courier -->
          <div class="space-y-2">
            <label class="block text-xs font-bold text-slate-700 uppercase tracking-wider">
              Select Available Courier
            </label>
            <div class="max-h-56 overflow-y-auto space-y-2 pr-1">
              <div
                *ngFor="let courier of dispatchService.couriers()"
                (click)="courier.available ? selectedCourierId.set(courier.courierId) : null"
                class="p-3 rounded-xl border flex items-center justify-between gap-3 transition-all"
                [class.cursor-pointer]="courier.available"
                [class.cursor-not-allowed]="!courier.available"
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
                    [disabled]="!courier.available"
                    class="text-indigo-600 focus:ring-indigo-500"
                  />
                  <div>
                    <p class="text-xs font-bold text-slate-900">{{ courier.name }}</p>
                    <p class="text-[11px] text-slate-500">{{ courier.zone }} • {{ courier.vehicleType }}</p>
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
              (click)="selectedParcelForAssign.set(null)"
              class="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="button"
              id="confirm-assignment-btn"
              [disabled]="!selectedCourierId()"
              (click)="confirmAssignment()"
              class="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Confirm Assignment
            </button>
          </div>
        </div>
      </div>

      <!-- ================================================================= -->
      <!-- MODAL: PROVISION STAFF USER -->
      <!-- ================================================================= -->
      <div
        *ngIf="isCreateUserModalOpen()"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-fade-in"
      >
        <div class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md p-6 space-y-4">
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h3 class="text-base font-extrabold text-slate-900">Provision Staff Account</h3>
              <p class="text-xs text-slate-500">Create a COURIER or DISPATCHER account (M4)</p>
            </div>
            <button
              type="button"
              (click)="isCreateUserModalOpen.set(false)"
              class="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-600 hover:bg-slate-100 text-sm"
            >
              ✕
            </button>
          </div>

          <form (ngSubmit)="submitCreateStaffUser()" class="space-y-3.5">
            <div>
              <label class="block text-xs font-bold text-slate-700 mb-1">Full Name</label>
              <input
                type="text"
                id="staff-fullname-input"
                [(ngModel)]="newUserFullName"
                name="fullName"
                required
                placeholder="e.g. Sunil Perera"
                class="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-slate-700 mb-1">Email Address</label>
              <input
                type="email"
                id="staff-email-input"
                [(ngModel)]="newUserEmail"
                name="email"
                required
                placeholder="e.g. courier.sunil@swiftdrop.lk"
                class="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-slate-700 mb-1">Phone Number</label>
              <input
                type="text"
                id="staff-phone-input"
                [(ngModel)]="newUserPhone"
                name="phoneNumber"
                required
                placeholder="+94771234567"
                class="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-slate-700 mb-1">Password</label>
              <input
                type="password"
                id="staff-password-input"
                [(ngModel)]="newUserPassword"
                name="password"
                required
                placeholder="Minimum 8 characters"
                class="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-bold text-slate-700 mb-1">Staff Role</label>
              <select
                id="staff-role-select"
                [(ngModel)]="newUserRole"
                name="role"
                class="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
              >
                <option value="COURIER">COURIER (Delivery Personnel)</option>
                <option value="DISPATCHER">DISPATCHER (Queue & Workload Manager)</option>
              </select>
            </div>

            <!-- Modal Action Buttons -->
            <div class="pt-3 border-t border-slate-100 flex items-center justify-end gap-2">
              <button
                type="button"
                (click)="isCreateUserModalOpen.set(false)"
                class="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                id="submit-staff-btn"
                [disabled]="!isFormValid()"
                class="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-sm transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Provision Account
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
})
export class DispatchComponent {
  public dispatchService = inject(DispatchService);

  public activeTab = signal<DispatchTab>('queue');

  // Search & Filter Signals
  public searchQuery = signal('');
  public selectedZone = signal('');
  public workloadZone = signal('');
  public availableOnly = signal(false);
  public userRoleFilter = signal('');
  public userStatusFilter = signal('');

  // Modals state
  public selectedParcelForAssign = signal<UnassignedParcel | null>(null);
  public selectedCourierId = signal<string>('');
  public isCreateUserModalOpen = signal<boolean>(false);

  // New staff form state
  public newUserFullName = '';
  public newUserEmail = '';
  public newUserPhone = '';
  public newUserPassword = '';
  public newUserRole: 'COURIER' | 'DISPATCHER' = 'COURIER';

  // Toast state
  public toastMessage = signal<string | null>(null);
  public toastType = signal<'success' | 'error'>('success');

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
          p.pickupAddress.toLowerCase().includes(q) ||
          p.dropoffAddress.toLowerCase().includes(q) ||
          p.recipientName.toLowerCase().includes(q)
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

  public filteredUsers = computed(() => {
    let list = this.dispatchService.users();
    const role = this.userRoleFilter();
    if (role) {
      list = list.filter((u) => u.role === role);
    }
    const status = this.userStatusFilter();
    if (status !== '') {
      const enabledBool = status === 'true';
      list = list.filter((u) => u.enabled === enabledBool);
    }
    return list;
  });

  public openAssignModal(parcel: UnassignedParcel): void {
    this.selectedParcelForAssign.set(parcel);
    const firstAvailable = this.dispatchService.availableCouriers()[0];
    this.selectedCourierId.set(firstAvailable ? firstAvailable.courierId : '');
  }

  public confirmAssignment(): void {
    const parcel = this.selectedParcelForAssign();
    const courierId = this.selectedCourierId();
    if (!parcel || !courierId) {
      return;
    }

    try {
      this.dispatchService.assignParcel(parcel.parcelId, courierId);
      const courier = this.dispatchService
        .couriers()
        .find((c) => c.courierId === courierId);
      this.showToast(
        `Parcel ${parcel.trackingNumber} assigned to ${courier?.name || 'Courier'}. Workload count updated!`,
        'success'
      );
      this.selectedParcelForAssign.set(null);
    } catch (err: any) {
      this.showToast(err.message || 'Failed to assign parcel', 'error');
    }
  }

  public toggleCourierAvailability(courier: CourierWorkload): void {
    const nextStatus = !courier.available;
    this.dispatchService.updateCourierAvailability(
      courier.courierId,
      nextStatus
    );
    this.showToast(
      `${courier.name} is now ${nextStatus ? 'Online' : 'Offline'}.`,
      'success'
    );
  }

  public toggleUserStatus(user: StaffUser): void {
    this.dispatchService.toggleUserStatus(user.id);
    const updated = this.dispatchService.users().find((u) => u.id === user.id);
    this.showToast(
      `User account ${user.email} is now ${updated?.enabled ? 'Active' : 'Disabled'}.`,
      'success'
    );
  }

  public isFormValid(): boolean {
    return (
      this.newUserFullName.trim().length > 0 &&
      this.newUserEmail.trim().includes('@') &&
      this.newUserPhone.trim().length >= 10 &&
      this.newUserPassword.length >= 8
    );
  }

  public submitCreateStaffUser(): void {
    if (!this.isFormValid()) {
      return;
    }

    try {
      const payload: CreateStaffPayload = {
        fullName: this.newUserFullName,
        email: this.newUserEmail,
        phoneNumber: this.newUserPhone,
        password: this.newUserPassword,
        role: this.newUserRole,
      };

      const created = this.dispatchService.createStaffUser(payload);
      this.showToast(
        `Staff account for ${created.fullName} (${created.role}) created successfully!`,
        'success'
      );

      // Reset form & close
      this.newUserFullName = '';
      this.newUserEmail = '';
      this.newUserPhone = '';
      this.newUserPassword = '';
      this.isCreateUserModalOpen.set(false);
    } catch (err: any) {
      this.showToast(err.message || 'Failed to create staff account', 'error');
    }
  }

  private showToast(message: string, type: 'success' | 'error'): void {
    this.toastMessage.set(message);
    this.toastType.set(type);
    setTimeout(() => {
      this.toastMessage.set(null);
    }, 4500);
  }
}
