import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CreateStaffPayload,
  StaffUser,
} from '../dispatch/dispatch.model';
import { DispatchService } from '../dispatch/dispatch.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="max-w-7xl mx-auto space-y-6 pb-16">
      <!-- Top Bar Header -->
      <div
        class="bg-white/90 backdrop-blur-md rounded-2xl shadow-sm border border-slate-200/80 p-6 flex flex-col md:flex-row md:items-center justify-between gap-4"
      >
        <div class="flex items-center gap-4">
          <div
            class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-purple-600 to-indigo-600 flex items-center justify-center text-white shadow-md shadow-indigo-200 text-xl font-black"
          >
            👥
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-2xl font-extrabold text-slate-900 tracking-tight">
                Staff User Administration
              </h1>
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-purple-50 text-purple-700 border border-purple-200"
              >
                ADMIN Only
              </span>
            </div>
            <p class="text-xs sm:text-sm text-slate-500 font-medium">
              SwiftDrop Governance: Provision & Manage COURIER and DISPATCHER Accounts
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <a
            routerLink="/admin"
            class="px-4 py-2.5 rounded-xl border border-slate-200 text-slate-600 hover:text-slate-900 hover:bg-slate-50 text-sm font-semibold transition-colors"
          >
            ← Admin Overview
          </a>
          <button
            type="button"
            id="open-create-user-modal-btn"
            (click)="isCreateUserModalOpen.set(true)"
            class="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 text-white font-semibold text-sm shadow-sm hover:bg-indigo-700 transition-colors"
          >
            <span>➕</span>
            <span>Provision Staff Member</span>
          </button>
        </div>
      </div>

      <!-- Toast Feedback -->
      <div
        *ngIf="toastMessage()"
        class="p-4 rounded-xl text-sm font-semibold flex items-center justify-between border animate-fade-in"
        [class.bg-emerald-50]="toastType() === 'success'"
        [class.text-emerald-800]="toastType() === 'success'"
        [class.border-emerald-200]="toastType() === 'success'"
        [class.bg-rose-50]="toastType() === 'error'"
        [class.text-rose-800]="toastType() === 'error'"
        [class.border-rose-200]="toastType() === 'error'"
      >
        <div class="flex items-center gap-2">
          <span>{{ toastType() === 'success' ? '✅' : '⚠️' }}</span>
          <span>{{ toastMessage() }}</span>
        </div>
        <button
          type="button"
          (click)="toastMessage.set(null)"
          class="text-xs hover:opacity-75 font-bold"
        >
          ✕
        </button>
      </div>

      <!-- User Filters -->
      <div
        class="bg-white rounded-2xl shadow-sm border border-slate-200 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
      >
        <div class="flex items-center gap-3 flex-1 flex-wrap">
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
            <thead
              class="bg-slate-50 border-b border-slate-200 text-slate-600 font-bold uppercase tracking-wider text-[11px]"
            >
              <tr>
                <th class="px-5 py-3.5">User</th>
                <th class="px-5 py-3.5">Phone Number</th>
                <th class="px-5 py-3.5">Role</th>
                <th class="px-5 py-3.5">Status</th>
                <th class="px-5 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 text-slate-800">
              <tr
                *ngFor="let user of filteredUsers()"
                class="hover:bg-slate-50/80 transition-colors"
              >
                <td class="px-5 py-4">
                  <div class="font-extrabold text-slate-900">{{ user.fullName }}</div>
                  <div class="text-xs text-slate-400 font-medium">{{ user.email }}</div>
                </td>
                <td class="px-5 py-4 font-mono text-xs text-slate-600">
                  {{ user.phoneNumber }}
                </td>
                <td class="px-5 py-4">
                  <span
                    class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold border"
                    [class.bg-purple-50]="user.role === 'ADMIN'"
                    [class.text-purple-700]="user.role === 'ADMIN'"
                    [class.border-purple-200]="user.role === 'ADMIN'"
                    [class.bg-blue-50]="user.role === 'DISPATCHER'"
                    [class.text-blue-700]="user.role === 'DISPATCHER'"
                    [class.border-blue-200]="user.role === 'DISPATCHER'"
                    [class.bg-emerald-50]="user.role === 'COURIER'"
                    [class.text-emerald-700]="user.role === 'COURIER'"
                    [class.border-emerald-200]="user.role === 'COURIER'"
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

      <!-- ================================================================= -->
      <!-- MODAL: CREATE STAFF USER (COURIER / DISPATCHER) -->
      <!-- ================================================================= -->
      <div
        *ngIf="isCreateUserModalOpen()"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-fade-in"
      >
        <div
          class="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-md p-6 space-y-4"
        >
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <div>
              <h3 class="text-base font-extrabold text-slate-900">Provision Staff Account</h3>
              <p class="text-xs text-slate-500">Create a COURIER or DISPATCHER account (Decision 001)</p>
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
export class AdminUsersComponent {
  public dispatchService = inject(DispatchService);

  public userRoleFilter = signal('');
  public userStatusFilter = signal('');
  public isCreateUserModalOpen = signal(false);

  // Form State
  public newUserFullName = '';
  public newUserEmail = '';
  public newUserPhone = '';
  public newUserPassword = '';
  public newUserRole: 'COURIER' | 'DISPATCHER' = 'COURIER';

  // Toast State
  public toastMessage = signal<string | null>(null);
  public toastType = signal<'success' | 'error'>('success');

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

