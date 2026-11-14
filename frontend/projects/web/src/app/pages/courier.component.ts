import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from 'shared-auth';
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
    <!-- Full-Screen Courier Dashboard Layout (Matching Customer Portal) -->
    <div class="w-full min-h-screen flex flex-col md:flex-row bg-white">
      <!-- Slim Left Sidebar Navigation Rail (Docked & Sticky) -->
      <aside
        class="w-full md:w-20 flex-shrink-0 flex md:flex-col items-center justify-between md:justify-start py-4 md:py-7 px-4 md:px-0 border-b md:border-b-0 md:border-r border-[#f0ebe5] bg-[#faf8f6] md:sticky md:top-0 md:h-screen md:space-y-7 z-20"
      >
        <!-- Brand Logo Icon -->
        <a
          href="/"
          (click)="$event.preventDefault(); navigateTo('/')"
          class="w-11 h-11 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 group cursor-pointer hover:scale-105 transition-transform"
          title="SwiftDrop Home"
        >
          <svg class="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
            <line x1="12" y1="22.08" x2="12" y2="12"/>
          </svg>
        </a>

        <!-- Navigation Icons Stack -->
        <nav class="flex md:flex-col items-center gap-3 sm:gap-4" aria-label="Courier Navigation">
          <!-- 1. Grid / Deliveries Run Sheet Icon (Active) -->
          <a
            href="/courier"
            (click)="$event.preventDefault(); navigateTo('/courier')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#a53a26] bg-white shadow-2xs transition-all cursor-pointer no-underline"
            title="Courier Run Sheet"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="3" width="7" height="7" rx="2"></rect>
              <rect x="14" y="14" width="7" height="7" rx="2"></rect>
              <rect x="3" y="14" width="7" height="7" rx="2"></rect>
            </svg>
            <span class="hidden md:block absolute -right-[1px] top-2 bottom-2 w-1 bg-[#a53a26] rounded-l-full"></span>
          </a>

          <!-- 2. Profile & Availability Icon -->
          <a
            href="/courier/profile"
            (click)="$event.preventDefault(); navigateTo('/courier/profile')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Profile & Availability"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </a>

          <!-- 3. Daily Summary Icon -->
          <a
            href="/courier/summary"
            (click)="$event.preventDefault(); navigateTo('/courier/summary')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Daily Summary"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
          </a>

          <!-- 4. Track a Parcel Link -->
          <a
            href="/track"
            (click)="$event.preventDefault(); navigateTo('/track')"
            class="rail-icon-btn relative w-10 h-10 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-all cursor-pointer no-underline"
            title="Track a Parcel"
          >
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M16.5 9.4 7.55 4.24a1.78 1.78 0 0 0-2.5 1.55v12.42a1.78 1.78 0 0 0 2.5 1.55L16.5 14.6a1.78 1.78 0 0 0 0-3.2z"></path>
              <circle cx="18" cy="6" r="3"></circle>
            </svg>
          </a>
        </nav>

        <!-- Sidebar Footer Utilities -->
        <div class="hidden md:flex flex-col items-center gap-3 pt-4 border-t border-[#f0ebe5]/60 mt-auto">
          <button
            type="button"
            id="courier-signout-btn"
            (click)="signOut()"
            class="w-9 h-9 rounded-xl flex items-center justify-center text-[#6f6d85] hover:text-[#a53a26] hover:bg-white transition-colors cursor-pointer"
            title="Sign Out"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </button>
        </div>
      </aside>

      <!-- Main Content Area (Edge-to-Edge Full Screen) -->
      <main class="flex-1 min-h-screen p-6 sm:p-8 lg:p-10 space-y-7 min-w-0 bg-white">
        <!-- Top Header: Search Bar & User Profile (Matching Customer Portal) -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-[#f4efe9]">
          <!-- Search Input -->
          <div class="relative flex-1 max-w-lg">
            <svg
              class="w-4 h-4 text-[#a09eaf] absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
            >
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input
              type="text"
              id="search-input"
              [ngModel]="searchQuery()"
              (ngModelChange)="searchQuery.set($event)"
              placeholder="Search for tracking numbers, recipients, addresses ..."
              class="w-full pl-11 pr-8 py-2.5 rounded-full bg-[#faf8f6] border border-[#e8e2dc] text-xs font-semibold text-[#1d1b2c] placeholder-[#a09eaf] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] transition-all"
            />
            <button
              *ngIf="searchQuery()"
              type="button"
              (click)="searchQuery.set('')"
              class="absolute right-3.5 top-1/2 -translate-y-1/2 text-[#a09eaf] hover:text-[#1d1b2c] text-xs font-bold"
            >
              ✕
            </button>
          </div>

          <!-- User Profile Dropdown (Matching Customer Portal) -->
          <div class="relative flex items-center gap-3 self-end sm:self-auto">
            <button
              type="button"
              id="profile-menu-btn"
              (click)="toggleProfileMenu()"
              aria-haspopup="menu"
              [attr.aria-expanded]="profileMenuOpen()"
              class="flex items-center gap-2.5 px-3 py-1.5 rounded-full bg-[#faf8f6] border border-[#e8e2dc] hover:border-[#a53a26]/40 transition-colors cursor-pointer"
            >
              <span
                class="w-8 h-8 rounded-full bg-[#a53a26] text-white flex items-center justify-center font-bold text-xs uppercase shadow-sm"
              >
                {{ courierInitials() }}
              </span>
              <span class="text-xs font-bold text-[#1d1b2c]">{{ courierDisplayName() }}</span>
              <svg
                class="w-3.5 h-3.5 text-[#6f6d85] transition-transform"
                [class.rotate-180]="profileMenuOpen()"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
              >
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>

            <div
              *ngIf="profileMenuOpen()"
              role="menu"
              id="profile-menu-panel"
              class="absolute right-0 top-full mt-2 w-56 rounded-2xl bg-white border border-[#e8e2dc] shadow-lg shadow-[#1d1b2c]/5 py-2 z-30"
            >
              <div class="px-4 py-2 border-b border-[#f0ebe5]">
                <p class="text-xs font-bold text-[#1d1b2c] truncate">{{ courierDisplayName() }}</p>
                <p class="text-[11px] text-[#6f6d85] truncate">{{ courierEmail() }}</p>
              </div>
              <button
                type="button"
                role="menuitem"
                id="profile-menu-settings"
                (click)="openSettings()"
                class="w-full flex items-center gap-2.5 px-4 py-2.5 text-xs font-bold text-[#1d1b2c] hover:bg-[#faf8f6] hover:text-[#a53a26] transition-colors cursor-pointer"
              >
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="3"></circle>
                  <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                </svg>
                <span>Settings</span>
              </button>
              <button
                type="button"
                role="menuitem"
                id="profile-menu-logout"
                (click)="logOutFromMenu()"
                class="w-full flex items-center gap-2.5 px-4 py-2.5 text-xs font-bold text-[#a53a26] hover:bg-[#faf8f6] transition-colors cursor-pointer"
              >
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                  <polyline points="16 17 21 12 16 7"></polyline>
                  <line x1="21" y1="12" x2="9" y2="12"></line>
                </svg>
                <span>Log out</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Courier Top Bar Header -->
        <div
          class="portal-header bg-white rounded-3xl p-6 sm:p-7 border border-[#e8e2dc] shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-5"
        >
          <div class="flex items-center gap-4">
            <div
              class="brand-badge w-12 h-12 rounded-2xl bg-[#a53a26] text-white flex items-center justify-center shadow-md shadow-[#a53a26]/20 flex-shrink-0"
            >
              <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                <rect x="1" y="3" width="15" height="13" rx="2"></rect>
                <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon>
                <circle cx="5.5" cy="18.5" r="2.5"></circle>
                <circle cx="18.5" cy="18.5" r="2.5"></circle>
              </svg>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="portal-pill">Courier Portal</span>
                <span
                  class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/80"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span> Online
                </span>
              </div>
              <h1 class="portal-title text-2xl font-black text-[#1d1b2c] tracking-tight mt-0.5">
                Courier Run Sheet
              </h1>
              <p class="text-xs font-semibold text-[#6f6d85] mt-0.5">
                {{ profile().name }} (ID: {{ profile().userId }}) • {{ profile().zone || 'Assigned Zone' }}
              </p>
            </div>
          </div>

          <!-- Quick Summary Chips -->
          <div class="flex items-center gap-2.5 overflow-x-auto pb-1 sm:pb-0">
            <div
              class="stat-pill px-4 py-2 rounded-2xl bg-[#faf8f6] border border-[#e8e2dc] text-center min-w-[80px]"
            >
              <p class="text-[10px] uppercase font-bold text-[#6f6d85] tracking-wider">Total Run</p>
              <p class="text-lg font-black text-[#1d1b2c]">{{ stats().total }}</p>
            </div>
            <div
              class="stat-pill px-4 py-2 rounded-2xl bg-[#fffbeb] border border-[#fef3c7] text-center min-w-[80px]"
            >
              <p class="text-[10px] uppercase font-bold text-amber-700 tracking-wider">Pending</p>
              <p class="text-lg font-black text-amber-800">
                {{ stats().created + stats().pickedUp + stats().inTransit + stats().outForDelivery }}
              </p>
            </div>
            <div
              class="stat-pill px-4 py-2 rounded-2xl bg-[#ecfdf5] border border-[#d1fae5] text-center min-w-[80px]"
            >
              <p class="text-[10px] uppercase font-bold text-emerald-700 tracking-wider">Delivered</p>
              <p class="text-lg font-black text-emerald-800">{{ stats().delivered }}</p>
            </div>
            <button
              type="button"
              id="refresh-btn"
              (click)="refreshDeliveries()"
              [disabled]="isLoading()"
              title="Refresh Deliveries"
              class="p-3 rounded-2xl border border-[#e8e2dc] bg-[#faf8f6] hover:bg-white text-[#6f6d85] hover:text-[#a53a26] transition-all shadow-xs cursor-pointer"
            >
              <span [class.animate-spin]="isLoading()" class="inline-block text-base">🔄</span>
            </button>
          </div>
        </div>

        <!-- Filter Tabs (Segmented Pill Bar matching Customer Portal) -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div
            class="tab-pill-bar inline-flex rounded-full bg-[#f1f5f9] p-1.5 border border-[#e2e8f0]/80 text-sm font-bold overflow-x-auto"
          >
            <button
              type="button"
              id="tab-filter-all"
              (click)="activeFilter.set('ALL')"
              [class.active]="activeFilter() === 'ALL'"
              class="tab-pill px-4 py-2 rounded-full transition-all flex items-center gap-1.5 font-bold text-xs sm:text-sm whitespace-nowrap cursor-pointer"
            >
              <span>All</span>
              <span
                class="count-badge ml-1 text-xs px-2 py-0.5 rounded-full"
                [class.active-badge]="activeFilter() === 'ALL'"
              >
                {{ stats().total }}
              </span>
            </button>
            <button
              type="button"
              id="tab-filter-active"
              (click)="activeFilter.set('ACTIVE')"
              [class.active]="activeFilter() === 'ACTIVE'"
              class="tab-pill px-4 py-2 rounded-full transition-all flex items-center gap-1.5 font-bold text-xs sm:text-sm whitespace-nowrap cursor-pointer"
            >
              <span>Active</span>
              <span
                class="count-badge ml-1 text-xs px-2 py-0.5 rounded-full"
                [class.active-badge]="activeFilter() === 'ACTIVE'"
              >
                {{ stats().created + stats().pickedUp + stats().inTransit + stats().outForDelivery }}
              </span>
            </button>
            <button
              type="button"
              id="tab-filter-delivered"
              (click)="activeFilter.set('DELIVERED')"
              [class.active]="activeFilter() === 'DELIVERED'"
              class="tab-pill px-4 py-2 rounded-full transition-all flex items-center gap-1.5 font-bold text-xs sm:text-sm whitespace-nowrap cursor-pointer"
            >
              <span>Delivered</span>
              <span
                class="count-badge ml-1 text-xs px-2 py-0.5 rounded-full"
                [class.active-badge]="activeFilter() === 'DELIVERED'"
              >
                {{ stats().delivered }}
              </span>
            </button>
            <button
              type="button"
              id="tab-filter-failed"
              (click)="activeFilter.set('FAILED')"
              [class.active]="activeFilter() === 'FAILED'"
              class="tab-pill px-4 py-2 rounded-full transition-all flex items-center gap-1.5 font-bold text-xs sm:text-sm whitespace-nowrap cursor-pointer"
            >
              <span>Failed</span>
              <span
                class="count-badge ml-1 text-xs px-2 py-0.5 rounded-full"
                [class.active-badge]="activeFilter() === 'FAILED'"
              >
                {{ stats().failed }}
              </span>
            </button>
          </div>
        </div>

        <!-- Toast Notification Alert -->
        <div
          *ngIf="toastMessage()"
          id="toast-notification"
          class="toast-alert bg-[#faf6f0] border border-[#a53a26]/20 text-[#1d1b2c] px-5 py-3.5 rounded-2xl flex items-center justify-between text-sm shadow-sm transition-all"
        >
          <div class="flex items-center gap-3 font-semibold">
            <div
              class="w-7 h-7 rounded-full bg-[#a53a26]/10 text-[#a53a26] flex items-center justify-center flex-shrink-0"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
            </div>
            <span>{{ toastMessage() }}</span>
          </div>
          <button
            type="button"
            (click)="toastMessage.set('')"
            class="text-[#6f6d85] hover:text-[#1d1b2c] font-bold p-1 rounded-lg transition-colors cursor-pointer"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>

        <!-- Error State Alert -->
        <div
          *ngIf="errorMessage()"
          id="courier-error-banner"
          class="rounded-2xl p-4 bg-rose-50 border border-rose-200 text-rose-900 text-sm flex items-center justify-between shadow-sm"
        >
          <div class="flex items-center gap-2.5">
            <span class="text-base">⚠️</span>
            <span class="font-semibold">{{ errorMessage() }}</span>
          </div>
          <button
            type="button"
            (click)="refreshDeliveries()"
            class="px-4 py-1.5 bg-white border border-rose-200 rounded-full text-xs font-bold text-rose-700 hover:bg-rose-100 transition-colors shadow-2xs cursor-pointer"
          >
            Retry
          </button>
        </div>

        <!-- Loading State Indicator -->
        <div
          *ngIf="isLoading() && filteredDeliveries().length === 0"
          id="courier-loading-spinner"
          class="bg-white rounded-3xl border border-[#e8e2dc] p-12 text-center shadow-sm"
        >
          <div
            class="w-16 h-16 rounded-2xl bg-[#faf6f0] text-[#a53a26] flex items-center justify-center mx-auto mb-4 text-2xl shadow-sm"
          >
            🚚
          </div>
          <h3 class="text-base font-bold text-[#1d1b2c]">Loading deliveries...</h3>
          <p class="text-xs text-[#6f6d85] mt-1">Fetching your assigned parcels from operations service.</p>
        </div>

        <!-- Deliveries List (Cards) -->
        <div *ngIf="!isLoading() || filteredDeliveries().length > 0" class="space-y-4">
          <div
            *ngFor="let delivery of filteredDeliveries()"
            [id]="'delivery-card-' + delivery.parcelId"
            class="delivery-run-card bg-white rounded-3xl shadow-sm border border-[#eee7e0] hover:border-[#a53a26]/40 hover:shadow-md transition-all overflow-hidden"
          >
            <!-- Card Header -->
            <div
              class="px-5 sm:px-6 py-4 flex flex-wrap items-center justify-between gap-3 border-b border-[#f4efe9] bg-[#faf8f6]/60"
            >
              <div class="flex items-center gap-3">
                <span
                  class="text-xs font-mono font-black px-3 py-1 bg-white border border-[#e8e2dc] rounded-full text-[#1d1b2c] shadow-2xs"
                >
                  {{ delivery.trackingNumber }}
                </span>
                <span
                  [ngClass]="getStatusBadgeClass(delivery.status)"
                  class="px-3 py-0.5 rounded-full text-xs font-extrabold uppercase tracking-wider flex items-center gap-1.5"
                >
                  <span class="w-1.5 h-1.5 rounded-full bg-current opacity-80"></span>
                  {{ getStatusLabel(delivery.status) }}
                </span>
              </div>

              <div class="flex items-center gap-2 text-xs text-[#6f6d85]">
                <span
                  *ngIf="delivery.attemptCount !== undefined"
                  class="font-bold bg-white border border-[#e8e2dc] px-2.5 py-0.5 rounded-full"
                >
                  Attempt: {{ delivery.attemptCount }}
                </span>
                <span
                  *ngIf="delivery.weightKg"
                  class="font-bold bg-white border border-[#e8e2dc] px-2.5 py-0.5 rounded-full"
                >
                  ⚖️ {{ delivery.weightKg }} kg
                </span>
              </div>
            </div>

            <!-- Card Body -->
            <div class="p-5 sm:p-6 grid grid-cols-1 md:grid-cols-12 gap-5">
              <!-- Recipient & Destination -->
              <div class="md:col-span-7 space-y-3">
                <div class="flex items-baseline justify-between gap-2">
                  <h3 class="text-base font-black text-[#1d1b2c] flex items-center gap-2">
                    <span
                      class="w-6 h-6 rounded-full bg-[#faf6f0] text-[#a53a26] flex items-center justify-center text-xs"
                    >👤</span>
                    {{ delivery.recipientName }}
                  </h3>
                  <a
                    *ngIf="delivery.recipientPhone"
                    [href]="'tel:' + delivery.recipientPhone"
                    class="inline-flex items-center gap-1.5 text-xs font-bold text-[#a53a26] hover:text-[#7e2615] bg-[#faf6f0] hover:bg-[#faf0e6] border border-[#a53a26]/20 px-3 py-1 rounded-full transition-colors"
                  >
                    📞 Call ({{ delivery.recipientPhone }})
                  </a>
                </div>

                <!-- Address -->
                <div class="text-xs sm:text-sm text-[#6f6d85] flex items-start gap-2">
                  <span class="text-[#a53a26] mt-0.5 font-bold">📍</span>
                  <div>
                    <p class="font-semibold text-[#1d1b2c]">
                      {{ delivery.dropoffAddress || (delivery.recipientAddress?.line1) }}
                      <span *ngIf="delivery.recipientAddress?.city">, {{ delivery.recipientAddress?.city }}</span>
                    </p>
                    <a
                      *ngIf="delivery.googleMapsUrl"
                      [href]="delivery.googleMapsUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="text-xs font-bold text-[#a53a26] hover:underline inline-flex items-center gap-1 mt-1"
                    >
                      🗺️ Open in Google Maps
                    </a>
                  </div>
                </div>

                <!-- Special Instructions -->
                <div
                  *ngIf="delivery.specialInstructions"
                  class="p-3 bg-[#fffbeb] border border-[#fef3c7] rounded-2xl text-xs text-amber-900 flex items-start gap-2"
                >
                  <span class="text-amber-500 font-bold">📝</span>
                  <p><span class="font-bold">Instructions:</span> {{ delivery.specialInstructions }}</p>
                </div>

                <!-- Failure Reason if any -->
                <div
                  *ngIf="(delivery.status === 'FAILED' || delivery.status === 'FAILED_DELIVERY') && delivery.failureReason"
                  class="p-3 bg-rose-50 border border-rose-200 rounded-2xl text-xs text-rose-900 flex items-start gap-2"
                >
                  <span class="text-rose-500 font-bold">⚠️</span>
                  <p><span class="font-bold">Failure Reason:</span> {{ delivery.failureReason }}</p>
                </div>

                <!-- Proof Thumbnail if delivered -->
                <div
                  *ngIf="delivery.status === 'DELIVERED' && delivery.proof"
                  class="p-3 bg-[#ecfdf5] border border-[#d1fae5] rounded-2xl text-xs text-emerald-900 flex items-center justify-between gap-3"
                >
                  <div class="flex items-center gap-2.5">
                    <span class="text-emerald-600 text-lg">📸</span>
                    <div>
                      <p class="font-extrabold text-[#1d1b2c]">Proof of Delivery Captured</p>
                      <p class="text-[11px] text-emerald-700 font-semibold">
                        Received by {{ delivery.proof.recipientName }}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    (click)="viewProofDetail(delivery)"
                    class="px-3 py-1.5 bg-white rounded-full border border-emerald-300 font-bold text-emerald-800 hover:bg-emerald-50 text-[11px] transition-colors shadow-2xs cursor-pointer"
                  >
                    View Details
                  </button>
                </div>
              </div>

              <!-- Sender / Origin Info & Action Buttons -->
              <div
                class="md:col-span-5 flex flex-col justify-between border-t md:border-t-0 md:border-l border-[#f4efe9] md:pl-5 pt-4 md:pt-0 space-y-4"
              >
                <div class="text-xs text-[#6f6d85]">
                  <p class="text-[10px] uppercase font-black text-[#a09eaf] tracking-wider">Pickup Origin</p>
                  <p class="font-bold text-[#1d1b2c] mt-0.5">{{ delivery.senderName || 'SwiftDrop Merchant' }}</p>
                  <p class="text-xs text-[#6f6d85]">{{ delivery.pickupAddress || (delivery.senderAddress?.line1) }}</p>
                </div>

                <!-- Action Buttons -->
                <div class="pt-2 space-y-2.5">
                  <!-- If CREATED or ASSIGNED -> Can Confirm Picked Up -->
                  <button
                    *ngIf="delivery.status === 'CREATED' || delivery.status === 'ASSIGNED'"
                    type="button"
                    [id]="'pickup-btn-' + delivery.parcelId"
                    [disabled]="isActionPending(delivery.parcelId)"
                    (click)="advanceStatus(delivery, 'PICKED_UP')"
                    class="action-pill-btn w-full py-2.5 px-4 rounded-full bg-[#a53a26] hover:bg-[#7e2615] disabled:opacity-40 text-white font-bold text-xs sm:text-sm shadow-md shadow-[#a53a26]/20 transition-all flex items-center justify-center gap-2 cursor-pointer"
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
                    class="action-pill-btn w-full py-2.5 px-4 rounded-full bg-[#1d1b2c] hover:bg-[#2d2b3d] disabled:opacity-40 text-white font-bold text-xs sm:text-sm shadow-md shadow-[#1d1b2c]/20 transition-all flex items-center justify-center gap-2 cursor-pointer"
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
                    class="action-pill-btn w-full py-2.5 px-4 rounded-full bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white font-bold text-xs sm:text-sm shadow-md shadow-amber-600/20 transition-all flex items-center justify-center gap-2 cursor-pointer"
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
                      class="action-pill-btn w-full py-2.5 px-4 rounded-full bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 text-white font-bold text-xs sm:text-sm shadow-md shadow-emerald-600/20 transition-all flex items-center justify-center gap-2 cursor-pointer"
                    >
                      <span>📸</span> Complete & Upload Proof
                    </button>
                    <button
                      type="button"
                      [id]="'fail-btn-' + delivery.parcelId"
                      [disabled]="isActionPending(delivery.parcelId)"
                      (click)="openFailModal(delivery)"
                      class="w-full py-2 px-3 rounded-full bg-[#f1f5f9] hover:bg-rose-50 disabled:opacity-40 text-[#6f6d85] hover:text-rose-700 font-bold text-xs border border-[#e2e8f0] hover:border-rose-200 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <span>⚠️</span> Mark Delivery Attempt Failed
                    </button>
                  </div>

                  <!-- If DELIVERED -> Done Badge -->
                  <div
                    *ngIf="delivery.status === 'DELIVERED'"
                    class="w-full py-2.5 px-4 rounded-full bg-[#ecfdf5] text-emerald-800 text-xs font-extrabold text-center border border-[#d1fae5] shadow-2xs"
                  >
                    ✅ Completed & Confirmed
                  </div>

                  <!-- If FAILED -> Attempt Recorded -->
                  <div
                    *ngIf="delivery.status === 'FAILED' || delivery.status === 'FAILED_DELIVERY'"
                    class="w-full py-2.5 px-4 rounded-full bg-rose-50 text-rose-800 text-xs font-extrabold text-center border border-rose-200 shadow-2xs"
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
            class="bg-white rounded-3xl border border-dashed border-[#e8e2dc] p-12 text-center shadow-sm"
          >
            <div
              class="w-16 h-16 rounded-2xl bg-[#faf6f0] text-[#a53a26] flex items-center justify-center mx-auto mb-3 text-2xl shadow-sm"
            >
              📦
            </div>
            <h3 class="text-base font-black text-[#1d1b2c]">No deliveries found</h3>
            <p class="text-xs text-[#6f6d85] mt-1 max-w-sm mx-auto">
              No parcels match the current filter or search criteria.
            </p>
            <button
              type="button"
              (click)="activeFilter.set('ALL'); searchQuery.set('')"
              class="mt-4 px-5 py-2 bg-[#faf6f0] text-[#a53a26] hover:bg-[#a53a26] hover:text-white font-bold text-xs rounded-full border border-[#a53a26]/20 transition-all cursor-pointer"
            >
              Clear Filters
            </button>
          </div>
        </div>

        <!-- Proof of Delivery Modal Dialog -->
        <div
          *ngIf="selectedDeliveryForProof()"
          class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1d1b2c]/60 backdrop-blur-xs animate-fade-in"
        >
          <div
            class="bg-white rounded-3xl shadow-xl border border-[#e8e2dc] w-full max-w-md overflow-hidden transform transition-all"
          >
            <!-- Modal Header -->
            <div class="p-6 border-b border-[#f4efe9] flex items-center justify-between bg-[#faf8f6]/80">
              <div>
                <div class="flex items-center gap-2">
                  <span class="portal-pill">Proof of Delivery</span>
                </div>
                <h2 class="text-lg font-black text-[#1d1b2c] flex items-center gap-2 mt-1">
                  <span>📸</span> Complete Delivery
                </h2>
                <p class="text-xs text-[#6f6d85] font-mono mt-0.5">
                  Tracking: {{ selectedDeliveryForProof()?.trackingNumber }}
                </p>
              </div>
              <button
                type="button"
                id="close-proof-modal"
                (click)="closeProofModal()"
                class="w-8 h-8 rounded-full bg-white hover:bg-[#faf6f0] text-[#6f6d85] hover:text-[#1d1b2c] border border-[#e8e2dc] flex items-center justify-center text-sm font-bold transition-colors cursor-pointer"
              >
                ✕
              </button>
            </div>

            <!-- Modal Body Form -->
            <div class="p-6 space-y-4">
              <!-- Recipient Name -->
              <div>
                <label class="block text-xs font-bold uppercase tracking-wider text-[#6f6d85] mb-1.5">
                  Received By (Full Name) *
                </label>
                <input
                  type="text"
                  id="proof-recipient-name"
                  [(ngModel)]="proofRecipientName"
                  placeholder="Enter recipient or signatory name"
                  class="w-full px-4 py-2.5 rounded-2xl border border-[#e8e2dc] text-sm font-semibold text-[#1d1b2c] placeholder-[#a09eaf] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] transition-all"
                />
              </div>

              <!-- Notes -->
              <div>
                <label class="block text-xs font-bold uppercase tracking-wider text-[#6f6d85] mb-1.5">
                  Delivery Notes / Remarks
                </label>
                <textarea
                  id="proof-notes"
                  [(ngModel)]="proofDeliveryNotes"
                  rows="2"
                  placeholder="e.g. Left with security, signed at front door"
                  class="w-full px-4 py-2.5 rounded-2xl border border-[#e8e2dc] text-sm font-semibold text-[#1d1b2c] placeholder-[#a09eaf] focus:outline-none focus:ring-2 focus:ring-[#a53a26]/20 focus:border-[#a53a26] transition-all"
                ></textarea>
              </div>

              <!-- Photo Upload Area -->
              <div>
                <label class="block text-xs font-bold uppercase tracking-wider text-[#6f6d85] mb-1.5">
                  Photo Proof (JPEG, PNG, WEBP - Max 5MB)
                </label>

                <div
                  *ngIf="!proofImagePreview"
                  class="border-2 border-dashed border-[#e8e2dc] hover:border-[#a53a26] rounded-2xl p-6 text-center cursor-pointer transition-colors bg-[#faf8f6]/50"
                  (click)="fileInput.click()"
                >
                  <div class="text-3xl mb-1">📷</div>
                  <p class="text-xs font-bold text-[#a53a26]">Click or tap to capture/upload photo</p>
                  <p class="text-[11px] text-[#a09eaf] mt-0.5">JPEG, PNG or WEBP up to 5MB</p>
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
                <div
                  *ngIf="proofImagePreview"
                  class="relative rounded-2xl overflow-hidden border border-[#e8e2dc] bg-[#faf8f6]"
                >
                  <img
                    [src]="proofImagePreview"
                    alt="Proof preview"
                    class="w-full h-48 object-cover"
                  />
                  <button
                    type="button"
                    (click)="clearProofFile()"
                    class="absolute top-2 right-2 px-3 py-1 bg-[#1d1b2c]/80 hover:bg-[#1d1b2c] text-white rounded-full text-xs font-bold transition-colors cursor-pointer"
                  >
                    ✕ Remove
                  </button>
                </div>

                <p *ngIf="fileError()" class="text-xs text-rose-600 mt-1 font-bold">
                  {{ fileError() }}
                </p>
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="p-5 border-t border-[#f4efe9] bg-[#faf8f6]/80 flex items-center justify-end gap-2.5">
              <button
                type="button"
                (click)="closeProofModal()"
                class="px-5 py-2 rounded-full border border-[#e8e2dc] text-[#6f6d85] hover:bg-white text-xs font-bold transition-all cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                id="submit-proof-btn"
                (click)="submitProof()"
                [disabled]="isSubmittingProof || !isProofFormValid()"
                class="action-pill-btn px-6 py-2 rounded-full bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 text-white text-xs font-bold shadow-md shadow-emerald-600/20 transition-all flex items-center gap-1.5 cursor-pointer"
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
          class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1d1b2c]/60 backdrop-blur-xs animate-fade-in"
        >
          <div
            class="bg-white rounded-3xl shadow-xl border border-[#e8e2dc] w-full max-w-md overflow-hidden transform transition-all"
          >
            <!-- Modal Header -->
            <div class="p-6 border-b border-rose-100 flex items-center justify-between bg-rose-50/50">
              <div>
                <span class="inline-block px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-rose-100 text-rose-700">
                  Failed Attempt
                </span>
                <h2 class="text-lg font-black text-[#1d1b2c] flex items-center gap-2 mt-1">
                  <span>⚠️</span> Record Failed Attempt
                </h2>
                <p class="text-xs text-[#6f6d85] font-mono mt-0.5">
                  Tracking: {{ selectedDeliveryForFailure()?.trackingNumber }}
                </p>
              </div>
              <button
                type="button"
                id="close-fail-modal"
                (click)="closeFailModal()"
                class="w-8 h-8 rounded-full bg-white hover:bg-rose-100 text-[#6f6d85] hover:text-rose-700 border border-rose-200 flex items-center justify-center text-sm font-bold transition-colors cursor-pointer"
              >
                ✕
              </button>
            </div>

            <!-- Modal Body -->
            <div class="p-6 space-y-4">
              <div>
                <label class="block text-xs font-bold uppercase tracking-wider text-[#6f6d85] mb-1.5">
                  Reason Category *
                </label>
                <select
                  id="fail-reason-select"
                  [(ngModel)]="failureReasonCategory"
                  class="w-full px-4 py-2.5 rounded-2xl border border-[#e8e2dc] text-sm font-semibold text-[#1d1b2c] focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 bg-white transition-all"
                >
                  <option value="RECIPIENT_UNAVAILABLE">Recipient unavailable / phone unreachable</option>
                  <option value="INCORRECT_ADDRESS">Incorrect address / premises not found</option>
                  <option value="CUSTOMER_REJECTED">Customer refused delivery</option>
                  <option value="ACCESS_RESTRICTED">Premises closed / access restricted</option>
                  <option value="OTHER">Other reason (describe below)</option>
                </select>
              </div>

              <div>
                <label class="block text-xs font-bold uppercase tracking-wider text-[#6f6d85] mb-1.5">
                  Additional Notes
                </label>
                <textarea
                  id="fail-notes"
                  [(ngModel)]="failureNotes"
                  rows="3"
                  placeholder="Details of the delivery attempt..."
                  class="w-full px-4 py-2.5 rounded-2xl border border-[#e8e2dc] text-sm font-semibold text-[#1d1b2c] placeholder-[#a09eaf] focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 transition-all"
                ></textarea>
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="p-5 border-t border-[#f4efe9] bg-[#faf8f6]/80 flex items-center justify-end gap-2.5">
              <button
                type="button"
                (click)="closeFailModal()"
                class="px-5 py-2 rounded-full border border-[#e8e2dc] text-[#6f6d85] hover:bg-white text-xs font-bold transition-all cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                id="submit-fail-btn"
                (click)="submitFailure()"
                [disabled]="isSubmittingFailure || (failureReasonCategory === 'OTHER' && !failureNotes.trim())"
                class="action-pill-btn px-6 py-2 rounded-full bg-rose-600 hover:bg-rose-700 disabled:opacity-40 text-white text-xs font-bold shadow-md shadow-rose-600/20 transition-all flex items-center gap-1.5 cursor-pointer"
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
          class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1d1b2c]/60 backdrop-blur-xs animate-fade-in"
        >
          <div
            class="bg-white rounded-3xl shadow-xl border border-[#e8e2dc] w-full max-w-lg overflow-hidden transform transition-all"
          >
            <!-- Modal Header -->
            <div class="p-6 border-b border-[#f4efe9] flex items-center justify-between bg-[#faf8f6]/80">
              <div>
                <div class="flex items-center gap-2">
                  <span class="portal-pill">Proof Verified</span>
                </div>
                <h2 class="text-lg font-black text-[#1d1b2c] flex items-center gap-2 mt-1">
                  <span>📸</span> Delivery Proof Details
                </h2>
                <p class="text-xs text-[#6f6d85] font-mono mt-0.5">
                  Tracking: {{ selectedDeliveryForProofView()?.trackingNumber }}
                </p>
              </div>
              <button
                type="button"
                id="close-proof-view-modal"
                (click)="closeProofViewModal()"
                class="w-8 h-8 rounded-full bg-white hover:bg-[#faf6f0] text-[#6f6d85] hover:text-[#1d1b2c] border border-[#e8e2dc] flex items-center justify-center text-sm font-bold transition-colors cursor-pointer"
              >
                ✕
              </button>
            </div>

            <!-- Modal Body -->
            <div class="p-6 space-y-4">
              <div class="bg-[#faf8f6] rounded-2xl p-4 border border-[#e8e2dc] text-xs space-y-1.5">
                <p>
                  <span class="font-bold text-[#6f6d85]">Recipient Name:</span>
                  <span class="font-black text-[#1d1b2c] ml-1">
                    {{ selectedDeliveryForProofView()?.proof?.recipientName }}
                  </span>
                </p>
                <p>
                  <span class="font-bold text-[#6f6d85]">Uploaded At:</span>
                  <span class="font-semibold text-[#1d1b2c] ml-1">
                    {{ selectedDeliveryForProofView()?.proof?.uploadedAt | date: 'medium' }}
                  </span>
                </p>
              </div>

              <div
                class="relative rounded-2xl overflow-hidden border border-[#e8e2dc] bg-[#faf8f6] min-h-[180px] flex items-center justify-center"
              >
                <div *ngIf="isLoadingProofImage()" class="text-xs text-[#6f6d85] font-bold flex items-center gap-2">
                  <span class="animate-spin">🔄</span> Loading authorized proof photo...
                </div>
                <img
                  *ngIf="!isLoadingProofImage() && viewProofImageUrl()"
                  [src]="viewProofImageUrl()"
                  alt="Delivery proof"
                  id="view-proof-image"
                  class="w-full max-h-72 object-contain bg-[#1d1b2c]"
                />
                <p *ngIf="!isLoadingProofImage() && !viewProofImageUrl()" class="text-xs text-[#a09eaf] font-semibold">
                  Photo preview not available.
                </p>
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="p-5 border-t border-[#f4efe9] bg-[#faf8f6]/80 flex items-center justify-end">
              <button
                type="button"
                (click)="closeProofViewModal()"
                class="action-pill-btn px-6 py-2 rounded-full bg-[#1d1b2c] hover:bg-[#2d2b3d] text-white text-xs font-bold transition-all shadow-sm cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      </main>
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
      .action-pill-btn {
        transition: all 0.2s ease;
      }
      .action-pill-btn:hover:not(:disabled) {
        transform: translateY(-1px);
      }
      .rail-icon-btn.rail-active {
        color: var(--terracotta);
        background: #fff;
      }
    `,
  ],
})
export class CourierComponent implements OnInit {
  private courierService = inject(CourierService);
  private authService = inject(AuthService, { optional: true });
  private router = inject(Router, { optional: true });

  readonly stats = this.courierService.stats;
  readonly profile = this.courierService.profile;
  readonly isLoading = this.courierService.isLoading;
  readonly errorMessage = this.courierService.error;

  activeFilter = signal<FilterTab>('ALL');
  searchQuery = signal<string>('');
  toastMessage = signal<string>('');
  profileMenuOpen = signal<boolean>(false);

  readonly courierDisplayName = computed(() => {
    return this.authService?.currentUser()?.fullName || this.profile().name || 'Sample Courier';
  });

  readonly courierEmail = computed(() => {
    return this.authService?.currentUser()?.email || 'courier@swiftdrop.lk';
  });

  readonly courierInitials = computed(() => {
    const name = this.courierDisplayName();
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

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
      error: () => {
        // Handled in service signal
      },
    });
  }

  navigateTo(path: string): void {
    if (this.router) {
      this.router.navigate([path]);
    } else if (typeof window !== 'undefined') {
      window.location.href = path;
    }
  }

  toggleProfileMenu(): void {
    this.profileMenuOpen.update((open) => !open);
  }

  openSettings(): void {
    this.profileMenuOpen.set(false);
    this.navigateTo('/courier/profile');
  }

  logOutFromMenu(): void {
    this.profileMenuOpen.set(false);
    this.signOut();
  }

  signOut(): void {
    if (this.authService) {
      this.authService.logout().subscribe({
        next: () => this.navigateTo('/login'),
        error: () => this.navigateTo('/login'),
      });
    } else {
      this.navigateTo('/login');
    }
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
        return 'bg-amber-50 text-amber-800 border border-amber-200/80';
      case 'PICKED_UP':
        return 'bg-[#faf6f0] text-[#a53a26] border border-[#a53a26]/20';
      case 'IN_TRANSIT':
        return 'bg-blue-50 text-blue-800 border border-blue-200/80';
      case 'OUT_FOR_DELIVERY':
        return 'bg-[#fffbeb] text-amber-800 border border-amber-300';
      case 'DELIVERED':
        return 'bg-emerald-50 text-emerald-800 border border-emerald-200/80';
      case 'FAILED':
      case 'FAILED_DELIVERY':
        return 'bg-rose-50 text-rose-800 border border-rose-200/80';
      default:
        return 'bg-slate-50 text-slate-800 border border-slate-200';
    }
  }

  advanceStatus(delivery: CourierDelivery, nextStatus: DeliveryStatus): void {
    if (this.isActionPending(delivery.parcelId)) return;

    this.setActionPending(delivery.parcelId, true);
    const idempotencyKey = `ui-${delivery.parcelId}-${nextStatus}-${Date.now()}`;

    this.courierService.updateStatus(delivery.parcelId, { status: nextStatus, idempotencyKey }).subscribe({
      next: () => {
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
