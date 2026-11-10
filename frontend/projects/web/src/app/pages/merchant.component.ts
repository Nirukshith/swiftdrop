import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
  PaginationComponent,
} from 'ui';
import {
  MerchantOrder,
  MerchantOrderFilterParams,
  OrderCounts,
  ParcelLifecycleStatus,
  TestWebhookResult,
  WebhookDeliveryItem,
  WebhookDeliveryStatus,
  WebhookSettings,
} from './merchant/merchant.model';
import { MerchantService } from './merchant/merchant.service';
import { MerchantCredentialsComponent } from './merchant/merchant-credentials.component';

type ActiveTab = 'orders' | 'settings' | 'deliveries' | 'credentials';

@Component({
  selector: 'app-merchant',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MerchantCredentialsComponent,
    PaginationComponent,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorStateComponent,
  ],
  template: `
    <div class="max-w-7xl mx-auto space-y-6">
      <!-- Portal Header -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <span class="inline-flex items-center justify-center w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 font-bold text-sm">
              M
            </span>
            <h1 class="text-2xl font-bold text-slate-900">Merchant Portal</h1>
          </div>
          <p class="text-sm text-slate-500 mt-1">
            Manage parcel delivery lifecycles, configure HMAC-SHA256 webhooks, and inspect real-time delivery logs.
          </p>
        </div>

        <!-- Navigation Tabs -->
        <div class="inline-flex flex-wrap rounded-lg bg-slate-100 p-1 border border-slate-200 text-sm font-medium">
          <button
            type="button"
            (click)="selectTab('orders')"
            [class.bg-white]="activeTab() === 'orders'"
            [class.shadow-sm]="activeTab() === 'orders'"
            [class.text-indigo-600]="activeTab() === 'orders'"
            [class.text-slate-600]="activeTab() !== 'orders'"
            class="px-4 py-2 rounded-md transition-colors"
          >
            📦 Orders Dashboard
          </button>
          <button
            type="button"
            (click)="selectTab('settings')"
            [class.bg-white]="activeTab() === 'settings'"
            [class.shadow-sm]="activeTab() === 'settings'"
            [class.text-indigo-600]="activeTab() === 'settings'"
            [class.text-slate-600]="activeTab() !== 'settings'"
            class="px-4 py-2 rounded-md transition-colors"
          >
            ⚙️ Webhook Settings
          </button>
          <button
            type="button"
            (click)="selectTab('deliveries')"
            [class.bg-white]="activeTab() === 'deliveries'"
            [class.shadow-sm]="activeTab() === 'deliveries'"
            [class.text-indigo-600]="activeTab() === 'deliveries'"
            [class.text-slate-600]="activeTab() !== 'deliveries'"
            class="px-4 py-2 rounded-md transition-colors"
          >
            📜 Delivery Logs
          </button>
          <button
            type="button"
            (click)="selectTab('credentials')"
            [class.bg-white]="activeTab() === 'credentials'"
            [class.shadow-sm]="activeTab() === 'credentials'"
            [class.text-indigo-600]="activeTab() === 'credentials'"
            [class.text-slate-600]="activeTab() !== 'credentials'"
            class="px-4 py-2 rounded-md transition-colors"
          >
            🔑 API Credentials
          </button>
        </div>
      </div>

      <!-- Toast Feedback -->
      <div *ngIf="notification()" class="rounded-lg bg-emerald-50 border border-emerald-200 p-4 text-sm text-emerald-800 flex justify-between items-center">
        <div class="flex items-center gap-2">
          <span>✅</span>
          <span>{{ notification() }}</span>
        </div>
        <button type="button" (click)="notification.set(null)" class="text-emerald-600 hover:text-emerald-900 font-bold">&times;</button>
      </div>

      <!-- ========================================================================= -->
      <!-- TAB 1: ORDERS DASHBOARD                                                  -->
      <!-- ========================================================================= -->
      <div *ngIf="activeTab() === 'orders'" class="space-y-6">
        <!-- KPI Metrics Cards -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
            <span class="text-xs font-semibold uppercase tracking-wider text-slate-500">Total Orders</span>
            <div class="text-3xl font-bold text-slate-900 mt-2">{{ counts().total }}</div>
            <span class="text-xs text-slate-400 mt-1 block">All registered parcels</span>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
            <span class="text-xs font-semibold uppercase tracking-wider text-amber-600">Active In-Transit</span>
            <div class="text-3xl font-bold text-amber-600 mt-2">{{ (counts().pickedUp || 0) + (counts().inTransit || 0) + (counts().outForDelivery || 0) }}</div>
            <span class="text-xs text-slate-400 mt-1 block">Picked up & Out for delivery</span>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
            <span class="text-xs font-semibold uppercase tracking-wider text-emerald-600">Delivered</span>
            <div class="text-3xl font-bold text-emerald-600 mt-2">{{ counts().delivered }}</div>
            <span class="text-xs text-slate-400 mt-1 block">Successfully fulfilled</span>
          </div>
          <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
            <span class="text-xs font-semibold uppercase tracking-wider text-rose-600">Failed / Cancelled</span>
            <div class="text-3xl font-bold text-rose-600 mt-2">{{ counts().failed + counts().cancelled }}</div>
            <span class="text-xs text-slate-400 mt-1 block">Requiring review or retry</span>
          </div>
        </div>

        <!-- Filter & Search Toolbar -->
        <div class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm flex flex-col gap-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-xs font-semibold text-slate-500 mr-1">Status:</span>
            <button
              *ngFor="let s of statusFilterOptions"
              type="button"
              (click)="onStatusFilterChange(s.value)"
              [class.bg-indigo-600]="selectedStatusFilter() === s.value"
              [class.text-white]="selectedStatusFilter() === s.value"
              [class.bg-slate-100]="selectedStatusFilter() !== s.value"
              [class.text-slate-700]="selectedStatusFilter() !== s.value"
              class="px-3 py-1.5 rounded-lg text-xs font-medium hover:opacity-90 transition-opacity"
            >
              {{ s.label }}
            </button>
          </div>

          <div class="flex flex-col sm:flex-row gap-3 items-center justify-between pt-2 border-t border-slate-100">
            <div class="flex flex-wrap items-center gap-3 w-full sm:w-auto">
              <div class="flex items-center gap-1.5 text-xs text-slate-600">
                <label for="fromDate" class="font-medium">From:</label>
                <input
                  id="fromDate"
                  type="date"
                  [(ngModel)]="fromDate"
                  (change)="onDateFilterChange()"
                  class="border border-slate-300 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
              </div>
              <div class="flex items-center gap-1.5 text-xs text-slate-600">
                <label for="toDate" class="font-medium">To:</label>
                <input
                  id="toDate"
                  type="date"
                  [(ngModel)]="toDate"
                  (change)="onDateFilterChange()"
                  class="border border-slate-300 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
              </div>
              <button
                *ngIf="selectedStatusFilter() !== 'ALL' || searchQuery || fromDate || toDate"
                type="button"
                (click)="resetFilters()"
                class="text-xs text-indigo-600 hover:text-indigo-800 font-medium px-2 py-1 underline"
              >
                Clear filters
              </button>
            </div>

            <div class="w-full sm:w-72">
              <input
                type="text"
                placeholder="Search by tracking number..."
                [(ngModel)]="searchQuery"
                (input)="onSearchChange()"
                class="w-full text-xs sm:text-sm border border-slate-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
          </div>
        </div>

        <!-- Orders Table / States -->
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div *ngIf="isLoadingOrders()" class="p-8">
            <ui-loading-state message="Loading merchant orders..." />
          </div>

          <div *ngIf="!isLoadingOrders() && orderError()" class="p-8">
            <ui-error-state
              title="Unable to load orders"
              [message]="orderError()!"
              (retry)="loadOrders()"
            />
          </div>

          <div *ngIf="!isLoadingOrders() && !orderError() && orders().length === 0" class="p-8">
            <ui-empty-state
              title="No orders found"
              message="No parcel orders match your selected filters. Try clearing your search or status filter."
            />
          </div>

          <div *ngIf="!isLoadingOrders() && !orderError() && orders().length > 0" class="overflow-x-auto">
            <table class="w-full text-left text-sm text-slate-600">
              <thead class="bg-slate-50 border-b border-slate-200 text-xs uppercase font-semibold text-slate-500">
                <tr>
                  <th class="px-6 py-3">Tracking Number</th>
                  <th class="px-6 py-3">Reference</th>
                  <th class="px-6 py-3">Recipient & Address</th>
                  <th class="px-6 py-3">Fee</th>
                  <th class="px-6 py-3">Status</th>
                  <th class="px-6 py-3">Created</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let o of orders()" class="hover:bg-slate-50 transition-colors">
                  <td class="px-6 py-4 font-mono font-medium text-indigo-600">
                    {{ o.trackingNumber }}
                  </td>
                  <td class="px-6 py-4 text-slate-500">
                    {{ o.externalReference || '—' }}
                  </td>
                  <td class="px-6 py-4">
                    <div class="font-medium text-slate-900">{{ o.recipientName || '—' }}</div>
                    <div *ngIf="o.recipientAddress" class="text-xs text-slate-400 mt-0.5">{{ o.recipientAddress }}</div>
                  </td>
                  <td class="px-6 py-4 font-mono text-xs text-slate-700">
                    {{ o.fee != null ? 'LKR ' + (o.fee | number:'1.2-2') : '—' }}
                  </td>
                  <td class="px-6 py-4">
                    <span [ngClass]="getStatusBadgeClass(o.status)" class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold">
                      {{ o.status }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-xs text-slate-400">
                    {{ o.createdAt | date: 'MMM d, y, h:mm a' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div *ngIf="!isLoadingOrders() && !orderError() && totalElements() > 0" class="p-4 border-t border-slate-200 bg-slate-50">
            <ui-pagination
              [page]="currentPage()"
              [size]="pageSize()"
              [totalElements]="totalElements()"
              (pageChange)="onPageChange($event)"
            />
          </div>
        </div>
      </div>

      <!-- ========================================================================= -->
      <!-- TAB 2: WEBHOOK SETTINGS                                                  -->
      <!-- ========================================================================= -->
      <div *ngIf="activeTab() === 'settings'" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div *ngIf="isLoadingSettings()" class="lg:col-span-3" role="status">Loading webhook settings...</div>
        <div *ngIf="settingsError()" class="lg:col-span-3 rounded-lg bg-rose-50 border border-rose-200 p-3 text-sm text-rose-800" role="alert">
          {{ settingsError() }}
          <button *ngIf="!settings()" type="button" (click)="loadSettings()" class="ml-2 underline">Retry</button>
        </div>
        <!-- Configuration Card -->
        <div *ngIf="settings()" class="lg:col-span-2 space-y-6">
          <div class="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5">
            <h2 class="text-lg font-bold text-slate-900">Webhook Destination & Subscriptions</h2>

            <!-- URL input -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1">
                Destination URL <span class="text-rose-500">*</span>
              </label>
              <input
                type="url"
                [(ngModel)]="settingsForm.url"
                [disabled]="isSavingSettings()"
                placeholder="https://api.yourdomain.com/webhooks/swiftdrop"
                class="w-full text-sm border border-slate-300 rounded-lg px-3.5 py-2.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <p class="text-xs text-slate-500 mt-1">
                🔒 <strong>SSRF Hardened:</strong> Only public HTTPS endpoints are accepted. Loopback (127.0.0.1), RFC 1918 private IPs, and cloud metadata (169.254.169.254) are rejected.
              </p>
            </div>

            <!-- Active Toggle -->
            <div class="flex items-center justify-between p-4 rounded-lg bg-slate-50 border border-slate-200">
              <div>
                <div class="text-sm font-semibold text-slate-800">Dispatch Webhooks</div>
                <div class="text-xs text-slate-500">Enable or temporarily suspend outbound webhook dispatches.</div>
              </div>
              <label class="relative inline-flex items-center cursor-pointer">
                <input type="checkbox" [(ngModel)]="settingsForm.active" [disabled]="isSavingSettings()" class="sr-only peer" />
                <div class="w-11 h-6 bg-slate-300 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
              </label>
            </div>

            <!-- Subscriptions -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-2">Subscribed Event Types</label>
              <div class="space-y-2">
                <label class="flex items-center gap-2.5 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    [checked]="settingsForm.subscribedEvents.includes('PARCEL_STATUS_CHANGED')"
                    (change)="toggleEvent('PARCEL_STATUS_CHANGED')"
                    [disabled]="isSavingSettings()"
                    class="rounded text-indigo-600 focus:ring-indigo-500"
                  />
                  <span><code>PARCEL_STATUS_CHANGED</code> — Emitted on state transitions (PICKED_UP, IN_TRANSIT, DELIVERED, etc.)</span>
                </label>
                <label class="flex items-center gap-2.5 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    [checked]="settingsForm.subscribedEvents.includes('PARCEL_CREATED')"
                    (change)="toggleEvent('PARCEL_CREATED')"
                    [disabled]="isSavingSettings()"
                    class="rounded text-indigo-600 focus:ring-indigo-500"
                  />
                  <span><code>PARCEL_CREATED</code> — Emitted when a new parcel is accepted into the system</span>
                </label>
              </div>
            </div>

            <div class="pt-4 border-t border-slate-200 flex justify-end">
              <button
                type="button"
                (click)="saveSettings()"
                [disabled]="isSavingSettings() || isLoadingSettings()"
                class="px-5 py-2.5 bg-indigo-600 text-white font-medium text-sm rounded-lg hover:bg-indigo-700 shadow-sm transition-colors"
              >
                {{ isSavingSettings() ? 'Saving...' : 'Save Settings' }}
              </button>
            </div>
          </div>

          <!-- HMAC Secret Card -->
          <div class="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
            <h2 class="text-lg font-bold text-slate-900">HMAC-SHA256 Signing Secret</h2>
            <p class="text-xs text-slate-500">
              SwiftDrop signs every outbound HTTP POST request using HMAC-SHA256 over <code>timestamp + "." + rawBody</code>. Verify the signature in <code>X-SwiftDrop-Signature</code> using this secret.
            </p>

            <div *ngIf="settings()?.rawSecret" class="p-4 rounded-lg bg-amber-50 border border-amber-200 text-amber-900 text-xs space-y-1">
              <div class="font-bold flex items-center gap-1">
                <span>⚠️</span> Copy your new signing secret now!
              </div>
              <p>This secret will not be displayed in full again:</p>
              <div class="font-mono bg-white p-2 rounded border border-amber-200 text-slate-800 break-all select-all">
                {{ settings()?.rawSecret }}
              </div>
              <button type="button" (click)="dismissSecret()" class="underline font-semibold">I have saved this secret</button>
            </div>

            <div class="flex items-center justify-between p-3.5 bg-slate-50 rounded-lg border border-slate-200">
              <div>
                <span class="text-xs text-slate-400 block">Active Secret Preview</span>
                <span class="font-mono text-sm text-slate-800">{{ settings()?.secretPreview || 'No secret configured' }}</span>
              </div>
              <button
                type="button"
                (click)="regenerateSecret()"
                [disabled]="isSavingSettings() || isLoadingSettings()"
                class="px-3.5 py-1.5 border border-slate-300 hover:bg-slate-100 text-slate-700 text-xs font-semibold rounded-md transition-colors"
              >
                Regenerate Secret
              </button>
            </div>
          </div>
        </div>

        <!-- Test Event Trigger Panel -->
        <div *ngIf="settings()" class="bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-5 h-fit">
          <div class="border-b border-slate-100 pb-3">
            <h2 class="text-lg font-bold text-slate-900">Test Webhook Trigger</h2>
            <p class="text-xs text-slate-500 mt-1">
              Dispatch an immediate test event to verify DNS resolution, endpoint latency, and HMAC signature verification.
            </p>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-600 mb-1">Target URL Override (Optional)</label>
            <input
              type="url"
              [(ngModel)]="testUrlOverride"
              [disabled]="isTestingWebhook()"
              placeholder="Leave empty to use configured URL"
              class="w-full text-xs border border-slate-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>

          <button
            type="button"
            (click)="runTestWebhook()"
            [disabled]="isTestingWebhook()"
            class="w-full py-2.5 bg-slate-900 text-white font-medium text-sm rounded-lg hover:bg-slate-800 disabled:opacity-50 transition-colors flex justify-center items-center gap-2"
          >
            <span *ngIf="isTestingWebhook()" class="inline-block animate-spin">⏳</span>
            <span>{{ isTestingWebhook() ? 'Sending Test Event...' : '⚡ Send Test Event' }}</span>
          </button>

          <div *ngIf="testError()" class="rounded-lg bg-rose-50 border border-rose-200 p-3 text-xs text-rose-800" role="alert">{{ testError() }}</div>

          <!-- Test Execution Result -->
          <div *ngIf="testResult()" class="rounded-lg p-4 text-xs space-y-2 border" [ngClass]="testResult()?.success ? 'bg-emerald-50 border-emerald-200 text-emerald-900' : 'bg-rose-50 border-rose-200 text-rose-900'">
            <div class="flex items-center justify-between font-bold">
              <span>{{ testResult()?.success ? 'HTTP ' + testResult()?.statusCode + ' — Delivered' : 'Delivery Failed' }}</span>
              <span>{{ testResult()?.latencyMs }} ms</span>
            </div>
            <div class="space-y-1 font-mono text-[11px] pt-1">
              <div><strong>Status:</strong> {{ testResult()?.statusCode }}</div>
              <div class="truncate"><strong>Signature:</strong> {{ testResult()?.signatureSent }}</div>
              <div class="truncate"><strong>Timestamp:</strong> {{ testResult()?.timestampSent }}</div>
            </div>
            <div *ngIf="testResult()?.responseBodySummary" class="mt-2 p-2 bg-white/75 rounded border border-slate-200 text-[11px] font-mono break-all">
              {{ testResult()?.responseBodySummary }}
            </div>
            <div *ngIf="testResult()?.errorMessage">{{ testResult()?.errorMessage }}</div>
          </div>
        </div>
      </div>

      <!-- ========================================================================= -->
      <!-- TAB 3: WEBHOOK DELIVERY LOG                                              -->
      <!-- ========================================================================= -->
      <div *ngIf="activeTab() === 'deliveries'" class="space-y-6">
        <div *ngIf="isLoadingDeliveries()" role="status" class="text-sm text-slate-500">Loading webhook deliveries...</div>
        <div *ngIf="deliveryError()" role="alert" class="rounded-lg bg-rose-50 border border-rose-200 p-3 text-sm text-rose-800">
          {{ deliveryError() }}
          <button type="button" (click)="loadDeliveries()" class="ml-2 underline">Retry</button>
        </div>
        <!-- Log Filter Toolbar -->
        <div class="bg-white rounded-xl border border-slate-200 p-4 shadow-sm flex flex-wrap gap-2 items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-xs font-semibold text-slate-500 mr-1">Status:</span>
            <button
              *ngFor="let f of deliveryFilterOptions"
              type="button"
              (click)="selectedDeliveryFilter.set(f.value); loadDeliveries()"
              [class.bg-indigo-600]="selectedDeliveryFilter() === f.value"
              [class.text-white]="selectedDeliveryFilter() === f.value"
              [class.bg-slate-100]="selectedDeliveryFilter() !== f.value"
              [class.text-slate-700]="selectedDeliveryFilter() !== f.value"
              class="px-3 py-1.5 rounded-lg text-xs font-medium hover:opacity-90 transition-opacity"
            >
              {{ f.label }}
            </button>
          </div>

          <div class="text-xs text-slate-400">
            Showing {{ deliveries().length }} delivery attempt(s)
          </div>
        </div>

        <!-- Deliveries Table -->
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-sm text-slate-600">
              <thead class="bg-slate-50 border-b border-slate-200 text-xs uppercase font-semibold text-slate-500">
                <tr>
                  <th class="px-6 py-3">Delivery ID</th>
                  <th class="px-6 py-3">Event Type</th>
                  <th class="px-6 py-3">Tracking #</th>
                  <th class="px-6 py-3">HTTP Code</th>
                  <th class="px-6 py-3">Status</th>
                  <th class="px-6 py-3">Attempts</th>
                  <th class="px-6 py-3">Dispatched</th>
                  <th class="px-6 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let d of deliveries()" class="hover:bg-slate-50 transition-colors">
                  <td class="px-6 py-4 font-mono text-xs text-slate-700">
                    {{ d.deliveryId.substring(0, 8) }}...
                  </td>
                  <td class="px-6 py-4 font-mono text-xs font-semibold text-indigo-600">
                    {{ d.eventType }}
                  </td>
                  <td class="px-6 py-4 font-mono text-xs">
                    {{ d.trackingNumber }}
                  </td>
                  <td class="px-6 py-4 font-mono text-xs">
                    <span [ngClass]="d.httpStatusCode && d.httpStatusCode < 300 ? 'text-emerald-600 font-bold' : 'text-rose-600 font-bold'">
                      {{ d.httpStatusCode || 'ERR' }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    <span [ngClass]="getDeliveryBadgeClass(d.status)" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium">
                      {{ d.status }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-xs font-medium text-slate-700">
                    {{ d.attempts }}/5
                  </td>
                  <td class="px-6 py-4 text-xs text-slate-400">
                    {{ d.createdAt | date: 'MMM d, h:mm:ss a' }}
                  </td>
                  <td class="px-6 py-4 text-right space-x-2">
                    <button
                      type="button"
                      (click)="inspectDelivery(d)"
                      class="px-2.5 py-1 text-xs font-medium text-indigo-600 hover:text-indigo-800 bg-indigo-50 rounded hover:bg-indigo-100 transition-colors"
                    >
                      Inspect
                    </button>
                    <button
                      *ngIf="d.status === 'FAILED' || d.status === 'PENDING_RETRY'"
                      type="button"
                      (click)="retryDelivery(d.deliveryId)"
                      class="px-2.5 py-1 text-xs font-medium text-amber-700 bg-amber-50 hover:bg-amber-100 rounded transition-colors"
                    >
                      Retry
                    </button>
                  </td>
                </tr>
                <tr *ngIf="deliveries().length === 0 && !isLoadingDeliveries() && !deliveryError()">
                  <td colspan="8" class="px-6 py-8 text-center text-slate-400">
                    No webhook deliveries found.
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Diagnostic Inspection Modal -->
        <div *ngIf="selectedDelivery()" class="fixed inset-0 bg-slate-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div class="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden">
            <div class="p-5 border-b border-slate-200 flex justify-between items-center bg-slate-50">
              <div>
                <h3 class="text-base font-bold text-slate-900">Webhook Diagnostic Details</h3>
                <span class="font-mono text-xs text-slate-500">{{ selectedDelivery()?.deliveryId }}</span>
              </div>
              <button type="button" (click)="selectedDelivery.set(null)" class="text-slate-400 hover:text-slate-700 font-bold text-lg">&times;</button>
            </div>

            <div class="p-6 overflow-y-auto space-y-4 text-xs">
              <!-- Summary -->
              <div class="grid grid-cols-2 gap-3 p-3 bg-slate-50 rounded-lg border border-slate-200">
                <div><strong>Status:</strong> {{ selectedDelivery()?.status }} (HTTP {{ selectedDelivery()?.httpStatusCode }})</div>
                <div><strong>Attempts:</strong> {{ selectedDelivery()?.attempts }} of 5</div>
                <div><strong>Target URL:</strong> {{ selectedDelivery()?.targetUrl }}</div>
                <div><strong>Event:</strong> {{ selectedDelivery()?.eventType }}</div>
              </div>

              <!-- Request Headers -->
              <div>
                <h4 class="font-bold text-slate-800 mb-1">Outbound Request Headers</h4>
                <pre class="bg-slate-900 text-slate-100 p-3 rounded-lg overflow-x-auto text-[11px]">{{ selectedDelivery()?.requestHeaders | json }}</pre>
              </div>

              <!-- Payload -->
              <div>
                <h4 class="font-bold text-slate-800 mb-1">Signed JSON Payload</h4>
                <pre class="bg-slate-900 text-slate-100 p-3 rounded-lg overflow-x-auto text-[11px]">{{ selectedDelivery()?.payloadJson }}</pre>
              </div>

              <!-- Response Body -->
              <div *ngIf="selectedDelivery()?.responseBody">
                <h4 class="font-bold text-slate-800 mb-1">Endpoint Response Body</h4>
                <pre class="bg-slate-100 text-slate-800 p-3 rounded-lg overflow-x-auto text-[11px]">{{ selectedDelivery()?.responseBody }}</pre>
              </div>

              <!-- Retry History Timeline -->
              <div *ngIf="selectedDelivery()?.retryHistory?.length">
                <h4 class="font-bold text-slate-800 mb-1">Delivery Attempts History</h4>
                <div class="space-y-1.5">
                  <div *ngFor="let h of selectedDelivery()?.retryHistory" class="flex justify-between items-center p-2 rounded bg-slate-50 border border-slate-200">
                    <span>Attempt #{{ h.attemptNumber }}</span>
                    <span class="font-mono">{{ h.httpStatusCode }}</span>
                    <span>{{ h.latencyMs }}ms</span>
                    <span [class.text-emerald-600]="h.success" [class.text-rose-600]="!h.success">
                      {{ h.success ? 'Success' : h.errorMessage || 'Failed' }}
                    </span>
                    <span class="text-slate-400">{{ h.attemptedAt | date: 'h:mm:ss a' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="p-4 border-t border-slate-200 bg-slate-50 flex justify-end">
              <button type="button" (click)="selectedDelivery.set(null)" class="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 text-xs font-semibold rounded-lg transition-colors">
                Close
              </button>
            </div>
          </div>
        </div>
      </div>
      <app-merchant-credentials *ngIf="activeTab() === 'credentials'"></app-merchant-credentials>
    </div>
  `,
})
export class MerchantComponent implements OnInit {
  private merchantService = inject(MerchantService);

