import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../services/auth.service';
import { of, throwError } from 'rxjs';
import { provideRouter } from '@angular/router';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceMock: {
    forgotPassword: jest.Mock;
  };

  beforeEach(async () => {
    authServiceMock = {
      forgotPassword: jest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize with invalid empty form', () => {
    expect(component.forgotForm.valid).toBe(false);
    expect(component.forgotForm.get('email')?.value).toBe('');
  });

  it('should validate email format', () => {
    const emailControl = component.forgotForm.get('email');

    emailControl?.setValue('invalid-email');
    expect(emailControl?.valid).toBe(false);
    expect(component.forgotForm.valid).toBe(false);

    emailControl?.setValue('valid.user@swiftdrop.lk');
    expect(emailControl?.valid).toBe(true);
    expect(component.forgotForm.valid).toBe(true);
  });

  it('should display generic success message on successful request', () => {
    const response = {
      message: 'If an account exists with this email, password reset instructions have been sent.',
    };
    authServiceMock.forgotPassword.mockReturnValue(of(response));

    component.forgotForm.setValue({ email: 'user@swiftdrop.lk' });
    component.onSubmit();

    expect(authServiceMock.forgotPassword).toHaveBeenCalledWith({ email: 'user@swiftdrop.lk' });
    expect(component.isLoading()).toBe(false);
    expect(component.successMessage()).toBe(response.message);
    expect(component.errorMessage()).toBeNull();
  });

  it('should display error message on API failure', () => {
    authServiceMock.forgotPassword.mockReturnValue(
      throwError(() => ({ status: 500, error: { message: 'Server error' } }))
    );

    component.forgotForm.setValue({ email: 'user@swiftdrop.lk' });
    component.onSubmit();

    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toBe('Server error');
    expect(component.successMessage()).toBeNull();
  });
});

