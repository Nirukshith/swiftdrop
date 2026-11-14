import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonComponent, CardComponent, InputComponent, StatusBadgeComponent } from 'ui';
import { MerchantOnboardingService } from './merchant-onboarding.service';
import { MerchantRegistrationResponse } from './merchant-registration.model';

@Component({
  selector: 'app-merchant-registration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardComponent, ButtonComponent, InputComponent, StatusBadgeComponent],
  template: `
    <div class="yum-page">
      <header class="top-bar">
        <a routerLink="/" class="brand">
          <svg class="brand-icon" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="10" fill="#ffffff" />
            <path d="M8 12L16 7L24 12V22L16 27L8 22V12Z" fill="#a53a26" />
            <path d="M16 7V27M8 12L24 22M24 12L8 22" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
          </svg>
          <span class="brand-text">SwiftDrop</span>
        </a>

        <nav class="nav-links">
          <a routerLink="/register" class="nav-link">Customer</a>
          <a routerLink="/merchant-registration" class="nav-pill">Merchant</a>
          <a routerLink="/login" class="nav-link">Sign In</a>
        </nav>
      </header>

      <main class="hero-grid">
        <!-- Left Column: Merchant Hero & Spoken Operations Chips -->
        <section class="hero-copy">
          <div class="hero-badge">
            <span class="badge-dot"></span> SwiftDrop Merchant Partner Program
          </div>

          <h1 class="hero-headline">Scale your business deliveries with ease</h1>

          <p class="hero-lede">
            Join thousands of e-commerce brands automating parcel fulfillment, daily courier dispatch, and live status updates across Sri Lanka.
          </p>

          <ul class="said-chips" aria-label="Merchant actions">
            <li class="said-chip chip-1">
              <svg class="chip-icon store" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
              <span>“Bulk dispatch 50 orders to Western Province”</span>
            </li>
            <li class="said-chip chip-2">
              <svg class="chip-icon chart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="18" y1="20" x2="18" y2="10"/>
                <line x1="12" y1="20" x2="12" y2="4"/>
                <line x1="6" y1="20" x2="6" y2="14"/>
              </svg>
              <span>“Automated daily courier reconciliation”</span>
            </li>
            <li class="said-chip chip-3">
              <svg class="chip-icon cash" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <rect x="2" y="6" width="20" height="12" rx="2"/>
                <circle cx="12" cy="12" r="3"/>
                <path d="M6 12h.01M18 12h.01"/>
              </svg>
              <span>“Daily sales settlements & island-wide COD”</span>
            </li>
          </ul>

          <div class="hero-store-row">
            <div class="store-badge">
              <svg class="apple-icon" viewBox="0 0 384 512" fill="currentColor">
                <path d="M318.7 268.7c-.2-36.7 16.4-64.4 50-84.8-18.8-26.9-47.2-41.7-84.7-44.6-35.5-2.8-74.3 20.7-88.5 20.7-15 0-49.4-19.7-76.4-19.7C63.3 141.2 4 184.8 4 273.5q0 39.3 14.4 81.2c12.8 36.7 59 126.7 107.2 125.2 25.2-.6 43-17.9 75.8-17.9 31.8 0 48.3 17.9 76.4 17.9 48.6-.7 90.4-82.5 102.6-119.3-65.2-30.7-61.7-90-61.7-91.9zm-56.6-164.2c27.3-32.4 24.8-61.9 24-72.5-24.1 1.4-52 16.4-67.9 34.9-17.5 19.8-27.8 44.3-25.6 71.9 26.1 2 49.9-11.4 69.5-34.3z"/>
              </svg>
              <div class="store-text">
                <span class="store-small">Official Partner Network</span>
                <span class="store-bold">SwiftDrop Merchant Portal</span>
              </div>
            </div>
          </div>
        </section>

        <!-- Right Column: Tactile White Card Styled like Login Page -->
        <section class="auth-card-wrap">
          <div class="merchant-card-shell">
            <ui-card
              title="Register your business"
              subtitle="Submit your business details to unlock wholesale courier rates, bulk dispatch tools, and dedicated account support."
            >
              <div uiCardActions>
                <span class="card-pill">Merchant Partnership</span>
              </div>

              @if (registration(); as result) {
                <div class="success-box" role="status">
                  <span class="success-badge">Registration received</span>
                  <h2 class="success-title">{{ result.businessName }}</h2>
                  <p class="success-msg">{{ result.message }}</p>
                  <p class="status-indicator">
                    Status: <ui-status-badge [status]="result.status" tone="warning" [label]="result.status" />
                  </p>
                  <p class="approval-info">Our merchant team reviews and activates partnership accounts within 24 hours to enable discounted volume rates and priority courier scheduling.</p>
                  <p class="approval-info">Your merchant portal access is separate from your sign-in password. After approval, use <a routerLink="/forgot-password" class="auth-link">Forgot password</a> with your registered email to set your sign-in password before opening the merchant portal.</p>
                  <div class="mt-6">
                    <ui-button routerLink="/login" [fullWidth]="true">Sign In</ui-button>
                  </div>
                </div>
              } @else {
                @if (errorMessage()) {
                  <div role="alert" class="error-banner">
                    <svg class="error-icon" viewBox="0 0 20 20" fill="currentColor">
                      <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
                    </svg>
                    <span>{{ errorMessage() }}</span>
                  </div>
                }

                <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form">
                  <div class="form-stack">
                    <ui-input
                      inputId="businessName"
                      label="Business name"
                      formControlName="businessName"
                      placeholder="e.g. Ceylon Spices Co."
                      autocomplete="organization"
                      [messages]="{ required: 'Enter a business name (up to 200 characters).', maxlength: 'Enter a business name (up to 200 characters).' }"
                    />

                    <ui-input
                      inputId="contactName"
                      label="Contact name"
                      formControlName="contactName"
                      placeholder="e.g. Kasun Perera"
                      autocomplete="name"
                      [messages]="{ required: 'Enter a contact name (up to 200 characters).', maxlength: 'Enter a contact name (up to 200 characters).' }"
                    />

                    <ui-input
                      inputId="phoneNumber"
                      label="Phone number"
                      type="tel"
                      formControlName="phoneNumber"
                      placeholder="+94771234567"
                      autocomplete="tel"
                      [messages]="{ required: 'Use 10–14 digits, optionally starting with +.', pattern: 'Use 10–14 digits, optionally starting with +.' }"
                    />

                    <ui-input
                      inputId="email"
                      label="Business email"
                      type="email"
                      formControlName="email"
                      placeholder="merchant@example.com"
                      autocomplete="email"
                      [messages]="{ required: 'Enter a valid email address.', email: 'Enter a valid email address.' }"
                    />

                    <ui-input
                      inputId="businessRegistrationNumber"
                      label="Business registration number (optional)"
                      formControlName="businessRegistrationNumber"
                      placeholder="e.g. PV-123456"
                    />
                  </div>

                  <div class="submit-wrap">
                    <ui-button type="submit" [disabled]="form.invalid || isSubmitting()" [loading]="isSubmitting()" [fullWidth]="true">
                      {{ isSubmitting() ? 'Submitting…' : 'Submit registration' }}
                    </ui-button>
                  </div>
                </form>

                <div class="auth-footer">
                  <p>Already approved? <a routerLink="/login">Sign in</a></p>
                  <p class="customer-note">Looking for personal delivery? <a routerLink="/register">Customer signup</a></p>
                </div>
              }
            </ui-card>
          </div>
        </section>
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      --disp: ui-rounded, 'SF Pro Rounded', 'Nunito', system-ui, -apple-system, sans-serif;
      --terracotta: #a53a26;
      --coral: #f2533a;
      --ink: #1d1b2c;
      --ink-3: #6f6d85;
      font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', system-ui, 'Segoe UI', Roboto, sans-serif;
    }
    .yum-page {
      position: relative;
      min-height: 100vh;
      margin: 0;
      padding: 2.5rem 2rem 4rem;
      background: radial-gradient(55% 65% at 75% 45%, #b8432f 0%, #98311e 65%, #882b1a 100%), #98311e;
      color: #fff;
      overflow: hidden;
    }
    .top-bar {
      max-width: 1200px;
      margin: 0 auto 3rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      color: #fff;
      text-decoration: none;
    }
    .brand-icon {
      width: 34px;
      height: 34px;
      border-radius: 10px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    }
    .brand-text {
      font-family: var(--disp);
      font-size: 1.5rem;
      font-weight: 900;
      letter-spacing: -0.03em;
    }
    .nav-links {
      display: flex;
      align-items: center;
      gap: 1.5rem;
    }
    .nav-link {
      color: rgba(255,255,255,0.85);
      text-decoration: none;
      font-size: 0.95rem;
      font-weight: 600;
      transition: color 0.2s;
    }
    .nav-link:hover { color: #fff; }
    .nav-pill {
      display: inline-flex;
      align-items: center;
      height: 38px;
      padding: 0 1.25rem;
      border-radius: 9999px;
      background: var(--ink);
      color: #fff;
      font-size: 0.875rem;
      font-weight: 700;
      text-decoration: none;
      transition: background-color 0.2s, transform 0.2s;
    }
    .nav-pill:hover {
      background: #000;
      transform: translateY(-1px);
    }
    .hero-grid {
      max-width: 1200px;
      margin: 0 auto;
      display: grid;
      grid-template-columns: 1fr 1fr;
      align-items: center;
      gap: 4rem;
    }
    .hero-copy {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }
    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.35rem 0.85rem;
      border-radius: 9999px;
      background: rgba(255,255,255,0.15);
      backdrop-filter: blur(8px);
      font-size: 0.8125rem;
      font-weight: 700;
      color: #fff;
      margin-bottom: 1.5rem;
    }
    .badge-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #4ade80;
      box-shadow: 0 0 8px #4ade80;
    }
    .hero-headline {
      font-family: var(--disp);
      font-size: clamp(2.4rem, 4.5vw, 4.25rem);
      font-weight: 900;
      line-height: 0.96;
      letter-spacing: -0.035em;
      color: #fff;
      margin: 0 0 1.5rem;
      text-shadow: 0 2px 0 rgba(140,30,15,0.25);
    }
    .hero-lede {
      max-width: 28rem;
      font-size: 1.15rem;
      line-height: 1.55;
      color: rgba(255,255,255,0.9);
      margin: 0 0 2.25rem;
    }
    .said-chips {
      list-style: none;
      padding: 0;
      margin: 0 0 2.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
      width: 100%;
      max-width: 32rem;
    }
    .said-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1.25rem;
      border-radius: 9999px;
      background: #fff;
      color: var(--ink);
      font-size: 0.95rem;
      font-weight: 700;
      box-shadow: 0 14px 30px -12px rgba(90,15,5,0.45);
      animation: bob 7s ease-in-out infinite;
      align-self: flex-start;
    }
    .chip-2 { animation-delay: -2.3s; }
    .chip-3 { animation-delay: -4.6s; }
    @keyframes bob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
    .chip-icon {
      width: 18px;
      height: 18px;
      color: var(--coral);
      flex-shrink: 0;
    }
    .hero-store-row { margin-top: 0.5rem; }
    .store-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.65rem 1.25rem;
      background: rgba(0,0,0,0.25);
      border: 1px solid rgba(255,255,255,0.15);
      border-radius: 16px;
      color: #fff;
    }
    .apple-icon { width: 22px; height: 26px; }
    .store-text { display: flex; flex-direction: column; }
    .store-small { font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.8; }
    .store-bold { font-size: 0.875rem; font-weight: 700; }
    .auth-card-wrap {
      display: flex;
      justify-content: center;
      width: 100%;
    }
    .merchant-card-shell {
      width: 100%;
      max-width: 500px;
    }
    :host ::ng-deep .merchant-card-shell ui-card > section {
      border-radius: 28px !important;
      padding: 2.75rem 2.25rem !important;
      box-shadow: 0 24px 60px -15px rgba(29,27,44,0.35), 0 8px 24px -6px rgba(0,0,0,0.2) !important;
      background: #ffffff !important;
      border: none !important;
      color: var(--ink) !important;
    }
    :host ::ng-deep .merchant-card-shell ui-card header {
      border-bottom: none !important;
      padding: 0 0 1.5rem 0 !important;
      display: flex !important;
      flex-direction: column-reverse !important;
      align-items: flex-start !important;
      gap: 0.5rem !important;
    }
    :host ::ng-deep .merchant-card-shell ui-card header h2 {
      font-family: var(--disp) !important;
      font-size: 1.85rem !important;
      font-weight: 900 !important;
      color: var(--ink) !important;
      letter-spacing: -0.025em !important;
      margin: 0.25rem 0 !important;
    }
    :host ::ng-deep .merchant-card-shell ui-card header p {
      font-size: 0.9rem !important;
      color: var(--ink-3) !important;
      line-height: 1.45 !important;
      margin: 0 !important;
    }
    :host ::ng-deep .merchant-card-shell ui-card > section > div {
      padding: 0 !important;
    }
    .card-pill {
      display: inline-block;
      padding: 0.25rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--terracotta);
      background: rgba(165,58,38,0.08);
      border-radius: 9999px;
    }
    .form-stack {
      display: flex;
      flex-direction: column;
      gap: 1.15rem;
    }
    :host ::ng-deep ui-input label {
      font-size: 0.875rem !important;
      font-weight: 700 !important;
      color: var(--ink) !important;
      margin-bottom: 0.35rem !important;
      display: block !important;
    }
    :host ::ng-deep ui-input input {
      padding: 0.85rem 1.1rem !important;
      border-radius: 14px !important;
      border: 1.5px solid #e2e8f0 !important;
      background: #f8fafc !important;
      color: var(--ink) !important;
      font-size: 0.95rem !important;
      transition: all 0.2s ease !important;
      width: 100% !important;
      box-sizing: border-box !important;
    }
    :host ::ng-deep ui-input input:focus {
      outline: none !important;
      background: #ffffff !important;
      border-color: var(--terracotta) !important;
      box-shadow: 0 0 0 4px rgba(165,58,38,0.15) !important;
    }
    :host ::ng-deep ui-button button {
      width: 100% !important;
      height: 50px !important;
      border-radius: 9999px !important;
      background: var(--ink) !important;
      color: #ffffff !important;
      font-size: 1rem !important;
      font-weight: 700 !important;
      border: none !important;
      box-shadow: 0 10px 25px -8px rgba(29,27,44,0.4) !important;
      transition: all 0.2s ease !important;
      cursor: pointer !important;
      display: flex !important;
      justify-content: center !important;
      align-items: center !important;
    }
    :host ::ng-deep ui-button button:hover:not(:disabled) {
      background: #000000 !important;
      transform: translateY(-1px) !important;
    }
    :host ::ng-deep ui-button button:disabled {
      opacity: 0.5 !important;
      cursor: not-allowed !important;
      transform: none !important;
      box-shadow: none !important;
    }
    .submit-wrap {
      margin-top: 1.5rem;
    }
    .auth-footer {
      margin-top: 1.75rem;
      text-align: center;
      font-size: 0.875rem;
      color: var(--ink-3);
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .auth-footer a {
      color: var(--terracotta);
      text-decoration: none;
      font-weight: 700;
    }
    .auth-footer a:hover { text-decoration: underline; }
    .customer-note { font-size: 0.8125rem; }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      background: #fef2f2;
      border: 1.5px solid #fca5a5;
      color: #991b1b;
      padding: 0.85rem 1rem;
      border-radius: 14px;
      margin-bottom: 1.5rem;
      font-size: 0.875rem;
      font-weight: 600;
    }
    .error-icon {
      width: 20px;
      height: 20px;
      flex-shrink: 0;
      color: #dc2626;
    }
    .success-box {
      border-radius: 20px;
      border: 1.5px solid #a7f3d0;
      background: #ecfdf5;
      padding: 1.75rem;
      color: #064e3b;
    }
    .success-badge {
      font-size: 0.75rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #047857;
    }
    .success-title {
      font-family: var(--disp);
      font-size: 1.5rem;
      font-weight: 900;
      color: #064e3b;
      margin: 0.5rem 0;
    }
    .success-msg {
      font-size: 0.9rem;
      color: #047857;
      margin-bottom: 1rem;
    }
    .status-indicator {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.8125rem;
      font-weight: 700;
      color: #92400e;
      margin-bottom: 1rem;
    }
    .approval-info {
      font-size: 0.85rem;
      color: #065f46;
      line-height: 1.5;
      margin: 0.75rem 0;
    }
    .auth-link {
      font-weight: 700;
      color: var(--terracotta);
      text-decoration: underline;
    }
    @media (max-width: 960px) {
      .yum-page { padding: 1.75rem 1.25rem 3rem; }
      .hero-grid { grid-template-columns: 1fr; gap: 2.75rem; }
      .hero-copy { align-items: center; text-align: center; }
      .hero-headline { font-size: 2.5rem; }
      .said-chips { align-items: center; }
      .said-chip { align-self: center; }
      .chip-2, .chip-3 { margin-left: 0; }
      .merchant-card-shell { max-width: 100%; }
    }
    @media (max-width: 480px) {
      .top-bar { flex-direction: column; gap: 1rem; }
      .hero-headline { font-size: 2.1rem; }
      .said-chip { font-size: 0.825rem; padding: 0.65rem 1rem; }
    }
  `]
})
export class MerchantRegistrationComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly onboardingService = inject(MerchantOnboardingService);

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly registration = signal<MerchantRegistrationResponse | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    businessName: ['', [Validators.required, Validators.maxLength(200)]],
    contactName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email]],
    phoneNumber: ['', [Validators.required, Validators.pattern(/^\+?[0-9]{10,14}$/)]],
    businessRegistrationNumber: [''],
  });

  invalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.dirty || control.touched);
  }

  submit(): void {
    if (this.form.invalid || this.isSubmitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    const { businessRegistrationNumber, ...requiredFields } = this.form.getRawValue();
    this.onboardingService.register({
      ...requiredFields,
      ...(businessRegistrationNumber.trim() ? { businessRegistrationNumber: businessRegistrationNumber.trim() } : {}),
    }).subscribe({
      next: (result) => {
        this.registration.set(result);
        this.isSubmitting.set(false);
      },
      error: (error: { status?: number }) => {
        this.errorMessage.set(error.status === 409
          ? 'This email is already registered.'
          : 'Registration could not be submitted. Please try again.');
        this.isSubmitting.set(false);
      },
    });
  }
}
