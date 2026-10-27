import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthResponse } from '../../models/auth.models';
import { provideRouter } from '@angular/router';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceMock: {
    login: jest.Mock;
  };
  let router: Router;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-token',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'usr-1',
      email: 'test@swiftdrop.lk',
      fullName: 'Test User',
      role: 'CUSTOMER',
      merchantId: null,
    },
  };

  beforeEach(async () => {
    authServiceMock = {
      login: jest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
    fixture.detectChanges();
  });

  it('should initialize with empty form and invalid state', () => {
    expect(component.loginForm.valid).toBe(false);
    expect(component.loginForm.get('email')?.value).toBe('');
    expect(component.loginForm.get('password')?.value).toBe('');
  });

  it('should validate email format and required password', () => {
    const emailControl = component.loginForm.get('email');
    const passwordControl = component.loginForm.get('password');

    emailControl?.setValue('invalid-email');
    passwordControl?.setValue('');
    expect(emailControl?.valid).toBe(false);
    expect(component.loginForm.valid).toBe(false);

    emailControl?.setValue('valid@swiftdrop.lk');
    passwordControl?.setValue('Password123!');
    expect(emailControl?.valid).toBe(true);
    expect(passwordControl?.valid).toBe(true);
    expect(component.loginForm.valid).toBe(true);
  });

  it('should navigate to user role route on successful login', () => {
    authServiceMock.login.mockReturnValue(of(mockAuthResponse));

    component.loginForm.setValue({
      email: 'test@swiftdrop.lk',
      password: 'Password123!',
    });

    component.onSubmit();

    expect(authServiceMock.login).toHaveBeenCalledWith({
      email: 'test@swiftdrop.lk',
      password: 'Password123!',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/customer']);
    expect(component.errorMessage()).toBeNull();
    expect(component.isLoading()).toBe(false);
  });

  it('should display error message on 401 Unauthorized', () => {
    authServiceMock.login.mockReturnValue(throwError(() => ({ status: 401 })));

    component.loginForm.setValue({
      email: 'test@swiftdrop.lk',
      password: 'WrongPassword!',
    });

    component.onSubmit();

    expect(component.errorMessage()).toContain('Invalid email or password');
    expect(component.isLoading()).toBe(false);
  });

  it('should display locked account message on 423 Locked', () => {
    authServiceMock.login.mockReturnValue(throwError(() => ({ status: 423 })));

    component.loginForm.setValue({
      email: 'test@swiftdrop.lk',
      password: 'WrongPassword!',
    });

    component.onSubmit();

    expect(component.errorMessage()).toContain('temporarily locked');
    expect(component.isLoading()).toBe(false);
  });

  it('should display disabled account message on 403 Forbidden', () => {
    authServiceMock.login.mockReturnValue(throwError(() => ({ status: 403 })));

    component.loginForm.setValue({
      email: 'disabled@swiftdrop.lk',
      password: 'Password123!',
    });

    component.onSubmit();

    expect(component.errorMessage()).toContain('disabled or pending approval');
    expect(component.isLoading()).toBe(false);
  });

  it('should display rate limit message on 429 Too Many Requests', () => {
    authServiceMock.login.mockReturnValue(throwError(() => ({ status: 429 })));

    component.loginForm.setValue({
      email: 'spammer@swiftdrop.lk',
      password: 'Password123!',
    });

    component.onSubmit();

    expect(component.errorMessage()).toContain('Too many login attempts');
    expect(component.isLoading()).toBe(false);
  });
});
