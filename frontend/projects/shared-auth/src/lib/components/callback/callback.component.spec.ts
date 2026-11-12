import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CallbackComponent } from './callback.component';
import { AuthService } from '../../services/auth.service';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';

describe('CallbackComponent', () => {
  let component: CallbackComponent;
  let fixture: ComponentFixture<CallbackComponent>;
  let router: Router;
  let authServiceMock: {
    initializeSession: jest.Mock;
    isAuthenticated: jest.Mock;
    userRole: jest.Mock;
  };
  let queryParamMapMock: Map<string, string>;

  beforeEach(async () => {
    queryParamMapMock = new Map<string, string>();

    authServiceMock = {
      initializeSession: jest.fn().mockResolvedValue(true),
      isAuthenticated: jest.fn().mockReturnValue(true),
      userRole: jest.fn().mockReturnValue('CUSTOMER'),
    };

    await TestBed.configureTestingModule({
      imports: [CallbackComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => queryParamMapMock.get(key) || null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(CallbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should initialize session and navigate to /customer on clean callback', async () => {
    createComponent();
    await fixture.whenStable();

    expect(authServiceMock.initializeSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/customer'], { replaceUrl: true });
    expect(component.errorMessage()).toBeNull();
  });

  it('should navigate to user role route if user has different role', async () => {
    authServiceMock.userRole.mockReturnValue('MERCHANT');
    createComponent();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/merchant'], { replaceUrl: true });
  });

  it('should display error message when access_denied error parameter is present', async () => {
    queryParamMapMock.set('error', 'access_denied');
    createComponent();
    await fixture.whenStable();

    expect(authServiceMock.initializeSession).not.toHaveBeenCalled();
    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toContain('Google sign-in was canceled or denied');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should display error message when account_disabled error parameter is present', async () => {
    queryParamMapMock.set('error', 'account_disabled');
    createComponent();
    await fixture.whenStable();

    expect(authServiceMock.initializeSession).not.toHaveBeenCalled();
    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toContain('account is disabled');
  });

  it('should display generic error message for other error parameters', async () => {
    queryParamMapMock.set('error', 'server_error');
    createComponent();
    await fixture.whenStable();

    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toContain('Authentication failed');
  });

  it('should display error when session initialization fails', async () => {
    authServiceMock.initializeSession.mockResolvedValue(false);
    createComponent();
    await fixture.whenStable();

    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toContain('Failed to restore session');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should display error when initializeSession throws an exception', async () => {
    authServiceMock.initializeSession.mockRejectedValue(new Error('Network error'));
    createComponent();
    await fixture.whenStable();

    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toContain('unexpected error occurred');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  describe('role-based navigation matrix', () => {
    const roleRoutes: { role: string; expectedPath: string }[] = [
      { role: 'CUSTOMER',   expectedPath: '/customer'   },
      { role: 'MERCHANT',   expectedPath: '/merchant'   },
      { role: 'COURIER',    expectedPath: '/courier'    },
      { role: 'DISPATCHER', expectedPath: '/dispatcher' },
      { role: 'ADMIN',      expectedPath: '/admin'      },
    ];

    roleRoutes.forEach(({ role, expectedPath }) => {
      it(`should navigate to ${expectedPath} when role is ${role}`, async () => {
        authServiceMock.userRole.mockReturnValue(role);
        createComponent();
        await fixture.whenStable();

        expect(authServiceMock.initializeSession).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(
          [expectedPath],
          { replaceUrl: true }
        );
        expect(component.errorMessage()).toBeNull();
      });
    });
  });

  it('should not write any token to localStorage', async () => {
    const setItemSpy = jest.spyOn(Storage.prototype, 'setItem');
    createComponent();
    await fixture.whenStable();

    const tokenWrites = setItemSpy.mock.calls.filter(([key]) =>
      key.toLowerCase().includes('token') ||
      key.toLowerCase().includes('access') ||
      key.toLowerCase().includes('refresh')
    );
    expect(tokenWrites).toHaveLength(0);
    setItemSpy.mockRestore();
  });
});

