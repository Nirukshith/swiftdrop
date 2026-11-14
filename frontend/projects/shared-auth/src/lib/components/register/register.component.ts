import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'lib-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
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
          <a routerLink="/register" class="nav-pill">Customer</a>
          <a routerLink="/merchant-registration" class="nav-link">Merchant</a>
          <a routerLink="/login" class="nav-link">Sign In</a>
        </nav>
      </header>

      <main class="hero-grid">
        <!-- Left Column: Customer Hero Copy & Spoken Chips -->
        <section class="hero-copy">
          <div class="hero-badge">
            <span class="badge-dot"></span> SwiftDrop Customer Registration
          </div>

          <h1 class="hero-headline">Create your customer account</h1>

          <p class="hero-lede">
            Sign up in seconds to schedule door-to-door deliveries, monitor parcels in real time, and enjoy automated doorstep collections.
          </p>

          <ul class="said-chips" aria-label="Customer quick commands">
            <li class="said-chip chip-1">
              <svg class="chip-icon mic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="22"/>
              </svg>
              <span>“Send a care package to Galle today”</span>
            </li>
            <li class="said-chip chip-2">
              <svg class="chip-icon parcel" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                <line x1="12" y1="22.08" x2="12" y2="12"/>
              </svg>
              <span>“Track my delivery in real time”</span>
            </li>
            <li class="said-chip chip-3">
              <svg class="chip-icon check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <span>“Doorstep pickup confirmed”</span>
            </li>
          </ul>

          <div class="hero-store-row">
            <div class="store-badge">
              <svg class="apple-icon" viewBox="0 0 384 512" fill="currentColor">
                <path d="M318.7 268.7c-.2-36.7 16.4-64.4 50-84.8-18.8-26.9-47.2-41.7-84.7-44.6-35.5-2.8-74.3 20.7-88.5 20.7-15 0-49.4-19.7-76.4-19.7C63.3 141.2 4 184.8 4 273.5q0 39.3 14.4 81.2c12.8 36.7 59 126.7 107.2 125.2 25.2-.6 43-17.9 75.8-17.9 31.8 0 48.3 17.9 76.4 17.9 48.6-.7 90.4-82.5 102.6-119.3-65.2-30.7-61.7-90-61.7-91.9zm-56.6-164.2c27.3-32.4 24.8-61.9 24-72.5-24.1 1.4-52 16.4-67.9 34.9-17.5 19.8-27.8 44.3-25.6 71.9 26.1 2 49.9-11.4 69.5-34.3z"/>
              </svg>
              <div class="store-text">
                <span class="store-small">Island-wide logistics</span>
                <span class="store-bold">SwiftDrop Instant Dispatch</span>
              </div>
            </div>
          </div>
        </section>

        <!-- Right Column: Tactile Customer Signup White Card -->
        <section class="auth-card-wrap">
          <div class="auth-card">
            <div class="card-header">
              <span class="card-pill">Customer Signup</span>
              <h2>Join SwiftDrop</h2>
              <p>Enter your details to create your delivery account</p>
            </div>

            @if (errorMessage()) {
              <div class="error-banner" role="alert">
                <svg class="error-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
                </svg>
                <span>{{ errorMessage() }}</span>
              </div>
            }

            <form [formGroup]="registerForm" (ngSubmit)="onSubmit()" class="auth-form">
              <div class="form-group">
                <label for="fullName">Full Name</label>
                <input
                  id="fullName"
                  type="text"
                  formControlName="fullName"
                  placeholder="Kasun Perera"
                  [class.invalid]="isFieldInvalid('fullName')"
                  autocomplete="name"
                />
                @if (isFieldInvalid('fullName')) {
                  <span class="field-error">Full name is required</span>
                }
              </div>

              <div class="form-group">
                <label for="email">Email address</label>
                <input
                  id="email"
                  type="email"
                  formControlName="email"
                  placeholder="you@example.com"
                  [class.invalid]="isFieldInvalid('email')"
                  autocomplete="email"
                />
                @if (isFieldInvalid('email')) {
                  <span class="field-error">Please enter a valid email address</span>
                }
              </div>

              <div class="form-group">
                <label for="phoneNumber">Phone Number (optional)</label>
                <input
                  id="phoneNumber"
                  type="tel"
                  formControlName="phoneNumber"
                  placeholder="+94771234567"
                  autocomplete="tel"
                />
              </div>

              <div class="form-group">
                <label for="password">Password</label>
                <input
                  id="password"
                  type="password"
                  formControlName="password"
                  placeholder="At least 8 characters"
                  [class.invalid]="isFieldInvalid('password')"
                  autocomplete="new-password"
                />
                @if (isFieldInvalid('password')) {
                  <span class="field-error">Password must be at least 8 characters</span>
                }
              </div>

              <button
                type="submit"
                class="submit-btn"
                [disabled]="registerForm.invalid || isLoading()"
              >
                @if (isLoading()) {
                  <span class="spinner"></span> Creating account...
                } @else {
                  Create Account
                }
              </button>
            </form>

            <div class="auth-footer">
              <p>Already have an account? <a routerLink="/login">Sign in</a></p>
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
      max-width: 480px;
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
    .auth-footer a:hover { text-decoration: underline; }
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
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly registerForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phoneNumber: [''],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]]
  });

  isFieldInvalid(fieldName: string): boolean {
    const field = this.registerForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.register(this.registerForm.value).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/customer']);
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.errorMessage.set('This email address is already registered.');
        } else if (err.status === 429) {
          this.errorMessage.set('Too many registration attempts. Please wait a moment and try again.');
        } else {
          this.errorMessage.set(err.error?.message || 'Registration failed. Please check your details.');
        }
      }
    });
  }
}
