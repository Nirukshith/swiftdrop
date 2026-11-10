import { CommonModule } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { ButtonComponent, CardComponent, StatusBadgeComponent } from 'ui';
import { MerchantApiCredentials } from './merchant.model';

@Component({
  selector: 'app-merchant-credentials',
  standalone: true,
  imports: [CommonModule, CardComponent, ButtonComponent, StatusBadgeComponent],
  template: `
    <main class="mx-auto max-w-4xl space-y-6">
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
            <div class="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900" role="status">
              Your merchant registration is awaiting administrator approval. An administrator will securely hand over your API credentials after approval.
            </div>
          } @else {
            <div class="space-y-5">
              <div>
                <label for="clientId" class="mb-1 block text-sm font-medium text-slate-700">Client ID</label>
                <code id="clientId" class="block break-all rounded-lg bg-slate-50 px-3 py-3 text-sm text-slate-800">{{ item.clientId }}</code>
              </div>
              <div>
                <div class="mb-1 flex items-center justify-between gap-3">
                  <label for="clientSecret" class="block text-sm font-medium text-slate-700">Client secret</label>
                  @if (item.secretAvailableOnce) {
                    <span class="text-xs font-medium text-amber-700">Shown once</span>
                  }
                </div>
                @if (item.clientSecret) {
                  <code id="clientSecret" class="block break-all rounded-lg border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-slate-900">{{ item.clientSecret }}</code>
                  <p class="mt-2 text-xs text-slate-600">Copy and store this secret securely. It will not be shown again after you hide it or leave this page.</p>
                  <div class="mt-3">
                    <ui-button variant="secondary" (click)="hideSecret()">I saved the secret</ui-button>
                  </div>
                } @else {
                  <p class="rounded-lg bg-slate-50 px-3 py-3 text-sm text-slate-600">The secret is unavailable in this view. Use the copy saved during the one-time administrator handoff.</p>
                }
              </div>
              <div class="rounded-lg border border-slate-200 p-4">
                <h3 class="text-sm font-semibold text-slate-800">Token request</h3>
                <p class="mt-1 text-sm text-slate-600">Request a short-lived bearer token using the client credentials grant. Send the token in the Authorization header when calling merchant APIs.</p>
                <code class="mt-3 block overflow-x-auto rounded-lg bg-slate-900 px-3 py-3 text-xs text-slate-100">Authorization: Bearer &lt;access-token&gt;</code>
              </div>
              <p class="text-xs text-slate-500">Hand over these credentials securely to the merchant. The secret cannot be retrieved again from this page.</p>
            </div>
          }
        } @else {
          <div class="rounded-lg bg-slate-50 p-5 text-sm text-slate-700" role="status">
            After your registration is approved, an administrator will securely hand over your client ID and one-time client secret.
            Use your saved credentials to request an access token. This page does not retrieve or redisplay your secret.
          </div>
        }
      </ui-card>
    </main>
  `,
})
export class MerchantCredentialsComponent {
  readonly credentials = input<MerchantApiCredentials | null>(null);
  readonly secretSaved = output<void>();

  hideSecret(): void {
    this.secretSaved.emit();
  }
}