  activeTab = signal<ActiveTab>('orders');
  notification = signal<string | null>(null);

  // Orders State
  orders = signal<MerchantOrder[]>([]);
  counts = signal<OrderCounts>({ total: 0, created: 0, inTransit: 0, delivered: 0, failed: 0, cancelled: 0 });
  selectedStatusFilter = signal<string>('ALL');
  searchQuery = '';
  fromDate = '';
  toDate = '';

  // Pagination & Loading States
  currentPage = signal<number>(0);
  pageSize = signal<number>(20);
  totalElements = signal<number>(0);
  totalPages = signal<number>(0);
  isLoadingOrders = signal<boolean>(false);
  orderError = signal<string | null>(null);

  statusFilterOptions = [
    { label: 'All', value: 'ALL' },
    { label: 'Created', value: 'CREATED' },
    { label: 'Picked Up', value: 'PICKED_UP' },
    { label: 'In Transit', value: 'IN_TRANSIT' },
    { label: 'Out for Delivery', value: 'OUT_FOR_DELIVERY' },
    { label: 'Delivered', value: 'DELIVERED' },
    { label: 'Failed', value: 'FAILED' },
    { label: 'Cancelled', value: 'CANCELLED' },
  ];

  // Settings State
  settings = signal<WebhookSettings | null>(null);
  isLoadingSettings = signal(false);
  isSavingSettings = signal(false);
  settingsError = signal<string | null>(null);
  settingsForm = {
    url: '',
    active: true,
    subscribedEvents: ['PARCEL_STATUS_CHANGED', 'PARCEL_CREATED'],
  };

