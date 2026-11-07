import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../services/auth.service';
import { of, throwError } from 'rxjs';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let authServiceMock: {
    resetPassword: jest.Mock;
  };
  let router: Router;

  const createComponentWithToken = async (token: string | null) => {
    authServiceMock = {
      resetPassword: jest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => (key === 'token' ? token : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
    fixture.detectChanges();
  };

  describe('with valid token', () => {
    beforeEach(async () => {
      await createComponentWithToken('valid-token-123');
    });

    it('should initialize with token and invalid empty form', () => {
      expect(component.missingToken()).toBe(false);
      expect(component.token()).toBe('valid-token-123');
      expect(component.resetForm.valid).toBe(false);
    });

    it('should validate password length and required fields', () => {
      const newPwd = component.resetForm.get('newPassword');
      const confirmPwd = component.resetForm.get('confirmPassword');

      newPwd?.setValue('short');
      confirmPwd?.setValue('short');
      expect(newPwd?.hasError('minlength')).toBe(true);
      expect(component.resetForm.valid).toBe(false);

      newPwd?.setValue('ValidP@ssword123');
      confirmPwd?.setValue('ValidP@ssword123');
      expect(component.resetForm.valid).toBe(true);
    });

    it('should flag password mismatch when passwords differ', () => {
      component.resetForm.setValue({
        newPassword: 'PasswordOne1!',
        confirmPassword: 'PasswordTwo2!',
      });
      component.resetForm.get('confirmPassword')?.markAsDirty();
      expect(component.hasMismatchError()).toBe(true);
      expect(component.resetForm.valid).toBe(false);
    });

    it('should toggle password visibility signals', () => {
      expect(component.showPassword()).toBe(false);
      component.toggleShowPassword();
      expect(component.showPassword()).toBe(true);

      expect(component.showConfirmPassword()).toBe(false);
      component.toggleShowConfirmPassword();
      expect(component.showConfirmPassword()).toBe(true);
    });

    it('should submit successfully and navigate to login', () => {
      const response = {
        message: 'Password has been successfully reset. Please log in with your new password.',
      };
      authServiceMock.resetPassword.mockReturnValue(of(response));

      component.resetForm.setValue({
        newPassword: 'BrandNewPassword123!',
        confirmPassword: 'BrandNewPassword123!',
      });

      component.onSubmit();

      expect(authServiceMock.resetPassword).toHaveBeenCalledWith({
        token: 'valid-token-123',
        newPassword: 'BrandNewPassword123!',
      });
      expect(component.isLoading()).toBe(false);
      expect(component.isSuccess()).toBe(true);
      expect(component.successMessage()).toBe(response.message);

      component.navigateToLogin();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('should display error message on 400 Invalid/Expired token', () => {
      authServiceMock.resetPassword.mockReturnValue(
        throwError(() => ({
          status: 400,
          error: { code: 'INVALID_RESET_TOKEN', message: 'Token is expired' },
        }))
      );

      component.resetForm.setValue({
        newPassword: 'BrandNewPassword123!',
        confirmPassword: 'BrandNewPassword123!',
      });

      component.onSubmit();

      expect(component.isLoading()).toBe(false);
      expect(component.isSuccess()).toBe(false);
      expect(component.errorMessage()).toBe('Token is expired');
    });
  });

  describe('with missing token', () => {
    beforeEach(async () => {
      await createComponentWithToken(null);
    });

    it('should set missingToken signal to true', () => {
      expect(component.missingToken()).toBe(true);
      expect(component.token()).toBeNull();
    });
  });
});
