import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AdminUsersComponent } from './admin-users.component';
import { AdminUsersService } from './admin-users.service';
import { UserSummary } from './admin-users.model';

describe('AdminUsersComponent', () => {
  let component: AdminUsersComponent;
  let fixture: ComponentFixture<AdminUsersComponent>;
  let adminUsersServiceMock: any;

  const mockUsers: UserSummary[] = [
    {
      id: 'usr-1',
      email: 'courier.kamal@swiftdrop.lk',
      fullName: 'Kamal Gunaratne',
      phoneNumber: '+94771234567',
      role: 'COURIER',
      enabled: true,
      createdAt: '2026-10-25T13:20:00Z',
    },
    {
      id: 'usr-2',
      email: 'dispatch.anura@swiftdrop.lk',
      fullName: 'Anura Silva',
      phoneNumber: '+94777654321',
      role: 'DISPATCHER',
      enabled: false,
      createdAt: '2026-10-26T10:00:00Z',
    },
  ];

  beforeEach(async () => {
    adminUsersServiceMock = {
      users: jest.fn(() => mockUsers),
      totalElements: jest.fn(() => 2),
      totalPages: jest.fn(() => 1),
      currentPage: jest.fn(() => 0),
      pageSize: jest.fn(() => 20),
      loading: jest.fn(() => false),
      error: jest.fn(() => null),
      loadUsers: jest.fn(() =>
        of({
          content: mockUsers,
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 20,
        })
      ),
      createStaffUser: jest.fn(),
      updateUserStatus: jest.fn(),
      clearState: jest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminUsersComponent, FormsModule],
      providers: [
        { provide: AdminUsersService, useValue: adminUsersServiceMock },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render staff user management table with real backend data', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#open-create-user-modal-btn')).toBeTruthy();
    expect(compiled.querySelector('#user-role-filter')).toBeTruthy();
    expect(compiled.querySelector('#user-status-filter')).toBeTruthy();
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalledWith(0, 20, undefined, undefined);

    const rows = compiled.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(compiled.textContent).toContain('Kamal Gunaratne');
    expect(compiled.textContent).toContain('Anura Silva');
  });

  it('should trigger server-side reload when role filter changes', () => {
    component.onRoleFilterChange('COURIER');
    expect(component.userRoleFilter()).toBe('COURIER');
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalledWith(0, 20, 'COURIER', undefined);
  });

  it('should trigger server-side reload when status filter changes', () => {
    component.onStatusFilterChange('true');
    expect(component.userStatusFilter()).toBe('true');
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalledWith(0, 20, undefined, true);
  });

  it('should toggle user account status via PATCH request', () => {
    const userToToggle = mockUsers[0]; // currently enabled: true
    const updatedUser: UserSummary = { ...userToToggle, enabled: false };
    adminUsersServiceMock.updateUserStatus.mockReturnValue(of(updatedUser));

    component.toggleUserStatus(userToToggle);

    expect(adminUsersServiceMock.updateUserStatus).toHaveBeenCalledWith('usr-1', false);
    expect(component.toastMessage()).toContain('Disabled');
    expect(component.toastType()).toBe('success');
  });

  it('should handle toggle status failure without corrupting state', () => {
    const userToToggle = mockUsers[0];
    adminUsersServiceMock.updateUserStatus.mockReturnValue(
      throwError(() => ({ status: 500, error: { message: 'Database failure' } }))
    );

    component.toggleUserStatus(userToToggle);

    expect(component.toastMessage()).toContain('Database failure');
    expect(component.toastType()).toBe('error');
    expect(component.updatingUserId()).toBeNull();
  });

  it('should validate form fields according to OpenAPI contract constraints', () => {
    component.newUserFullName = '';
    component.newUserEmail = 'invalid-email';
    component.newUserPhone = '123'; // invalid: must be 10-14 digits
    component.newUserPassword = 'short'; // invalid: min 8 chars
    component.newUserRole = 'COURIER';

    expect(component.isPhoneValid()).toBe(false);
    expect(component.isFormValid()).toBe(false);

    component.newUserFullName = 'Saman Kumara';
    component.newUserEmail = 'courier.saman@swiftdrop.lk';
    component.newUserPhone = '+94771234567'; // valid
    component.newUserPassword = 'SecurePassword#2026'; // valid

    expect(component.isPhoneValid()).toBe(true);
    expect(component.isFormValid()).toBe(true);
  });

  it('should submit valid staff creation and close modal on HTTP 201 success', () => {
    component.openCreateUserModal();
    component.newUserFullName = 'Saman Kumara';
    component.newUserEmail = 'courier.saman@swiftdrop.lk';
    component.newUserPhone = '+94771234567';
    component.newUserPassword = 'SecurePassword#2026';
    component.newUserRole = 'COURIER';

    const createdResponse: UserSummary = {
      id: 'usr-new-1',
      email: 'courier.saman@swiftdrop.lk',
      fullName: 'Saman Kumara',
      phoneNumber: '+94771234567',
      role: 'COURIER',
      enabled: true,
      createdAt: '2026-10-28T12:00:00Z',
    };
    adminUsersServiceMock.createStaffUser.mockReturnValue(of(createdResponse));

    component.submitCreateStaffUser();

    expect(adminUsersServiceMock.createStaffUser).toHaveBeenCalledWith({
      fullName: 'Saman Kumara',
      email: 'courier.saman@swiftdrop.lk',
      phoneNumber: '+94771234567',
      password: 'SecurePassword#2026',
      role: 'COURIER',
    });
    expect(component.toastMessage()).toContain('created successfully');
    expect(component.isCreateUserModalOpen()).toBe(false);
    expect(component.newUserPassword).toBe(''); // sensitive field cleared
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalled();
  });

  it('should handle HTTP 409 duplicate email by keeping modal open and displaying error', () => {
    component.openCreateUserModal();
    component.newUserFullName = 'Saman Kumara';
    component.newUserEmail = 'courier.duplicate@swiftdrop.lk';
    component.newUserPhone = '+94771234567';
    component.newUserPassword = 'SecurePassword#2026';
    component.newUserRole = 'COURIER';

    adminUsersServiceMock.createStaffUser.mockReturnValue(
      throwError(() => ({
        status: 409,
        error: { message: 'A user account with this email address already exists.' },
      }))
    );

    component.submitCreateStaffUser();

    expect(component.isCreateUserModalOpen()).toBe(true);
    expect(component.modalError()).toContain('already exists');
    expect(component.toastType()).toBe('error');
    expect(component.newUserEmail).toBe('courier.duplicate@swiftdrop.lk'); // user can fix email
  });

  it('should display loading spinner when service is loading', () => {
    adminUsersServiceMock.loading.mockReturnValue(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#admin-users-loading')).toBeTruthy();
  });

  it('should display empty state when user list is empty and not loading', () => {
    adminUsersServiceMock.users.mockReturnValue([]);
    adminUsersServiceMock.loading.mockReturnValue(false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#admin-users-empty')).toBeTruthy();
    expect(compiled.textContent).toContain('No users found');
  });

  it('should display error message and allow retry when service has error', () => {
    adminUsersServiceMock.error.mockReturnValue('Network connection failed');
    adminUsersServiceMock.loading.mockReturnValue(false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const errorEl = compiled.querySelector('#admin-users-error');
    expect(errorEl).toBeTruthy();
    expect(errorEl?.textContent).toContain('Network connection failed');

    const retryBtn = compiled.querySelector('#admin-users-retry-btn') as HTMLButtonElement;
    retryBtn.click();
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalled();
  });

  it('should handle pagination next and previous page navigation', () => {
    adminUsersServiceMock.totalPages.mockReturnValue(3);
    adminUsersServiceMock.currentPage.mockReturnValue(1);
    adminUsersServiceMock.totalElements.mockReturnValue(45);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#page-info')?.textContent).toContain('Page 2 of 3');

    component.goToPage(2);
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalledWith(2, 20, undefined, undefined);

    component.goToPage(0);
    expect(adminUsersServiceMock.loadUsers).toHaveBeenCalledWith(0, 20, undefined, undefined);
  });
});