  // Test Webhook State
  testUrlOverride = '';
  isTestingWebhook = signal<boolean>(false);
  testResult = signal<TestWebhookResult | null>(null);
  testError = signal<string | null>(null);

  // Deliveries State
  deliveries = signal<WebhookDeliveryItem[]>([]);
  selectedDeliveryFilter = signal<string>('ALL');
  selectedDelivery = signal<WebhookDeliveryItem | null>(null);
  deliveryError = signal<string | null>(null);
  isLoadingDeliveries = signal(false);

  deliveryFilterOptions = [
    { label: 'All', value: 'ALL' },
    { label: 'Success (2xx)', value: 'SUCCESS' },
    { label: 'Failed (4xx/5xx)', value: 'FAILED' },
    { label: 'Pending Retry', value: 'PENDING_RETRY' },
  ];

  ngOnInit(): void {
    this.loadOrders();
    this.loadSettings();
    this.loadDeliveries();
  }

  selectTab(tab: ActiveTab): void {
    if (tab !== 'settings') {
      this.dismissSecret();
    }
    this.activeTab.set(tab);
  }

  dismissSecret(): void {
    const current = this.settings();
    if (current?.rawSecret) {
      this.settings.set({ ...current, rawSecret: null });
    }
  }

  loadOrders(page: number = this.currentPage()): void {
    this.currentPage.set(page);
    this.isLoadingOrders.set(true);
    this.orderError.set(null);

    const params: MerchantOrderFilterParams = {
      status: this.selectedStatusFilter() !== 'ALL' ? this.selectedStatusFilter() : undefined,
      trackingNumber: this.searchQuery?.trim() || undefined,
      from: this.fromDate ? new Date(this.fromDate).toISOString() : undefined,
      to: this.toDate ? new Date(this.toDate + 'T23:59:59.999Z').toISOString() : undefined,
      page,
      size: this.pageSize(),
    };

    this.merchantService.getOrders(params).subscribe({
      next: (res) => {
        this.orders.set(res.content);
        this.totalElements.set(res.totalElements);
        this.totalPages.set(res.totalPages);
        this.isLoadingOrders.set(false);
      },
      error: () => {
        this.orderError.set('Unable to load merchant orders. Please check network connection and retry.');
        this.isLoadingOrders.set(false);
      },
    });

    this.merchantService.getOrderCounts().subscribe({
      next: (c) => {
        this.counts.set(c);
      },
      error: () => {},
    });
  }

