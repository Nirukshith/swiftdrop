import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MerchantOnboardingService } from './merchant-onboarding.service';
import { MerchantRegistrationComponent } from './merchant-registration.component';

describe('MerchantRegistrationComponent', () => {
  let fixture: ComponentFixture<MerchantRegistrationComponent>;
  let component: MerchantRegistrationComponent;
  let onboardingService: { register: jest.Mock };

  beforeEach(async () => {
    onboardingService = { register: jest.fn() };
    await TestBed.configureTestingModule({
      imports: [MerchantRegistrationComponent],
      providers: [
        provideRouter([]),
        { provide: MerchantOnboardingService, useValue: onboardingService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(MerchantRegistrationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('requires contract fields and validates the phone format', () => {
    expect(component.form.invalid).toBe(true);
    component.form.setValue({
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: 'invalid',
      businessRegistrationNumber: '',
    });
    expect(component.form.invalid).toBe(true);
    expect(onboardingService.register).not.toHaveBeenCalled();
  });

  it('submits the merchant registration and shows pending approval', () => {
    onboardingService.register.mockReturnValue(of({
      id: 'merchant-1',
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: '+94771234567',
      status: 'PENDING',
      createdAt: '2026-10-28T00:00:00Z',
      message: 'Registration submitted for administrator review.',
    }));
    component.form.setValue({
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: '+94771234567',
      businessRegistrationNumber: '',
    });

    component.submit();
    fixture.detectChanges();

    expect(onboardingService.register).toHaveBeenCalledWith({
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: '+94771234567',
    });
    expect(fixture.nativeElement.textContent).toContain('Status:');
    const badge = fixture.nativeElement.querySelector('ui-status-badge');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('PENDING');
    const resetLink = fixture.nativeElement.querySelector('a[href="/forgot-password"]');
    expect(resetLink?.textContent).toContain('Forgot password');
    expect(fixture.nativeElement.textContent).toContain('After approval');
    expect(fixture.nativeElement.textContent).toContain('separate from your sign-in password');
  });

  it('shows a duplicate email message for a registration conflict', () => {
    onboardingService.register.mockReturnValue(throwError(() => ({ status: 409 })));
    component.form.setValue({
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: '+94771234567',
      businessRegistrationNumber: '',
    });

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toContain('already registered');
  });

  it('renders UI kit components: card, inputs, and submit button', () => {
    const card = fixture.nativeElement.querySelector('ui-card');
    expect(card).toBeTruthy();

    const inputs = fixture.nativeElement.querySelectorAll('ui-input');
    expect(inputs.length).toBe(5);

    const submitBtn = fixture.nativeElement.querySelector('ui-button[type="submit"]');
    expect(submitBtn).toBeTruthy();
  });

  it('renders Customer, Merchant, and Sign In in top bar navigation', () => {
    const navLinks = fixture.nativeElement.querySelectorAll('.nav-links a');
    expect(navLinks.length).toBe(3);
    expect(navLinks[0].textContent.trim()).toBe('Customer');
    expect(navLinks[1].textContent.trim()).toBe('Merchant');
    expect(navLinks[2].textContent.trim()).toBe('Sign In');
  });
});
