import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { AdminUsersService } from './admin-users.service';
import {
  CreateStaffUserRequest,
  UserListPage,
  UserSummary,
} from './admin-users.model';

describe('AdminUsersService', () => {
  let service: AdminUsersService;
  let httpMock: HttpTestingController;

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

  const mockListPage: UserListPage = {
    content: mockUsers,
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AdminUsersService],
    });

    service = TestBed.inject(AdminUsersService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created with initial empty signals', () => {
    expect(service).toBeTruthy();
    expect(service.users()).toEqual([]);
    expect(service.totalElements()).toBe(0);
    expect(service.totalPages()).toBe(0);
    expect(service.currentPage()).toBe(0);
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('should load users with default parameters and update signals', () => {
    service.loadUsers().subscribe((res) => {
      expect(res).toEqual(mockListPage);
    });

    const req = httpMock.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('role')).toBe(false);
    expect(req.request.params.has('enabled')).toBe(false);

    req.flush(mockListPage);

    expect(service.users()).toEqual(mockUsers);
    expect(service.totalElements()).toBe(2);
    expect(service.totalPages()).toBe(1);
    expect(service.currentPage()).toBe(0);
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('should pass role and enabled filter query params to GET request', () => {
    service.loadUsers(1, 10, 'COURIER', true).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('role')).toBe('COURIER');
    expect(req.request.params.get('enabled')).toBe('true');

    req.flush({
      content: [mockUsers[0]],
      totalElements: 1,
      totalPages: 1,
      page: 1,
      size: 10,
    });

    expect(service.users().length).toBe(1);
    expect(service.users()[0].role).toBe('COURIER');
    expect(service.totalElements()).toBe(1);
  });

  it('should handle GET error and update error signal', () => {
    service.loadUsers().subscribe({
      next: () => fail('Should have failed'),
      error: (err) => {
        expect(err.status).toBe(500);
      },
    });

    const req = httpMock.expectOne('/api/v1/admin/users?page=0&size=20');
    req.flush(
      { message: 'Database connection failed' },
      { status: 500, statusText: 'Internal Server Error' }
    );

    expect(service.error()).toBe('Database connection failed');
    expect(service.loading()).toBe(false);
  });

  it('should post create staff user and update signals on 201 response', () => {
    const payload: CreateStaffUserRequest = {
      email: 'courier.new@swiftdrop.lk',
      password: 'Password#2026',
      fullName: 'New Courier',
      phoneNumber: '+94773333333',
      role: 'COURIER',
    };

    const createdResponse: UserSummary = {
      id: 'usr-3',
      email: payload.email,
      fullName: payload.fullName,
      phoneNumber: payload.phoneNumber,
      role: payload.role,
      enabled: true,
      createdAt: '2026-10-28T12:00:00Z',
    };

    service.createStaffUser(payload).subscribe((res) => {
      expect(res).toEqual(createdResponse);
    });

    const req = httpMock.expectOne('/api/v1/admin/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);

    req.flush(createdResponse, { status: 201, statusText: 'Created' });

    expect(service.users().length).toBe(1);
    expect(service.users()[0].id).toBe('usr-3');
    expect(service.totalElements()).toBe(1);
  });

  it('should not insert newly created staff into signal if role filter does not match', () => {
    service.loadUsers(0, 20, 'DISPATCHER').subscribe();
    const req1 = httpMock.expectOne('/api/v1/admin/users?page=0&size=20&role=DISPATCHER');
    req1.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });

    const payload: CreateStaffUserRequest = {
      fullName: 'Kamal Courier',
      email: 'kamal@swiftdrop.lk',
      phoneNumber: '+94771234567',
      password: 'Password#123',
      role: 'COURIER',
    };

    const createdResponse: UserSummary = {
      id: 'usr-courier',
      email: payload.email,
      fullName: payload.fullName,
      phoneNumber: payload.phoneNumber,
      role: payload.role,
      enabled: true,
      createdAt: '2026-10-28T12:00:00Z',
    };

    service.createStaffUser(payload).subscribe();
    const req2 = httpMock.expectOne('/api/v1/admin/users');
    req2.flush(createdResponse, { status: 201, statusText: 'Created' });

    expect(service.users().length).toBe(0);
    expect(service.totalElements()).toBe(1);
  });

  it('should patch user status and update existing user in signal', () => {
    // Populate service signal first
    service.loadUsers().subscribe();
    const req1 = httpMock.expectOne('/api/v1/admin/users?page=0&size=20');
    req1.flush(mockListPage);

    expect(service.users()[0].enabled).toBe(true);

    service.updateUserStatus('usr-1', false).subscribe((res) => {
      expect(res.enabled).toBe(false);
    });

    const req2 = httpMock.expectOne('/api/v1/admin/users/usr-1');
    expect(req2.request.method).toBe('PATCH');
    expect(req2.request.body).toEqual({ enabled: false });

    req2.flush({ ...mockUsers[0], enabled: false });

    expect(service.users().find((u) => u.id === 'usr-1')?.enabled).toBe(false);
  });

  it('should clear state when clearState is called', () => {
    service.loadUsers().subscribe();
    const req = httpMock.expectOne('/api/v1/admin/users?page=0&size=20');
    req.flush(mockListPage);
    expect(service.users().length).toBe(2);

    service.clearState();

    expect(service.users()).toEqual([]);
    expect(service.totalElements()).toBe(0);
    expect(service.totalPages()).toBe(0);
    expect(service.currentPage()).toBe(0);
    expect(service.error()).toBeNull();
  });
});