  onStatusFilterChange(status: string): void {
    this.selectedStatusFilter.set(status);
    this.loadOrders(0);
  }

  onSearchChange(): void {
    this.loadOrders(0);
  }

  onDateFilterChange(): void {
    this.loadOrders(0);
  }

  resetFilters(): void {
    this.selectedStatusFilter.set('ALL');
    this.searchQuery = '';
    this.fromDate = '';
    this.toDate = '';
    this.loadOrders(0);
  }

  onPageChange(newPage: number): void {
    this.loadOrders(newPage);
  }

  loadSettings(): void {
    if (this.isLoadingSettings()) return;
    this.isLoadingSettings.set(true);
    this.settingsError.set(null);
    this.merchantService.getWebhookSettings().pipe(finalize(() => this.isLoadingSettings.set(false))).subscribe({
      next: (s) => {
        this.settings.set({ ...s, rawSecret: null });
        this.settingsForm.url = s.url || '';
        this.settingsForm.active = s.active;
        this.settingsForm.subscribedEvents = [...s.subscribedEvents];
      },
      error: (error: HttpErrorResponse) => {
        this.settings.set(null);
        this.settingsError.set(this.apiErrorMessage(error, 'Unable to load webhook settings.'));
      },
    });
  }

  toggleEvent(eventType: string): void {
    if (this.isSavingSettings()) return;
    const list = this.settingsForm.subscribedEvents;
    const idx = list.indexOf(eventType);
    if (idx > -1) {
      list.splice(idx, 1);
    } else {
      list.push(eventType);
    }
  }

