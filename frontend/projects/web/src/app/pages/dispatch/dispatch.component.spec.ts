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
  });

  it('should switch tabs when clicking tab buttons', () => {
    component.activeTab.set('workload');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('workload');

    component.activeTab.set('queue');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('queue');
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
});
