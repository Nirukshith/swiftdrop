import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'lib-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-header">
          <div class="brand-badge">SwiftDrop</div>
          <h2>Reset your password</h2>
          <p>Enter your email address and we'll send you instructions to reset your password.</p>
        </div>

        @if (successMessage()) {
          <div class="success-banner" role="status">
            <span class="success-icon">✓</span>
            <div>
              <strong>Check your inbox</strong>
              <p>{{ successMessage() }}</p>
            </div>
          </div>
        }

        @if (errorMessage()) {
          <div class="error-banner" role="alert">
            <span class="error-icon">⚠️</span>
            <span>{{ errorMessage() }}</span>
          </div>
        }

        @if (!successMessage()) {
          <form [formGroup]="forgotForm" (ngSubmit)="onSubmit()" class="auth-form">
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

            <button
              type="submit"
              class="submit-btn"
              [disabled]="forgotForm.invalid || isLoading()"
            >
              @if (isLoading()) {
                <span class="spinner"></span> Sending instructions...
              } @else {
                Send reset link
              }
            </button>
          </form>
        }

        <div class="auth-footer">
          <p>Remembered your password? <a routerLink="/login">Back to Sign in</a></p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      padding: 2rem 1rem;
      background: radial-gradient(circle at top right, #1e293b 0%, #0f172a 100%);
      color: #f8fafc;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .auth-card {
      width: 100%;
      max-width: 440px;
      padding: 2.5rem;
      background: rgba(30, 41, 59, 0.7);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 1rem;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3);
    }

    .auth-header {
      text-align: center;
      margin-bottom: 2rem;
    }

    .brand-badge {
      display: inline-block;
      padding: 0.25rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
      color: white;
      border-radius: 9999px;
      margin-bottom: 0.75rem;
    }

    .auth-header h2 {
      font-size: 1.5rem;
      font-weight: 700;
      color: #f8fafc;
      margin: 0 0 0.5rem 0;
    }

    .auth-header p {
      font-size: 0.875rem;
      color: #94a3b8;
      margin: 0;
      line-height: 1.4;
    }

    .success-banner {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      background: rgba(34, 197, 94, 0.15);
      border: 1px solid #22c55e;
      color: #86efac;
      padding: 1rem;
      border-radius: 0.5rem;
      margin-bottom: 1.5rem;
      font-size: 0.875rem;
      line-height: 1.4;
    }

    .success-banner strong {
      display: block;
      color: #4ade80;
      margin-bottom: 0.25rem;
    }

    .success-banner p {
      margin: 0;
      color: #dcfce7;
    }

    .success-icon {
      font-size: 1.1rem;
      color: #4ade80;
    }

    .error-banner {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid #ef4444;
      color: #fca5a5;
      padding: 0.75rem 1rem;
      border-radius: 0.5rem;
      margin-bottom: 1.5rem;
      font-size: 0.875rem;
    }

    .auth-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }

    .form-group label {
      font-size: 0.875rem;
      font-weight: 500;
      color: #cbd5e1;
    }

    .form-group input {
      padding: 0.75rem 1rem;
      border-radius: 0.5rem;
      border: 1px solid rgba(255, 255, 255, 0.15);
      background: rgba(15, 23, 42, 0.6);
      color: #f8fafc;
      font-size: 0.95rem;
      transition: all 0.2s ease;
    }

    .form-group input:focus {
      outline: none;
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
    }

    .form-group input.invalid {
      border-color: #ef4444;
    }

    .field-error {
      font-size: 0.75rem;
      color: #ef4444;
    }

    .submit-btn {
      margin-top: 0.5rem;
      padding: 0.875rem 1.25rem;
      border-radius: 0.5rem;
      border: none;
      background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
      color: white;
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 0.5rem;
      transition: all 0.2s ease;
    }

    .submit-btn:hover:not(:disabled) {
      background: linear-gradient(135deg, #2563eb 0%, #1e40af 100%);
      box-shadow: 0 4px 12px rgba(37, 99, 255, 0.35);
    }

    .submit-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .spinner {
      width: 1rem;
      height: 1rem;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .auth-footer {
      margin-top: 2rem;
      text-align: center;
      font-size: 0.875rem;
      color: #94a3b8;
    }

    .auth-footer a {
      color: #60a5fa;
      text-decoration: none;
      font-weight: 500;
    }

    .auth-footer a:hover {
      text-decoration: underline;
    }
  `]
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly forgotForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  isFieldInvalid(fieldName: string): boolean {
    const field = this.forgotForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  onSubmit(): void {
    if (this.forgotForm.invalid) {
      this.forgotForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.forgotPassword(this.forgotForm.value).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        this.successMessage.set(response.message || 'If an account exists with this email, password reset instructions have been sent.');
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Unable to process your request. Please try again.');
      }
    });
  }
}