  saveSettings(): void {
    this.submitSettings(false);
  }

  regenerateSecret(): void {
    if (this.isSavingSettings() || this.isLoadingSettings()) return;
    if (confirm('Are you sure you want to regenerate your HMAC signing secret? Outbound signatures will immediately use the new secret.')) {
      this.submitSettings(true);
    }
  }

  private submitSettings(regenerateSecret: boolean): void {
    if (this.isSavingSettings() || this.isLoadingSettings() || !this.settings()) return;
    this.settingsError.set(null);
    const url = this.settingsForm.url.trim();
    if (!this.isHttpsUrl(url)) {
      this.settingsError.set('Enter a valid public HTTPS destination URL.');
      return;
    }
    this.isSavingSettings.set(true);
    this.merchantService.updateWebhookSettings(
      url, this.settingsForm.active, [...this.settingsForm.subscribedEvents], regenerateSecret
    ).pipe(finalize(() => this.isSavingSettings.set(false))).subscribe({
      next: (updated) => {
        this.settings.set(updated);
        this.settingsForm.url = updated.url || '';
        this.notification.set(updated.rawSecret
          ? 'Webhook settings saved. Copy the new signing secret now.'
          : 'Webhook configuration saved successfully.');
      },
      error: (error: HttpErrorResponse) => {
        this.settingsError.set(this.apiErrorMessage(error, 'Unable to save webhook settings.'));
      },
    });
  }

