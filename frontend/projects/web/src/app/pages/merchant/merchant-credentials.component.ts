import { CommonModule } from '@angular/common';
import { Component, input, output, signal } from '@angular/core';
import { ButtonComponent, CardComponent, StatusBadgeComponent } from 'ui';
import { MerchantApiCredentials } from './merchant.model';

@Component({
  selector: 'app-merchant-credentials',
  standalone: true,
  imports: [CommonModule, CardComponent, ButtonComponent, StatusBadgeComponent],
  template: `
    <main class="mx-auto max-w-4xl space-y-6">
      <!-- Section Header -->
      <div>
        <h1 class="text-2xl sm:text-3xl font-black text-[#1d1b2c] tracking-tight">
          API Credentials & Authentication
        </h1>
        <p class="text-xs text-[#6f6d85] font-medium mt-1">
          OAuth 2.0 Client Credentials, access tokens, and integration specifications for merchant APIs.
        </p>
      </div>

      <!-- Main Credentials Card -->
      <ui-card
        title="API credentials"
        subtitle="Use these OAuth 2.0 client credentials to request an access token for your merchant integration."
      >
        <div uiCardActions>
          @if (credentials(); as item) {
            <ui-status-badge
              [status]="item.status"
              [tone]="item.status === 'APPROVED' ? 'success' : 'warning'"
              [label]="item.status"
            />
          }
        </div>

        @if (credentials(); as item) {
          @if (item.status === 'PENDING') {
            <div class="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-xs text-amber-900 leading-relaxed font-medium" role="status">
              Your merchant registration is awaiting administrator approval. An administrator will securely hand over your API credentials after approval.
            </div>
          } @else {
            <div class="space-y-6 pt-2">
              <!-- Client ID Row -->
              <div class="p-4 rounded-2xl bg-[#faf8f6] border border-[#e8e2dc] space-y-2">
                <div class="flex items-center justify-between">
                  <label for="clientId" class="block text-xs font-bold text-[#1d1b2c]">Client ID</label>
                  <span
                    *ngIf="item.clientId"
                    (click)="copyText(item.clientId, 'Client ID')"
                    class="text-[11px] font-bold text-[#a53a26] hover:text-[#7e2615] transition-colors cursor-pointer select-none"
                  >
                    {{ copiedField() === 'Client ID' ? '✓ Copied' : 'Copy ID' }}
                  </span>
                </div>
                <code id="clientId" class="block break-all rounded-xl bg-white border border-[#e8e2dc] px-3.5 py-2.5 text-xs font-mono font-bold text-[#1d1b2c] select-all shadow-sm">
                  {{ item.clientId }}
                </code>
              </div>

              <!-- Client Secret Row -->
              <div class="p-4 rounded-2xl bg-[#faf8f6] border border-[#e8e2dc] space-y-2">
                <div class="flex items-center justify-between gap-3">
                  <label for="clientSecret" class="block text-xs font-bold text-[#1d1b2c]">Client secret</label>
                  @if (item.secretAvailableOnce) {
                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-amber-100 text-amber-800">
                      Shown once
                    </span>
                  }
                </div>
                @if (item.clientSecret) {
                  <div class="p-4 rounded-xl border border-amber-200 bg-amber-50/80 space-y-3">
                    <div class="flex items-center justify-between">
                      <div class="flex items-center gap-1.5 text-[11px] font-bold text-amber-900">
                        <svg class="w-3.5 h-3.5 text-amber-800" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                          <line x1="12" y1="9" x2="12" y2="13"></line>
                          <line x1="12" y1="17" x2="12.01" y2="17"></line>
                        </svg>
                        <span>Active One-Time Client Secret</span>
                      </div>
                      <span
                        (click)="copyText(item.clientSecret, 'Client Secret')"
                        class="text-[11px] font-bold text-amber-900 hover:text-black transition-colors cursor-pointer underline select-none"
                      >
                        {{ copiedField() === 'Client Secret' ? '✓ Copied' : 'Copy Secret' }}
                      </span>
                    </div>
                    <code id="clientSecret" class="block break-all rounded-lg border border-amber-300 bg-white px-3 py-2.5 text-xs font-mono font-bold text-slate-900 select-all shadow-sm">
                      {{ item.clientSecret }}
                    </code>
                    <p class="text-[11px] text-slate-600 leading-normal">
                      Copy and store this secret securely. It will not be shown again after you hide it or leave this page.
                    </p>
                    <div class="pt-1">
                      <ui-button variant="secondary" (click)="hideSecret()">Done</ui-button>
                    </div>
                  </div>
                } @else {
                  <p class="rounded-xl bg-white border border-[#e8e2dc] px-4 py-3 text-xs text-[#6f6d85] leading-relaxed">
                    The secret is unavailable in this view. Use the copy saved during the one-time administrator handoff.
                  </p>
                }
              </div>

              @if (showTokenRequest()) {
                <!-- Token Request Guide -->
                <div class="rounded-2xl border border-[#eee7e0] p-5 bg-white space-y-3">
                  <div class="flex items-center justify-between">
                    <div>
                      <h3 class="text-xs font-black uppercase tracking-wider text-[#1d1b2c]">Token request</h3>
                      <p class="text-xs text-[#6f6d85] mt-0.5">
                        Request a short-lived bearer token using the client credentials grant. Send the token in the Authorization header when calling merchant APIs.
                      </p>
                    </div>
                    <span class="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#a53a26]/10 text-[#a53a26]">
                      OAuth 2.0
                    </span>
                  </div>

                  <div class="space-y-2 pt-1">
                    <span class="text-[10px] uppercase font-bold text-[#a09eaf] block">Request Header Format</span>
                    <code class="block overflow-x-auto rounded-xl bg-[#1d1b2c] px-4 py-3 text-xs font-mono text-[#faf8f6] shadow-sm">Authorization: Bearer &lt;access-token&gt;</code>
                  </div>

                  <!-- Integration Quick Reference -->
                  <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2 text-[11px]">
                    <div class="p-3 rounded-xl bg-[#faf8f6] border border-[#e8e2dc]">
                      <span class="font-bold text-[#1d1b2c] block">Base API URL</span>
                      <span class="font-mono text-[#6f6d85] text-[10px] break-all block mt-0.5">/api/v1/merchant</span>
                    </div>
                    <div class="p-3 rounded-xl bg-[#faf8f6] border border-[#e8e2dc]">
                      <span class="font-bold text-[#1d1b2c] block">Grant Type</span>
                      <span class="font-mono text-[#6f6d85] text-[10px] block mt-0.5">client_credentials</span>
                    </div>
                    <div class="p-3 rounded-xl bg-[#faf8f6] border border-[#e8e2dc]">
                      <span class="font-bold text-[#1d1b2c] block">Rate Limit</span>
                      <span class="font-mono text-[#6f6d85] text-[10px] block mt-0.5">60 requests / min</span>
                    </div>
                  </div>
                </div>
              }

              <p class="text-xs text-slate-500 font-medium">
                Hand over these credentials securely to the merchant. The secret cannot be retrieved again from this page.
              </p>
            </div>
          }
        } @else {
          <!-- Empty / Informational State for Merchant Portal View -->
          <div class="space-y-6 pt-2">
            <div class="rounded-2xl bg-[#faf8f6] border border-[#e8e2dc] p-5 text-xs text-[#6f6d85] leading-relaxed space-y-2" role="status">
              <p class="font-semibold text-[#1d1b2c]">OAuth 2.0 Integration Information</p>
              <p>
                After your registration is approved, an administrator will securely hand over your client ID and one-time client secret.
                Use your saved credentials to request an access token. This page does not retrieve or redisplay your secret.
              </p>
            </div>

            <!-- API Quick Reference Cards -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="p-5 rounded-2xl bg-white border border-[#eee7e0] shadow-sm space-y-2">
                <div class="w-8 h-8 rounded-full bg-[#a53a26]/10 text-[#a53a26] flex items-center justify-center font-bold text-xs">
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                  </svg>
                </div>
                <h4 class="text-xs font-bold text-[#1d1b2c]">Token Endpoint</h4>
                <p class="text-[11px] text-[#6f6d85]">
                  Issue a POST request to exchange your client credentials for a signed JWT access token.
                </p>
                <code class="block text-[11px] font-mono bg-[#faf8f6] p-2.5 rounded-lg border border-[#e8e2dc] text-[#1d1b2c] truncate">
                  POST /api/v1/auth/token
                </code>
              </div>

              <div class="p-5 rounded-2xl bg-white border border-[#eee7e0] shadow-sm space-y-2">
                <div class="w-8 h-8 rounded-full bg-[#a53a26]/10 text-[#a53a26] flex items-center justify-center font-bold text-xs">
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                  </svg>
                </div>
                <h4 class="text-xs font-bold text-[#1d1b2c]">Idempotency Guarantee</h4>
                <p class="text-[11px] text-[#6f6d85]">
                  Supply an <code>Idempotency-Key</code> UUID header when submitting bulk orders to prevent duplicate deliveries.
                </p>
                <code class="block text-[11px] font-mono bg-[#faf8f6] p-2.5 rounded-lg border border-[#e8e2dc] text-[#1d1b2c] truncate">
                  Idempotency-Key: &lt;uuid&gt;
                </code>
              </div>
            </div>
          </div>
        }
      </ui-card>
    </main>
  `,
})
export class MerchantCredentialsComponent {
  readonly credentials = input<MerchantApiCredentials | null>(null);
  readonly showTokenRequest = input<boolean>(false);
  readonly secretSaved = output<void>();
  readonly copiedField = signal<string | null>(null);

  hideSecret(): void {
    this.secretSaved.emit();
  }

  copyText(text: string | null | undefined, label: string): void {
    if (!text) return;
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        this.copiedField.set(label);
        setTimeout(() => this.copiedField.set(null), 2000);
      });
    }
  }
}
