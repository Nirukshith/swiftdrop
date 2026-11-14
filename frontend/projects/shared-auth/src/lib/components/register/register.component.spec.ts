import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthResponse } from '../../models/auth.models';
import { provideRouter } from '@angular/router';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let authServiceMock: {
    register: jest.Mock;
  };
  let router: Router;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-token',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'usr-1',
      email: 'new@swiftdrop.lk',
      fullName: 'New User',
      role: 'CUSTOMER',
      merchantId: null,
    },
  };

  beforeEach(async () => {
    authServiceMock = {
      register: jest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
    fixture.detectChanges();
  });

  it('should initialize with empty form and invalid state', () => {
    expect(component.registerForm.valid).toBe(false);
  });

  it('should validate form fields', () => {
    component.registerForm.patchValue({
      fullName: '',
      email: 'not-an-email',
      password: 'short',
    });
    expect(component.registerForm.valid).toBe(false);

    component.registerForm.patchValue({
      fullName: 'Kasun Perera',
      email: 'kasun@swiftdrop.lk',
      password: 'SecurePassword123!',
      phoneNumber: '+94771234567',
    });
    expect(component.registerForm.valid).toBe(true);
  });

  it('should navigate to /customer on successful registration', () => {
    authServiceMock.register.mockReturnValue(of(mockAuthResponse));

    component.registerForm.setValue({
      fullName: 'Kasun Perera',
      email: 'kasun@swiftdrop.lk',
      phoneNumber: '+94771234567',
      password: 'SecurePassword123!',
    });

    component.onSubmit();

    expect(authServiceMock.register).toHaveBeenCalledWith({
      fullName: 'Kasun Perera',
      email: 'kasun@swiftdrop.lk',
      phoneNumber: '+94771234567',
      password: 'SecurePassword123!',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/customer']);
    expect(component.errorMessage()).toBeNull();
    expect(component.isLoading()).toBe(false);
  });

  it('should show duplicate email message on 409 Conflict', () => {
    authServiceMock.register.mockReturnValue(throwError(() => ({ status: 409 })));

    component.registerForm.setValue({
      fullName: 'Existing User',
      email: 'existing@swiftdrop.lk',
      phoneNumber: '',
      password: 'SecurePassword123!',
    });

    component.onSubmit();

    expect(component.errorMessage()).toContain('already registered');
    expect(component.isLoading()).toBe(false);
  });

  it('should show rate limit error on 429 Too Many Requests', () => {
    authServiceMock.register.mockReturnValue(throwError(() => ({ status: 429 })));

    component.registerForm.setValue({
      fullName: 'Spam User',
      email: 'spam@swiftdrop.lk',
      phoneNumber: '',
      password: 'SecurePassword123!',
    });

    component.onSubmit();

    expect(component.errorMessage()).toContain('Too many registration attempts');
    expect(component.isLoading()).toBe(false);
  });

  it('should render top bar with Customer, Merchant, and Sign In navigation links', () => {
    const navLinks = fixture.nativeElement.querySelectorAll('.nav-links a');
    expect(navLinks.length).toBe(3);
    expect(navLinks[0].textContent.trim()).toBe('Customer');
    expect(navLinks[1].textContent.trim()).toBe('Merchant');
    expect(navLinks[2].textContent.trim()).toBe('Sign In');
  });

  it('should render customer registration form directly without tab selection', () => {
    const form = fixture.nativeElement.querySelector('form.auth-form');
    expect(form).toBeTruthy();
    const roleTabs = fixture.nativeElement.querySelectorAll('.role-tab');
    expect(roleTabs.length).toBe(0);
  });

  it('should render link to merchant registration in the footer', () => {
    const merchantLink = fixture.nativeElement.querySelector('.auth-footer a[href="/merchant-registration"]');
    expect(merchantLink).toBeTruthy();
    expect(merchantLink.textContent.trim()).toContain('Register business');
  });
});
