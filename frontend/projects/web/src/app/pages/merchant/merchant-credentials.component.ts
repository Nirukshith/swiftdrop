import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MerchantApiCredentials } from './merchant.model';
import { MerchantService } from './merchant.service';

@Component({
  selector: 'app-merchant-credentials',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="mx-auto max-w-4xl space-y-6" aria-labelledby="credentials-title">
      <header class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p class="text-sm font-semibold uppercase tracking-wide text-indigo-600">Developer settings</p>
            <h2 id="credentials-title" class="mt-2 text-2xl font-bold text-slate-900">API credentials</h2>
            <p class="mt-2 max-w-2xl text-sm text-slate-600">Use these OAuth 2.0 client credentials to request an access token for your merchant integration.</p>
          </div>
          @if (credentials(); as item) {
            <span class="rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">{{ item.status }}</span>
          }
        </div>
      </header>

      @if (credentials(); as item) {
        @if (item.status === 'PENDING') {
          <div class="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900" role="status">
            Your merchant registration is awaiting administrator approval. API credentials will appear here after approval.
          </div>
        } @else {
          <div class="space-y-5 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
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
                <button type="button" (click)="hideSecret()" class="mt-3 rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">I saved the secret</button>
              } @else {
                <p class="rounded-lg bg-slate-50 px-3 py-3 text-sm text-slate-600">This secret has already been shown. Keep your saved copy secure.</p>
              }
            </div>
            <div class="rounded-lg border border-slate-200 p-4">
              <h3 class="text-sm font-semibold text-slate-800">Token request</h3>
              <p class="mt-1 text-sm text-slate-600">Request a short-lived bearer token using the client credentials grant. Send the token in the Authorization header when calling merchant APIs.</p>
              <code class="mt-3 block overflow-x-auto rounded-lg bg-slate-900 px-3 py-3 text-xs text-slate-100">Authorization: Bearer &lt;access-token&gt;</code>
            </div>
            <p class="text-xs text-slate-500">Demo credentials are mock data. Never commit real secrets to source control.</p>
          </div>
        }
      }
    </section>
  `,
})
export class MerchantCredentialsComponent implements OnInit {
  private readonly merchantService = inject(MerchantService);
  readonly credentials = signal<MerchantApiCredentials | null>(null);

  ngOnInit(): void {
    this.merchantService.getApiCredentials().subscribe((credentials) => this.credentials.set(credentials));
  }

  hideSecret(): void {
    this.credentials.update((current) => current ? { ...current, clientSecret: null } : current);
  }
}