  runTestWebhook(): void {
    if (this.isTestingWebhook() || !this.settings()) return;
    this.testError.set(null);
    const override = this.testUrlOverride.trim();
    if (override && !this.isHttpsUrl(override)) {
      this.testError.set('Enter a valid public HTTPS test URL or leave the override empty.');
      return;
    }
    this.isTestingWebhook.set(true);
    this.testResult.set(null);

    this.merchantService.triggerTestWebhook(override).pipe(finalize(() => this.isTestingWebhook.set(false))).subscribe({
      next: (res) => {
        this.testResult.set(res);
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 502 && this.isTestResult(error.error)) {
          this.testResult.set(error.error);
        } else {
          this.testError.set(this.apiErrorMessage(error, 'Unable to send the test event.'));
        }
      },
    });
  }

  loadDeliveries(): void {
    this.isLoadingDeliveries.set(true);
    this.deliveryError.set(null);
    this.merchantService.getDeliveryLogs(this.selectedDeliveryFilter())
      .pipe(finalize(() => this.isLoadingDeliveries.set(false))).subscribe({
      next: (d) => this.deliveries.set(d),
      error: (error: HttpErrorResponse) => {
        this.deliveries.set([]);
        this.deliveryError.set(this.apiErrorMessage(error, 'Unable to load webhook deliveries.'));
      },
    });
  }

  inspectDelivery(item: WebhookDeliveryItem): void {
    this.deliveryError.set(null);
    this.merchantService.getDeliveryDetail(item.deliveryId).subscribe({
      next: (detail) => this.selectedDelivery.set({
        ...item,
        ...detail,
        httpStatusCode: detail.httpStatusCode ?? undefined,
        lastAttemptAt: detail.lastAttemptAt ?? undefined,
        payloadJson: detail.payload == null ? undefined : JSON.stringify(detail.payload, null, 2),
        requestHeaders: detail.requestHeaders || undefined,
        responseHeaders: detail.responseHeaders || undefined,
        responseBody: detail.responseBody || undefined,
      }),
      error: (error: HttpErrorResponse) => {
        this.deliveryError.set(this.apiErrorMessage(error, 'Unable to load delivery details.'));
      },
    });
  }

  retryDelivery(deliveryId: string): void {
    this.merchantService.retryDelivery(deliveryId).subscribe({
      next: (updated) => {
        this.notification.set(`Delivery retry accepted (${updated.status}).`);
        this.loadDeliveries();
        const selected = this.selectedDelivery();
        if (selected?.deliveryId === deliveryId) {
          this.inspectDelivery(selected);
        }
      },
      error: (error: HttpErrorResponse) => {
        this.deliveryError.set(this.apiErrorMessage(error, 'Unable to retry delivery.'));
      },
    });
  }

  private isHttpsUrl(value: string): boolean {
    try {
      const url = new URL(value);
      return url.protocol === 'https:' && !!url.hostname && !url.username && !url.password;
    } catch {
      return false;
    }
  }

  private isTestResult(value: unknown): value is TestWebhookResult {
    return typeof value === 'object' && value !== null &&
      'success' in value && 'statusCode' in value && 'latencyMs' in value;
  }

  private apiErrorMessage(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 400) return 'The URL or request was rejected. Check the destination and try again.';
    if (error.status === 401) return 'Your session has expired. Sign in and try again.';
    if (error.status === 403) return 'Your merchant account cannot perform this action.';
    return fallback;
  }

  getStatusBadgeClass(status: ParcelLifecycleStatus): string {
    switch (status) {
      case 'DELIVERED':
        return 'bg-emerald-100 text-emerald-800';
      case 'IN_TRANSIT':
      case 'PICKED_UP':
      case 'OUT_FOR_DELIVERY':
        return 'bg-amber-100 text-amber-800';
      case 'FAILED':
        return 'bg-rose-100 text-rose-800';
      case 'CANCELLED':
        return 'bg-slate-100 text-slate-800';
      default:
        return 'bg-indigo-100 text-indigo-800';
    }
  }

  getDeliveryBadgeClass(status: WebhookDeliveryStatus): string {
    switch (status) {
      case 'SUCCESS':
        return 'bg-emerald-100 text-emerald-800';
      case 'FAILED':
        return 'bg-rose-100 text-rose-800';
      case 'PENDING_RETRY':
        return 'bg-amber-100 text-amber-800';
      default:
        return 'bg-slate-100 text-slate-800';
    }
  }
}
