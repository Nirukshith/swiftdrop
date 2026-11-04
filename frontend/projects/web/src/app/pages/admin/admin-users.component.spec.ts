import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { AdminUsersComponent } from './admin-users.component';
import { DispatchService } from '../dispatch/dispatch.service';

describe('AdminUsersComponent', () => {
  let component: AdminUsersComponent;
  let fixture: ComponentFixture<AdminUsersComponent>;
  let dispatchService: DispatchService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsersComponent, FormsModule],
      providers: [DispatchService, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsersComponent);
    component = fixture.componentInstance;
    dispatchService = TestBed.inject(DispatchService);
    fixture.detectChanges();
  });

  it('should create and render staff user management table', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#open-create-user-modal-btn')).toBeTruthy();
    expect(compiled.querySelector('#user-role-filter')).toBeTruthy();
    expect(compiled.querySelector('#user-status-filter')).toBeTruthy();
    expect(component.filteredUsers().length).toBeGreaterThan(0);
  });

  it('should filter staff users by role', () => {
    component.userRoleFilter.set('COURIER');
    fixture.detectChanges();
    const users = component.filteredUsers();
    expect(users.every((u) => u.role === 'COURIER')).toBe(true);
  });

  it('should filter staff users by status', () => {
    component.userStatusFilter.set('true');
    fixture.detectChanges();
    const users = component.filteredUsers();
    expect(users.every((u) => u.enabled)).toBe(true);
  });

  it('should toggle user account status', () => {
    const user = dispatchService.users()[0];
    const initialStatus = user.enabled;

    component.toggleUserStatus(user);
    fixture.detectChanges();

    const updated = dispatchService.users().find((u) => u.id === user.id);
    expect(updated?.enabled).toBe(!initialStatus);
    expect(component.toastMessage()).toContain(updated?.enabled ? 'Active' : 'Disabled');
  });

  it('should validate form and submit create staff user', () => {
    component.newUserFullName = 'Saman Kumara';
    component.newUserEmail = 'courier.newuser@swiftdrop.lk';
    component.newUserPhone = '+94771234567';
    component.newUserPassword = 'Password#2026';
    component.newUserRole = 'COURIER';

    expect(component.isFormValid()).toBe(true);

    component.submitCreateStaffUser();
    fixture.detectChanges();

    expect(component.toastMessage()).toContain('created successfully');
    expect(component.isCreateUserModalOpen()).toBe(false);

    const user = dispatchService
      .users()
      .find((u) => u.email === 'courier.newuser@swiftdrop.lk');
    expect(user).toBeDefined();
    expect(user?.role).toBe('COURIER');
  });
});
