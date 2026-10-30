import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DispatchComponent } from './dispatch.component';
import { DispatchService } from './dispatch.service';

describe('DispatchComponent', () => {
  let component: DispatchComponent;
  let fixture: ComponentFixture<DispatchComponent>;
  let dispatchService: DispatchService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DispatchComponent, FormsModule],
      providers: [DispatchService],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchComponent);
    component = fixture.componentInstance;
    dispatchService = TestBed.inject(DispatchService);
    fixture.detectChanges();
  });

  it('should create and render navigation tabs', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#tab-queue-btn')).toBeTruthy();
    expect(compiled.querySelector('#tab-workload-btn')).toBeTruthy();
    expect(compiled.querySelector('#tab-users-btn')).toBeTruthy();
  });

  it('should switch tabs when clicking tab buttons', () => {
    component.activeTab.set('workload');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('workload');

    component.activeTab.set('users');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('users');
  });

  it('should filter unassigned parcels by search query', () => {
    component.searchQuery.set('Kiribathgoda');
    const filtered = component.filteredParcels();
    expect(filtered.length).toBe(1);
    expect(filtered[0].dropoffAddress).toContain('Kiribathgoda');
  });

  it('should open assign modal and confirm parcel assignment', () => {
    const parcel = dispatchService.unassignedParcels()[0];
    component.openAssignModal(parcel);
    fixture.detectChanges();

    expect(component.selectedParcelForAssign()).toEqual(parcel);
    expect(component.selectedCourierId()).toBeTruthy();

    const initialWorkload = dispatchService
      .couriers()
      .find((c) => c.courierId === component.selectedCourierId())?.activeCount;

    component.confirmAssignment();
    fixture.detectChanges();

    expect(component.selectedParcelForAssign()).toBeNull();
    expect(component.toastMessage()).toContain('assigned');

    const updatedWorkload = dispatchService
      .couriers()
      .find((c) => c.courierId === component.selectedCourierId())?.activeCount;
    expect(updatedWorkload).toBe((initialWorkload ?? 0) + 1);
  });

  it('should toggle courier availability', () => {
    const courier = dispatchService.couriers()[0];
    const initialStatus = courier.available;

    component.toggleCourierAvailability(courier);
    fixture.detectChanges();

    const updated = dispatchService
      .couriers()
      .find((c) => c.courierId === courier.courierId);
    expect(updated?.available).toBe(!initialStatus);
  });

  it('should validate form and submit create staff user', () => {
    component.newUserFullName = 'Test Staff';
    component.newUserEmail = 'test.staff@swiftdrop.lk';
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
      .find((u) => u.email === 'test.staff@swiftdrop.lk');
    expect(user).toBeDefined();
    expect(user?.role).toBe('COURIER');
  });

  it('should toggle user account status', () => {
    const user = dispatchService.users()[0];
    const initialStatus = user.enabled;

    component.toggleUserStatus(user);
    fixture.detectChanges();

    const updated = dispatchService.users().find((u) => u.id === user.id);
    expect(updated?.enabled).toBe(!initialStatus);
  });
});
