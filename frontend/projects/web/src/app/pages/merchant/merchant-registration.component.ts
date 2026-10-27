import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MerchantOnboardingService } from './merchant-onboarding.service';
import { MerchantRegistrationResponse } from './merchant-registration.model';

@Component({
  selector: 'app-merchant-registration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="mx-auto max-w-3xl space-y-6">
      <header class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-semibold uppercase tracking-wide text-indigo-600">SwiftDrop partners</p>
        <h1 class="mt-2 text-2xl font-bold text-slate-900">Register your business</h1>
        <p class="mt-2 text-sm text-slate-600">Submit your business details. An administrator must approve your account before API credentials are available.</p>
      </header>

      @if (registration(); as result) {
        <section class="rounded-xl border border-emerald-200 bg-emerald-50 p-6" role="status">
          <p class="text-sm font-semibold text-emerald-800">Registration received</p>
          <h2 class="mt-1 text-xl font-bold text-slate-900">{{ result.businessName }}</h2>
          <p class="mt-2 text-sm text-slate-700">{{ result.message }}</p>
          <p class="mt-4 inline-flex rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800">Status: {{ result.status }}</p>
          <p class="mt-4 text-sm text-slate-600">Once approved, your API client credentials will be available in the Merchant Portal.</p>
          <a routerLink="/login" class="mt-5 inline-flex rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">Sign in</a>
        </section>
      } @else {
        @if (errorMessage()) {
          <div role="alert" class="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{{ errorMessage() }}</div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-5 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div class="grid gap-5 sm:grid-cols-2">
            <div class="sm:col-span-2">
              <label for="businessName" class="mb-1 block text-sm font-medium text-slate-700">Business name</label>
              <input id="businessName" formControlName="businessName" maxlength="200" autocomplete="organization" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
              @if (invalid('businessName')) { <p class="mt-1 text-xs text-rose-700">Enter a business name (up to 200 characters).</p> }
            </div>
            <div>
              <label for="contactName" class="mb-1 block text-sm font-medium text-slate-700">Contact name</label>
              <input id="contactName" formControlName="contactName" maxlength="200" autocomplete="name" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
              @if (invalid('contactName')) { <p class="mt-1 text-xs text-rose-700">Enter a contact name (up to 200 characters).</p> }
            </div>
            <div>
              <label for="phoneNumber" class="mb-1 block text-sm font-medium text-slate-700">Phone number</label>
              <input id="phoneNumber" type="tel" formControlName="phoneNumber" placeholder="+94771234567" autocomplete="tel" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
              @if (invalid('phoneNumber')) { <p class="mt-1 text-xs text-rose-700">Use 10–14 digits, optionally starting with +.</p> }
            </div>
            <div class="sm:col-span-2">
              <label for="email" class="mb-1 block text-sm font-medium text-slate-700">Business email</label>
              <input id="email" type="email" formControlName="email" autocomplete="email" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
              @if (invalid('email')) { <p class="mt-1 text-xs text-rose-700">Enter a valid email address.</p> }
            </div>
            <div class="sm:col-span-2">
              <label for="businessRegistrationNumber" class="mb-1 block text-sm font-medium text-slate-700">Business registration number <span class="font-normal text-slate-500">(optional)</span></label>
              <input id="businessRegistrationNumber" formControlName="businessRegistrationNumber" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
            </div>
          </div>
          <div class="flex flex-col justify-between gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-center">
            <p class="text-xs text-slate-500">Your account stays pending until administrator approval.</p>
            <button type="submit" [disabled]="form.invalid || isSubmitting()" class="rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50">
              {{ isSubmitting() ? 'Submitting…' : 'Submit registration' }}
            </button>
          </div>
        </form>
      }
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
