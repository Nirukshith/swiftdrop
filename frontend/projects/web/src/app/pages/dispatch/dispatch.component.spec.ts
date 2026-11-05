import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DispatchComponent } from './dispatch.component';
import {
  AssignmentResult,
  CourierWorkloadItem,
  DispatchParcelSummary,
  UnassignedParcelsPage,
} from './dispatch.model';
import { DispatchService } from './dispatch.service';

describe('DispatchComponent (Real API Integration)', () => {
  let component: DispatchComponent;
  let fixture: ComponentFixture<DispatchComponent>;
  let dispatchService: jasmine.SpyObj<DispatchService> | any;

  const mockParcels: DispatchParcelSummary[] = [
    {
      parcelId: 'pcl-101',
      trackingNumber: 'SD-20261028-001',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-SOUTH',
      pickupAddress: '45 Galle Road, Colombo 03',
      dropoffAddress: '12 Havelock Road, Colombo 05',
      weightKg: 2.5,
      attemptCount: 0,
      failureReason: null,
      createdAt: '2026-10-25T10:00:00Z',
    },
    {
      parcelId: 'pcl-102',
      trackingNumber: 'SD-20261028-002',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-NORTH',
      pickupAddress: '100 Union Place, Colombo 02',
      dropoffAddress: '88 Kandy Road, Kiribathgoda',
      weightKg: 1.2,
      attemptCount: 0,
      failureReason: null,
      createdAt: '2026-10-25T11:00:00Z',
    },
  ];

  const mockParcelsPage: UnassignedParcelsPage = {
    content: mockParcels,
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  };

  const mockCouriers: CourierWorkloadItem[] = [
    {
      courierId: 'f8e7d6c5-b4a3-2109-8765-43210fedcba9',
      name: 'Saman Perera',
      phoneNumber: '+94771234567',
      zone: 'ZONE-COLOMBO-NORTH',
      activeCount: 2,
      available: true,
      vehicleType: 'MOTORCYCLE',
    },
    {
      courierId: 'a2b3c4d5-e6f7-4819-9012-34567890abcd',
      name: 'Kamal Silva',
      phoneNumber: '+94772345678',
      zone: 'ZONE-COLOMBO-SOUTH',
      activeCount: 1,
      available: false,
      vehicleType: 'VAN',
    },
  ];

  const mockFailedParcels: DispatchParcelSummary[] = [
    {
      parcelId: 'fail-101',
      trackingNumber: 'SD-FAIL-001',
      status: 'FAILED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-SOUTH',
      pickupAddress: '10 Main St, Colombo 03',
      dropoffAddress: '20 Hill St, Colombo 05',
      attemptCount: 1,
      failureReason: 'Customer not reachable',
      createdAt: '2026-10-25T10:00:00Z',
    },
  ];

  let dispatchServiceSpy: {
    loadUnassignedParcels: jest.Mock;
    loadCourierWorkload: jest.Mock;
    loadFailedParcels: jest.Mock;
    assignParcel: jest.Mock;
    bulkAssignParcels: jest.Mock;
    requeueParcel: jest.Mock;
    updateCourierAvailability: jest.Mock;
    unassignedParcels: any;
    couriers: any;
    availableCouriers: any;
    unassignedCount: any;
    availableCouriersCount: any;
    totalWorkloadActive: any;
    failedParcels: any;
    totalFailedCount: any;
    isLoadingParcels: any;
    isLoadingCouriers: any;
    isLoadingFailed: any;
    parcelsError: any;
    couriersError: any;
    failedError: any;
    users: any;
  };

  beforeEach(async () => {
    // Create signal functions
    const parcelsSignal = jest.fn(() => mockParcels);
    const couriersSignal = jest.fn(() => mockCouriers);
    const availableCouriersSignal = jest.fn(() => [mockCouriers[0]]);
    const unassignedCountSignal = jest.fn(() => mockParcels.length);
    const availableCouriersCountSignal = jest.fn(() => 1);
    const totalWorkloadActiveSignal = jest.fn(() => 3);
    const failedParcelsSignal = jest.fn(() => mockFailedParcels);
    const totalFailedCountSignal = jest.fn(() => mockFailedParcels.length);
    const isLoadingParcelsSignal = jest.fn(() => false);
    const isLoadingCouriersSignal = jest.fn(() => false);
    const isLoadingFailedSignal = jest.fn(() => false);
    const parcelsErrorSignal = jest.fn(() => null);
    const couriersErrorSignal = jest.fn(() => null);
    const failedErrorSignal = jest.fn(() => null);

    dispatchServiceSpy = {
      loadUnassignedParcels: jest.fn().mockReturnValue(of(mockParcelsPage)),
      loadCourierWorkload: jest.fn().mockReturnValue(of(mockCouriers)),
      loadFailedParcels: jest.fn().mockReturnValue(
        of({
          content: mockFailedParcels,
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 20,
        })
      ),
      assignParcel: jest.fn().mockReturnValue(
        of({
          parcelId: 'pcl-101',
          courierId: 'f8e7d6c5-b4a3-2109-8765-43210fedcba9',
          assignedAt: '2026-10-25T12:00:00Z',
          status: 'ASSIGNED',
        } as AssignmentResult)
      ),
      bulkAssignParcels: jest.fn().mockReturnValue(
        of({
          totalRequested: 2,
          successfulCount: 2,
          failedCount: 0,
          results: [
            { parcelId: 'pcl-101', courierId: 'c1', success: true },
            { parcelId: 'pcl-102', courierId: 'c1', success: true },
          ],
        })
      ),
      requeueParcel: jest.fn().mockReturnValue(
        of({
          parcelId: 'fail-101',
          status: 'CREATED',
          attemptCount: 2,
          requeuedAt: '2026-10-25T15:00:00Z',
        })
      ),
      updateCourierAvailability: jest.fn().mockReturnValue(
        of({
          ...mockCouriers[0],
          available: false,
        } as CourierWorkloadItem)
      ),
      unassignedParcels: parcelsSignal,
      couriers: couriersSignal,
      availableCouriers: availableCouriersSignal,
      unassignedCount: unassignedCountSignal,
      availableCouriersCount: availableCouriersCountSignal,
      totalWorkloadActive: totalWorkloadActiveSignal,
      failedParcels: failedParcelsSignal,
      totalFailedCount: totalFailedCountSignal,
      isLoadingParcels: isLoadingParcelsSignal,
      isLoadingCouriers: isLoadingCouriersSignal,
      isLoadingFailed: isLoadingFailedSignal,
      parcelsError: parcelsErrorSignal,
      couriersError: couriersErrorSignal,
      failedError: failedErrorSignal,
      users: jest.fn(() => []),
    };

    await TestBed.configureTestingModule({
      imports: [DispatchComponent, FormsModule],
      providers: [
        { provide: DispatchService, useValue: dispatchServiceSpy },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render navigation tabs and links', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#tab-queue-btn')).toBeTruthy();
    expect(compiled.querySelector('#tab-workload-btn')).toBeTruthy();
    expect(compiled.querySelector('#tab-failed-btn')).toBeTruthy();
    expect(compiled.querySelector('#tab-zones-link')).toBeTruthy();
    expect(compiled.querySelector('#tab-pricing-link')).toBeTruthy();
  });

  it('should call loadUnassignedParcels, loadCourierWorkload, and loadFailedParcels on initialization', () => {
    expect(dispatchServiceSpy.loadUnassignedParcels).toHaveBeenCalled();
    expect(dispatchServiceSpy.loadCourierWorkload).toHaveBeenCalled();
    expect(dispatchServiceSpy.loadFailedParcels).toHaveBeenCalled();
  });

  it('should switch tabs when clicking tab buttons', () => {
    component.activeTab.set('workload');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('workload');

    component.activeTab.set('queue');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('queue');
  });

  it('should select workload tab when route has query param tab=workload', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/?tab=workload');

    const queryFixture = TestBed.createComponent(DispatchComponent);
    const queryComponent = queryFixture.componentInstance;
    queryFixture.detectChanges();

    expect(queryComponent.activeTab()).toBe('workload');
  });

  it('should default activeTab to queue when query param tab is missing or invalid', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/?tab=other-tab');

    const queryFixture = TestBed.createComponent(DispatchComponent);
    const queryComponent = queryFixture.componentInstance;
    queryFixture.detectChanges();

    expect(queryComponent.activeTab()).toBe('queue');
  });

  it('should filter unassigned parcels by search query', () => {
    component.searchQuery.set('Kiribathgoda');
    const filtered = component.filteredParcels();
    expect(filtered.length).toBe(1);
    expect(filtered[0].dropoffAddress).toContain('Kiribathgoda');
  });

  it('should trigger reload when zone filter changes in queue', () => {
    dispatchServiceSpy.loadUnassignedParcels.mockClear();
    component.onQueueZoneChange('ZONE-COLOMBO-NORTH');
    expect(component.selectedZone()).toBe('ZONE-COLOMBO-NORTH');
    expect(dispatchServiceSpy.loadUnassignedParcels).toHaveBeenCalledWith('ZONE-COLOMBO-NORTH');
  });

  it('should open assign modal and confirm parcel assignment via real API', () => {
    const parcel = mockParcels[0];
    component.openAssignModal(parcel);
    fixture.detectChanges();

    expect(component.selectedParcelForAssign()).toEqual(parcel);
    expect(component.selectedCourierId()).toBe(mockCouriers[0].courierId);

    component.confirmAssignment();
    fixture.detectChanges();

    expect(dispatchServiceSpy.assignParcel).toHaveBeenCalledWith(
      parcel.parcelId,
      mockCouriers[0].courierId
    );
    expect(component.selectedParcelForAssign()).toBeNull();
    expect(component.toastMessage()).toContain('assigned to Saman Perera');
    expect(component.toastType()).toBe('success');
  });

  it('should prevent duplicate assignment submissions when already submitting', () => {
    const parcel = mockParcels[0];
    component.openAssignModal(parcel);
    component.isSubmittingAssignment.set(true);

    component.confirmAssignment();

    expect(dispatchServiceSpy.assignParcel).not.toHaveBeenCalled();
  });

  it('should handle 409 Conflict error on assign and trigger queue refresh', () => {
    dispatchServiceSpy.assignParcel.mockReturnValue(
      throwError(() => ({
        status: 409,
        error: { message: 'Parcel has already been assigned to another courier.' },
      }))
    );
    dispatchServiceSpy.loadUnassignedParcels.mockClear();

    const parcel = mockParcels[0];
    component.openAssignModal(parcel);
    component.confirmAssignment();
    fixture.detectChanges();

    expect(component.isSubmittingAssignment()).toBe(false);
    expect(component.toastType()).toBe('error');
    expect(component.toastMessage()).toContain('already been assigned');
    expect(dispatchServiceSpy.loadUnassignedParcels).toHaveBeenCalled();
  });

  it('should toggle courier availability via API', () => {
    const courier = mockCouriers[0];
    component.toggleCourierAvailability(courier);
    fixture.detectChanges();

    expect(dispatchServiceSpy.updateCourierAvailability).toHaveBeenCalledWith(
      courier.courierId,
      false
    );
    expect(component.toastMessage()).toContain('is now Offline');
  });

  it('should display error banner with retry button when queue loading fails', () => {
    dispatchServiceSpy.parcelsError = jest.fn(() => 'Network Error');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const retryBtn = compiled.querySelector('#retry-queue-btn');
    expect(retryBtn).toBeTruthy();

    dispatchServiceSpy.loadUnassignedParcels.mockClear();
    (retryBtn as HTMLButtonElement).click();

    expect(dispatchServiceSpy.loadUnassignedParcels).toHaveBeenCalled();
  });

  it('should display loading spinner when loading parcels', () => {
    dispatchServiceSpy.isLoadingParcels = jest.fn(() => true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Loading unassigned dispatch queue...');
  });

  describe('Feature A & B: Failed Parcel List & Re-queue', () => {
    it('should switch to failed tab when clicking failed tab button', () => {
      component.activeTab.set('failed');
      fixture.detectChanges();
      expect(component.activeTab()).toBe('failed');

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('#failed-search-input')).toBeTruthy();
      expect(compiled.querySelector('#failed-zone-filter')).toBeTruthy();
    });

    it('should select failed tab when route has query param tab=failed', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/?tab=failed');

      const queryFixture = TestBed.createComponent(DispatchComponent);
      const queryComponent = queryFixture.componentInstance;
      queryFixture.detectChanges();

      expect(queryComponent.activeTab()).toBe('failed');
    });

    it('should filter failed parcels by search query', () => {
      component.failedSearchQuery.set('reachable');
      const filtered = component.filteredFailedParcels();
      expect(filtered.length).toBe(1);
      expect(filtered[0].trackingNumber).toBe('SD-FAIL-001');

      component.failedSearchQuery.set('nonexistent-query');
      expect(component.filteredFailedParcels().length).toBe(0);
    });

    it('should trigger reload when zone filter changes in failed deliveries tab', () => {
      dispatchServiceSpy.loadFailedParcels.mockClear();
      component.onFailedZoneChange('ZONE-COLOMBO-NORTH');
      expect(component.failedSelectedZone()).toBe('ZONE-COLOMBO-NORTH');
      expect(dispatchServiceSpy.loadFailedParcels).toHaveBeenCalledWith('ZONE-COLOMBO-NORTH');
    });

    it('should open re-queue modal, accept optional reason, and confirm re-queue via API', () => {
      const failedParcel = mockFailedParcels[0];
      component.openRequeueModal(failedParcel);
      fixture.detectChanges();

      expect(component.selectedParcelForRequeue()).toEqual(failedParcel);
      expect(component.requeueReason()).toBe('');

      component.requeueReason.set('Customer requested redelivery tomorrow');
      component.confirmRequeue();
      fixture.detectChanges();

      expect(dispatchServiceSpy.requeueParcel).toHaveBeenCalledWith(
        failedParcel.parcelId,
        'Customer requested redelivery tomorrow'
      );
      expect(component.selectedParcelForRequeue()).toBeNull();
      expect(component.toastMessage()).toContain('successfully re-queued to CREATED (Attempt #2)!');
      expect(component.toastType()).toBe('success');
    });

    it('should handle re-queue API errors gracefully', () => {
      dispatchServiceSpy.requeueParcel.mockReturnValue(
        throwError(() => ({
          status: 400,
          error: { message: 'Parcel is not in FAILED status' },
        }))
      );

      const failedParcel = mockFailedParcels[0];
      component.openRequeueModal(failedParcel);
      component.confirmRequeue();
      fixture.detectChanges();

      expect(component.isSubmittingRequeue()).toBe(false);
      expect(component.toastType()).toBe('error');
      expect(component.toastMessage()).toContain('Parcel is not in FAILED status');
    });
  });

  describe('Feature C: Bulk Assignment', () => {
    it('should toggle individual parcel selection', () => {
      const p1 = mockParcels[0].parcelId;
      expect(component.isParcelSelected(p1)).toBe(false);

      component.toggleSelectParcel(p1);
      expect(component.isParcelSelected(p1)).toBe(true);

      component.toggleSelectParcel(p1);
      expect(component.isParcelSelected(p1)).toBe(false);
    });

    it('should toggle select all and clear selection', () => {
      expect(component.isAllSelected()).toBe(false);

      component.toggleSelectAll();
      expect(component.selectedParcelIds().size).toBe(mockParcels.length);
      expect(component.isAllSelected()).toBe(true);

      component.toggleSelectAll();
      expect(component.selectedParcelIds().size).toBe(0);
      expect(component.isAllSelected()).toBe(false);
    });

    it('should submit bulk assignment and display outcome modal', () => {
      const p1 = mockParcels[0].parcelId;
      const p2 = mockParcels[1].parcelId;
      component.toggleSelectParcel(p1);
      component.toggleSelectParcel(p2);
      component.bulkCourierId.set('c1');

      component.submitBulkAssign();
      fixture.detectChanges();

      expect(dispatchServiceSpy.bulkAssignParcels).toHaveBeenCalledWith([
        { parcelId: p1, courierId: 'c1' },
        { parcelId: p2, courierId: 'c1' },
      ]);
      expect(component.bulkResult()).toBeTruthy();
      expect(component.bulkResult()?.successfulCount).toBe(2);
      expect(component.selectedParcelIds().size).toBe(0);
      expect(component.toastType()).toBe('success');
    });

    it('should handle bulk assignment API failure', () => {
      dispatchServiceSpy.bulkAssignParcels.mockReturnValue(
        throwError(() => ({ error: { message: 'Bulk assignment network timeout' } }))
      );

      component.toggleSelectParcel(mockParcels[0].parcelId);
      component.bulkCourierId.set('c1');
      component.submitBulkAssign();
      fixture.detectChanges();

      expect(component.isSubmittingBulk()).toBe(false);
      expect(component.toastType()).toBe('error');
      expect(component.toastMessage()).toContain('Bulk assignment network timeout');
    });
  });
});
