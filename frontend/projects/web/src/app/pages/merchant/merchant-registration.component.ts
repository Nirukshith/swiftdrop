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
    <main class="mx-auto max-w-3xl space-y-6">
      <ui-card
        title="Register your business"
        subtitle="Submit your business details. An administrator must approve your account before API credentials are available."
      >
        <div uiCardActions>
          <span class="text-xs font-semibold uppercase tracking-wide text-brand-600">SwiftDrop partners</span>
        </div>

        @if (registration(); as result) {
          <div class="rounded-xl border border-emerald-200 bg-emerald-50 p-6" role="status">
            <p class="text-sm font-semibold text-emerald-800">Registration received</p>
            <h2 class="mt-1 text-xl font-bold text-slate-900">{{ result.businessName }}</h2>
            <p class="mt-2 text-sm text-slate-700">{{ result.message }}</p>
            <p class="mt-4 inline-flex items-center gap-2 text-xs font-semibold text-amber-800">
              Status: <ui-status-badge [status]="result.status" tone="warning" [label]="result.status" />
            </p>
            <p class="mt-4 text-sm text-slate-600">Once approved, an administrator will securely hand over your API client credentials. Store the secret safely; it is shown only once during approval.</p>
            <div class="mt-5">
              <ui-button routerLink="/login">Sign in</ui-button>
            </div>
          </div>
        } @else {
          @if (errorMessage()) {
            <div role="alert" class="mb-5 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{{ errorMessage() }}</div>
          }

          <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-5">
            <div class="grid gap-5 sm:grid-cols-2">
              <div class="sm:col-span-2">
                <ui-input
                  inputId="businessName"
                  label="Business name"
                  formControlName="businessName"
                  autocomplete="organization"
                  [messages]="{ required: 'Enter a business name (up to 200 characters).', maxlength: 'Enter a business name (up to 200 characters).' }"
                />
              </div>
              <div>
                <ui-input
                  inputId="contactName"
                  label="Contact name"
                  formControlName="contactName"
                  autocomplete="name"
                  [messages]="{ required: 'Enter a contact name (up to 200 characters).', maxlength: 'Enter a contact name (up to 200 characters).' }"
                />
              </div>
              <div>
                <ui-input
                  inputId="phoneNumber"
                  label="Phone number"
                  type="tel"
                  formControlName="phoneNumber"
                  placeholder="+94771234567"
                  autocomplete="tel"
                  [messages]="{ required: 'Use 10–14 digits, optionally starting with +.', pattern: 'Use 10–14 digits, optionally starting with +.' }"
                />
              </div>
              <div class="sm:col-span-2">
                <ui-input
                  inputId="email"
                  label="Business email"
                  type="email"
                  formControlName="email"
                  autocomplete="email"
                  [messages]="{ required: 'Enter a valid email address.', email: 'Enter a valid email address.' }"
                />
              </div>
              <div class="sm:col-span-2">
                <ui-input
                  inputId="businessRegistrationNumber"
                  label="Business registration number (optional)"
                  formControlName="businessRegistrationNumber"
                />
              </div>
            </div>
            <div class="flex flex-col justify-between gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-center">
              <p class="text-xs text-slate-500">Your account stays pending until administrator approval.</p>
              <ui-button type="submit" [disabled]="form.invalid || isSubmitting()" [loading]="isSubmitting()">
                {{ isSubmitting() ? 'Submitting…' : 'Submit registration' }}
              </ui-button>
            </div>
          </form>
        }
      </ui-card>
    </main>
  `,
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
