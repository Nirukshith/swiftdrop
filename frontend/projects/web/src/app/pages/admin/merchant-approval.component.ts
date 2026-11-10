import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MerchantApprovalService } from './merchant-approval.service';
import { MerchantSummary } from './merchant-approval.model';
import { MerchantCredentialsComponent } from '../merchant/merchant-credentials.component';
import { MerchantApiCredentials } from '../merchant/merchant.model';

type Action = 'approve' | 'reject';

@Component({
  selector: 'app-merchant-approval',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MerchantCredentialsComponent],
  template: `
    <main class="mx-auto max-w-7xl space-y-6 pb-16">
      <header class="rounded-2xl bg-slate-900 p-6 text-white shadow-sm">
        <div class="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <p class="text-sm font-medium text-indigo-300">SwiftDrop governance</p>
            <h1 class="mt-1 text-3xl font-bold">Merchant approval</h1>
            <p class="mt-2 text-slate-300">Review pending merchant registrations before they receive API access.</p>
          </div>
          <span class="inline-flex w-fit items-center rounded-full border border-purple-400/30 bg-purple-500/20 px-3 py-1 text-xs font-semibold text-purple-300">
            ADMIN only
          </span>
        </div>
      </header>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p class="text-sm font-semibold text-slate-700">Pending merchants</p>
          <p class="text-xs text-slate-500">{{ merchantService.totalElements() }} awaiting review</p>
        </div>
        <div class="flex gap-2">
          <a routerLink="/admin" class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:text-indigo-600">Admin overview</a>
          <button type="button" class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:text-indigo-600" (click)="reload()">Refresh</button>
        </div>
      </div>

      @if (feedback(); as message) {
        <div role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{{ message }}</div>
      }

      @if (handoff(); as credentials) {
        <section aria-label="One-time merchant credential handoff">
          <p class="mb-3 text-sm font-semibold text-slate-800">Credentials for {{ handoffBusinessName() }}</p>
          <app-merchant-credentials [credentials]="credentials" (secretSaved)="clearHandoff()" />
          @if (!credentials.clientSecret) {
            <button id="dismiss-merchant-handoff" type="button" class="mt-3 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" (click)="clearHandoff()">Close</button>
          }
        </section>
      }

      @if (merchantService.error() && !merchantService.loading()) {
        <div id="merchant-approval-error" class="flex items-center justify-between gap-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800" role="alert">
          <span>{{ merchantService.error() }}</span>
          <button id="merchant-approval-retry" type="button" class="rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-rose-700" (click)="reload()">Retry</button>
        </div>
      }

      @if (merchantService.loading()) {
        <div id="merchant-approval-loading" class="rounded-2xl border border-slate-200 bg-white p-12 text-center shadow-sm" role="status">
          <div class="mx-auto mb-3 h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"></div>
          <p class="text-sm font-semibold text-slate-600">Loading pending merchants...</p>
        </div>
      } @else if (merchantService.merchants().length === 0 && !merchantService.error()) {
        <div id="merchant-approval-empty" class="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center shadow-sm">
          <h2 class="text-lg font-bold text-slate-800">No pending merchants</h2>
          <p class="mt-2 text-sm text-slate-500">New merchant registrations will appear here for review.</p>
        </div>
      } @else if (!merchantService.error()) {
        <section class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[760px] text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs font-bold uppercase tracking-wide text-slate-600">
                <tr>
                  <th class="px-5 py-3">Business</th>
                  <th class="px-5 py-3">Contact</th>
                  <th class="px-5 py-3">Submitted</th>
                  <th class="px-5 py-3">Status</th>
                  <th class="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (merchant of merchantService.merchants(); track merchant.id) {
                  <tr class="hover:bg-slate-50">
                    <td class="px-5 py-4">
                      <p class="font-bold text-slate-900">{{ merchant.businessName }}</p>
                      <p class="text-xs text-slate-500">{{ merchant.email }}</p>
                    </td>
                    <td class="px-5 py-4">
                      <p class="text-slate-800">{{ merchant.contactName }}</p>
                      <p class="text-xs text-slate-500">{{ merchant.phoneNumber }}</p>
                    </td>
                    <td class="px-5 py-4 text-slate-600">{{ merchant.createdAt | date: 'medium' }}</td>
                    <td class="px-5 py-4"><span class="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">{{ merchant.status }}</span></td>
                    <td class="px-5 py-4">
                      <div class="flex justify-end gap-2">
                        <button type="button" class="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:border-indigo-300 hover:text-indigo-600" (click)="selected.set(merchant)">Details</button>
                        <button type="button" class="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50" [disabled]="isSubmitting() || handoff() !== null" (click)="openApprove(merchant)">Approve</button>
                        <button type="button" class="rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50" [disabled]="isSubmitting()" (click)="openReject(merchant)">Reject</button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        @if (merchantService.totalPages() > 1) {
          <nav class="flex items-center justify-between" aria-label="Merchant pages">
            <button type="button" class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm disabled:opacity-40" [disabled]="merchantService.currentPage() === 0 || merchantService.loading()" (click)="goToPage(merchantService.currentPage() - 1)">Previous</button>
            <span class="text-sm text-slate-600">Page {{ merchantService.currentPage() + 1 }} of {{ merchantService.totalPages() }}</span>
            <button type="button" class="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm disabled:opacity-40" [disabled]="merchantService.currentPage() >= merchantService.totalPages() - 1 || merchantService.loading()" (click)="goToPage(merchantService.currentPage() + 1)">Next</button>
          </nav>
        }
      }
    </main>

    @if (selected(); as merchant) {
      <div class="fixed inset-0 z-10 flex items-center justify-center bg-slate-900/50 p-4" role="presentation" (click)="selected.set(null)">
        <section class="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl" role="dialog" aria-modal="true" aria-labelledby="merchant-details-title" (click)="$event.stopPropagation()">
          <div class="flex items-start justify-between gap-4">
            <div>
              <h2 id="merchant-details-title" class="text-xl font-bold text-slate-900">{{ merchant.businessName }}</h2>
              <p class="mt-1 text-sm text-slate-500">Merchant ID: {{ merchant.id }}</p>
            </div>
            <button type="button" class="text-slate-500 hover:text-slate-900" aria-label="Close details" (click)="selected.set(null)">✕</button>
          </div>
          <dl class="mt-5 grid gap-3 text-sm sm:grid-cols-2">
            <div><dt class="font-semibold text-slate-500">Contact</dt><dd>{{ merchant.contactName }}</dd></div>
            <div><dt class="font-semibold text-slate-500">Status</dt><dd>{{ merchant.status }}</dd></div>
            <div><dt class="font-semibold text-slate-500">Email</dt><dd class="break-all">{{ merchant.email }}</dd></div>
            <div><dt class="font-semibold text-slate-500">Phone</dt><dd>{{ merchant.phoneNumber }}</dd></div>
            <div class="sm:col-span-2"><dt class="font-semibold text-slate-500">Submitted</dt><dd>{{ merchant.createdAt | date: 'medium' }}</dd></div>
            @if (merchant.rejectionReason) {
              <div class="sm:col-span-2"><dt class="font-semibold text-slate-500">Rejection reason</dt><dd>{{ merchant.rejectionReason }}</dd></div>
            }
          </dl>
          @if (merchant.status === 'PENDING') {
            <div class="mt-6 flex justify-end gap-2">
              <button type="button" class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white" (click)="openApprove(merchant)">Approve</button>
              <button type="button" class="rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white" (click)="openReject(merchant)">Reject</button>
            </div>
          }
        </section>
      </div>
    }

    @if (action(); as currentAction) {
      @if (selected(); as merchant) {
        <div class="fixed inset-0 z-20 flex items-center justify-center bg-slate-900/50 p-4" role="presentation">
          <section class="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl" role="dialog" aria-modal="true" [attr.aria-labelledby]="currentAction + '-merchant-title'">
            <h2 [id]="currentAction + '-merchant-title'" class="text-xl font-bold text-slate-900">{{ currentAction === 'approve' ? 'Approve merchant?' : 'Reject merchant?' }}</h2>
            <p class="mt-2 text-sm text-slate-600">Confirm this action for <strong>{{ merchant.businessName }}</strong>.</p>
            @if (currentAction === 'reject') {
              <label for="rejection-reason" class="mt-5 block text-sm font-semibold text-slate-700">Rejection reason</label>
              <textarea id="rejection-reason" class="mt-2 min-h-28 w-full rounded-lg border border-slate-300 p-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200" maxlength="1000" [ngModel]="reason()" (ngModelChange)="reason.set($event)" aria-describedby="rejection-help"></textarea>
              <p id="rejection-help" class="mt-1 text-xs text-slate-500">{{ reason().length }}/1000 characters. A reason is required.</p>
            }
            @if (reasonError()) { <p class="mt-1 text-xs text-rose-600" role="alert">{{ reasonError() }}</p> }
            <div class="mt-6 flex justify-end gap-2">
              <button type="button" class="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700" [disabled]="isSubmitting()" (click)="closeAction()">Cancel</button>
              <button type="button" class="rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50" [class.bg-emerald-600]="currentAction === 'approve'" [class.bg-rose-600]="currentAction === 'reject'" [disabled]="isSubmitting()" (click)="confirmAction()">{{ isSubmitting() ? 'Submitting…' : (currentAction === 'approve' ? 'Confirm approval' : 'Confirm rejection') }}</button>
            </div>
          </section>
        </div>
      }
    }
  `,
})
export class MerchantApprovalComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  readonly merchantService = inject(MerchantApprovalService);
  readonly selected = signal<MerchantSummary | null>(null);
  readonly action = signal<Action | null>(null);
  readonly reason = signal('');
  readonly reasonError = signal<string | null>(null);
  readonly isSubmitting = signal(false);
  readonly feedback = signal<string | null>(null);
  readonly handoff = signal<MerchantApiCredentials | null>(null);
  readonly handoffBusinessName = signal('');

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.clearHandoff();
  }

  clearHandoff(): void {
    this.handoff.set(null);
    this.handoffBusinessName.set('');
  }

  reload(): void {
    this.merchantService.refresh().subscribe({ error: () => undefined });
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.merchantService.totalPages()) {
      this.merchantService.loadMerchants(page, this.merchantService.pageSize()).subscribe({ error: () => undefined });
    }
  }

  openApprove(merchant: MerchantSummary): void {
    if (merchant.status !== 'PENDING' || this.isSubmitting() || this.handoff()) return;
    this.selected.set(merchant);
    this.action.set('approve');
    this.reasonError.set(null);
  }

  openReject(merchant: MerchantSummary): void {
    if (merchant.status !== 'PENDING') return;
    this.selected.set(merchant);
    this.action.set('reject');
    this.reason.set('');
    this.reasonError.set(null);
  }

  closeAction(): void {
    if (!this.isSubmitting()) this.action.set(null);
  }

  confirmAction(): void {
    const merchant = this.selected();
    const currentAction = this.action();
    if (!merchant || merchant.status !== 'PENDING' || !currentAction || this.isSubmitting()) return;

    const trimmedReason = this.reason().trim();
    if (currentAction === 'reject' && (trimmedReason.length < 1 || trimmedReason.length > 1000)) {
      this.reasonError.set(trimmedReason.length > 1000 ? 'Enter no more than 1000 characters.' : 'Enter a rejection reason.');
      return;
    }

    this.isSubmitting.set(true);
    this.reasonError.set(null);
    if (currentAction === 'approve') {
      this.merchantService.approve(merchant.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: (result) => {
          this.handoffBusinessName.set(result.merchant.businessName);
          this.handoff.set({
            merchantId: result.merchant.id,
            status: 'APPROVED',
            clientId: result.clientId,
            clientSecret: result.clientSecret ?? null,
            secretAvailableOnce: !!result.clientSecret,
          });
          this.completeAction(result.merchant);
        },
        error: (error: unknown) => this.failAction(error),
      });
      return;
    }

    this.merchantService.reject(merchant.id, { reason: trimmedReason }).subscribe({
      next: (result) => this.completeAction(result),
      error: (error: unknown) => this.failAction(error),
    });
  }

  private completeAction(updatedMerchant: MerchantSummary): void {
    this.feedback.set(`${updatedMerchant.businessName} is now ${updatedMerchant.status}.`);
    this.action.set(null);
    this.selected.set(null);
    this.isSubmitting.set(false);
    this.reload();
  }

  private failAction(error: unknown): void {
    this.reasonError.set(this.merchantService.messageFor(error, 'The merchant action could not be completed.'));
    this.isSubmitting.set(false);
  }
}
