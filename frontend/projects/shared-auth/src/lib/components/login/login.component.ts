import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'lib-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <div class="yum-page">
      <header class="top-bar">
        <div class="brand">
          <svg class="brand-icon" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="10" fill="#ffffff" />
            <path d="M8 12L16 7L24 12V22L16 27L8 22V12Z" fill="#a53a26" />
            <path d="M16 7V27M8 12L24 22M24 12L8 22" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
          </svg>
          <span class="brand-text">SwiftDrop</span>
        </div>

        <nav class="nav-links">
          <a routerLink="/track" class="nav-link">Live Track</a>
          <a routerLink="/merchant-registration" class="nav-link">Merchants</a>
          <a routerLink="/register" class="nav-pill">Get Started</a>
        </nav>
      </header>

      <main class="hero-grid">
        <!-- Left Column: Spoken Headlines and Floating Chips (YumLog style) -->
        <section class="hero-copy">
          <div class="hero-badge">
            <span class="badge-dot"></span> Next-Gen Parcel Delivery
          </div>

          <h1 class="hero-headline">The parcel delivery you can talk&nbsp;to</h1>

          <p class="hero-lede">
            Say “two parcels to Kandy with priority handling”, and dispatch is scheduled.
            SwiftDrop calculates dynamic rates, assigns couriers in real time, and tracks every stop.
          </p>

          <ul class="said-chips" aria-label="Spoken delivery commands">
            <li class="said-chip chip-1">
              <svg class="chip-icon mic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="22"/>
              </svg>
              <span>“Deliver package #SD-8942 by 2 PM”</span>
            </li>
            <li class="said-chip chip-2">
              <svg class="chip-icon parcel" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                <line x1="12" y1="22.08" x2="12" y2="12"/>
              </svg>
              <span>“Courier arriving in 8 minutes”</span>
            </li>
            <li class="said-chip chip-3">
              <svg class="chip-icon check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <span>“Proof of delivery confirmed”</span>
            </li>
          </ul>

          <div class="hero-store-row">
            <div class="store-badge">
              <svg class="apple-icon" viewBox="0 0 384 512" fill="currentColor">
                <path d="M318.7 268.7c-.2-36.7 16.4-64.4 50-84.8-18.8-26.9-47.2-41.7-84.7-44.6-35.5-2.8-74.3 20.7-88.5 20.7-15 0-49.4-19.7-76.4-19.7C63.3 141.2 4 184.8 4 273.5q0 39.3 14.4 81.2c12.8 36.7 59 126.7 107.2 125.2 25.2-.6 43-17.9 75.8-17.9 31.8 0 48.3 17.9 76.4 17.9 48.6-.7 90.4-82.5 102.6-119.3-65.2-30.7-61.7-90-61.7-91.9zm-56.6-164.2c27.3-32.4 24.8-61.9 24-72.5-24.1 1.4-52 16.4-67.9 34.9-17.5 19.8-27.8 44.3-25.6 71.9 26.1 2 49.9-11.4 69.5-34.3z"/>
              </svg>
              <div class="store-text">
                <span class="store-small">Available across Sri Lanka</span>
                <span class="store-bold">SwiftDrop Instant Dispatch</span>
              </div>
            </div>
          </div>
        </section>

        <!-- Right Column: Tactile YumLog White Card -->
        <section class="auth-card-wrap">
          <div class="auth-card">
            <div class="card-header">
              <span class="card-pill">Secure Sign In</span>
              <h2>Welcome back</h2>
              <p>Log in to access your parcels, dispatch queue, and account</p>
            </div>

            @if (errorMessage()) {
              <div class="error-banner" role="alert">
                <svg class="error-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
                </svg>
                <span>{{ errorMessage() }}</span>
              </div>
            }

            <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="auth-form">
              <div class="form-group">
                <label for="email">Email address</label>
                <input
                  id="email"
                  type="email"
                  formControlName="email"
                  placeholder="name@swiftdrop.lk"
                  [class.invalid]="isFieldInvalid('email')"
                  autocomplete="email"
                />
                @if (isFieldInvalid('email')) {
                  <span class="field-error">Please enter a valid email address</span>
                }
              </div>

              <div class="form-group">
                <div class="password-label-container">
                  <label for="password">Password</label>
                  <a routerLink="/forgot-password" class="forgot-password-link">Forgot?</a>
                </div>
                <input
                  id="password"
                  type="password"
                  formControlName="password"
                  placeholder="••••••••••••"
                  [class.invalid]="isFieldInvalid('password')"
                  autocomplete="current-password"
                />
                @if (isFieldInvalid('password')) {
                  <span class="field-error">Password is required</span>
                }
              </div>

              <button
                type="submit"
                class="submit-btn"
                [disabled]="loginForm.invalid || isLoading()"
              >
                @if (isLoading()) {
                  <span class="spinner"></span> Signing in...
                } @else {
                  Sign in
                }
              </button>
            </form>

            <div class="oauth-divider">
              <span>or</span>
            </div>

            <button
              type="button"
              class="google-btn"
              (click)="onGoogleLogin()"
              [disabled]="isLoading()"
            >
              <svg class="google-icon" viewBox="0 0 24 24" width="18" height="18">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
              </svg>
              Continue with Google
            </button>

            <div class="auth-footer">
              <p>Don't have an account? <a routerLink="/register">Create one</a></p>
              <p class="merchant-note">Merchant partner? <a routerLink="/merchant-registration">Register business</a></p>
            </div>
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
      min-height: calc(100vh - 5rem);
      margin: -1.5rem;
      padding: 2.25rem 2rem 4rem;
      background: radial-gradient(55% 65% at 75% 45%, #b8432f 0%, #98311e 65%, #882b1a 100%), #98311e;
      color: #fff;
      border-radius: 24px;
      overflow: hidden;
      box-shadow: 0 20px 50px -20px rgba(40,10,5,0.45);
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
    .nav-pill:hover, .submit-btn:hover:not(:disabled) {
      background: #000;
      transform: translateY(-1px);
    }
    .hero-grid {
      max-width: 1200px;
      margin: 0 auto;
      display: grid;
      grid-template-columns: 1.1fr 0.9fr;
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
    .chip-2 { margin-left: 1.5rem; animation-delay: -2.3s; }
    .chip-3 { margin-left: 0.5rem; animation-delay: -4.6s; }
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
    .auth-card {
      width: 100%;
      max-width: 440px;
      padding: 2.75rem 2.25rem;
      background: #fff;
      color: var(--ink);
      border-radius: 28px;
      box-shadow: 0 24px 60px -15px rgba(29,27,44,0.35), 0 8px 24px -6px rgba(0,0,0,0.2);
    }
    .card-header { margin-bottom: 2rem; text-align: left; }
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
      margin-bottom: 0.75rem;
    }
    .card-header h2 {
      font-family: var(--disp);
      font-size: 1.85rem;
      font-weight: 900;
      color: var(--ink);
      letter-spacing: -0.025em;
      margin: 0 0 0.5rem;
    }
    .card-header p {
      font-size: 0.9rem;
      color: var(--ink-3);
      line-height: 1.45;
      margin: 0;
    }
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
    .auth-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
    }
    .form-group label {
      font-size: 0.875rem;
      font-weight: 700;
      color: var(--ink);
    }
    .password-label-container {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .forgot-password-link {
      font-size: 0.8125rem;
      color: var(--terracotta);
      font-weight: 700;
      text-decoration: none;
    }
    .forgot-password-link:hover, .auth-footer a:hover { text-decoration: underline; }
    .form-group input {
      padding: 0.85rem 1.1rem;
      border-radius: 14px;
      border: 1.5px solid #e2e8f0;
      background: #f8fafc;
      color: var(--ink);
      font-size: 0.95rem;
      transition: all 0.2s ease;
    }
    .form-group input:focus {
      outline: none;
      background: #fff;
      border-color: var(--terracotta);
      box-shadow: 0 0 0 4px rgba(165,58,38,0.15);
    }
    .form-group input.invalid {
      border-color: #ef4444;
      background: #fff5f5;
    }
    .field-error {
      font-size: 0.75rem;
      font-weight: 600;
      color: #dc2626;
    }
    .submit-btn {
      margin-top: 0.5rem;
      height: 50px;
      border-radius: 9999px;
      border: none;
      background: var(--ink);
      color: #fff;
      font-size: 1rem;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 0.5rem;
      box-shadow: 0 10px 25px -8px rgba(29,27,44,0.4);
      transition: all 0.2s ease;
    }
    .submit-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
    }
    .spinner {
      width: 1rem;
      height: 1rem;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .oauth-divider {
      display: flex;
      align-items: center;
      margin: 1.5rem 0 1.25rem;
      text-align: center;
    }
    .oauth-divider::before, .oauth-divider::after {
      content: '';
      flex: 1;
      border-bottom: 1.5px solid #e2e8f0;
    }
    .oauth-divider span {
      padding: 0 0.85rem;
      color: var(--ink-3);
      font-size: 0.8125rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 700;
    }
    .google-btn {
      width: 100%;
      height: 48px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      background: #fff;
      color: var(--ink);
      font-size: 0.95rem;
      font-weight: 700;
      border: 1.5px solid #e2e8f0;
      border-radius: 9999px;
      cursor: pointer;
      transition: all 0.2s ease;
    }
    .google-btn:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #cbd5e1;
      box-shadow: 0 4px 12px rgba(0,0,0,0.06);
    }
    .google-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .auth-footer {
      margin-top: 2rem;
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
    .merchant-note { font-size: 0.8125rem; }
    @media (max-width: 960px) {
      .yum-page { padding: 1.75rem 1.25rem 3rem; }
      .hero-grid { grid-template-columns: 1fr; gap: 2.75rem; }
      .hero-copy { align-items: center; text-align: center; }
      .hero-headline { font-size: 2.5rem; }
      .said-chips { align-items: center; }
      .said-chip { align-self: center; }
      .chip-2, .chip-3 { margin-left: 0; }
      .auth-card { padding: 2.25rem 1.75rem; }
    }
    @media (max-width: 480px) {
      .top-bar { flex-direction: column; gap: 1rem; }
      .hero-headline { font-size: 2.1rem; }
      .said-chip { font-size: 0.825rem; padding: 0.65rem 1rem; }
      .auth-card { padding: 1.75rem 1.25rem; border-radius: 20px; }
    }
  `]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  onGoogleLogin(): void {
    this.errorMessage.set(null);
    this.isLoading.set(true);
    try {
      this.authService.loginWithGoogle();
    } catch {
      this.isLoading.set(false);
      this.errorMessage.set('Unable to initiate Google sign-in. Please try again.');
    }
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.loginForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.loginForm.value).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        const role = response.user.role?.toLowerCase();
        const target = role ? `/${role}` : '/customer';
        this.router.navigate([target]);
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 401) {
          this.errorMessage.set('Invalid email or password credentials.');
        } else if (err.status === 423) {
          this.errorMessage.set('Account is temporarily locked due to repeated failed login attempts.');
        } else if (err.status === 403) {
          this.errorMessage.set('Account is disabled or pending approval.');
        } else if (err.status === 429) {
          this.errorMessage.set('Too many login attempts. Please wait a minute and try again.');
        } else {
          this.errorMessage.set(err.error?.message || 'Authentication failed. Please try again.');
        }
      }
    });
  }
}
