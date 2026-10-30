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
    expect(fixture.nativeElement.textContent).toContain('Status: PENDING');
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
});
