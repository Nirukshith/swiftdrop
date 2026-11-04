import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DispatchZonesComponent } from './dispatch-zones.component';

describe('DispatchZonesComponent', () => {
  let component: DispatchZonesComponent;
  let fixture: ComponentFixture<DispatchZonesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DispatchZonesComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchZonesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render initial delivery zones', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#zones')).toBeTruthy();
    expect(compiled.textContent).toContain('COLOMBO');
    expect(compiled.textContent).toContain('GAMPAHA');
    expect(component.zones().length).toBe(2);
  });

  it('should render navigation links to queue, workload, and pricing', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#tab-queue-link')).toBeTruthy();
    const workloadLink = compiled.querySelector('#tab-workload-link') as HTMLAnchorElement;
    expect(workloadLink).toBeTruthy();
    expect(workloadLink.getAttribute('href')).toContain('tab=workload');
    expect(compiled.querySelector('#tab-zones-btn')).toBeTruthy();
    expect(compiled.querySelector('#tab-pricing-link')).toBeTruthy();
  });

  it('should show validation feedback when form inputs are invalid and touched', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const nameControl = component.zoneForm.get('name');
    nameControl?.setValue('');
    nameControl?.markAsTouched();

    const districtsControl = component.zoneForm.get('districts');
    districtsControl?.setValue([]);
    districtsControl?.markAsTouched();

    const feeControl = component.zoneForm.get('baseFeeLkr');
    feeControl?.setValue(-10);
    feeControl?.markAsTouched();

    fixture.detectChanges();

    expect(component.zoneForm.invalid).toBe(true);
    expect(compiled.querySelector('#zone-name-error')?.textContent).toContain('Zone name is required');
    expect(compiled.querySelector('#zone-districts-error')?.textContent).toContain('At least one district must be selected');
    expect(compiled.querySelector('#zone-fee-error')?.textContent).toContain('Base fee must be 0 or greater');
  });

  it('should add a new zone upon valid submission and reset form', () => {
    const initialCount = component.zones().length;

    component.zoneForm.setValue({
      name: 'KANDY_CENTRAL',
      districts: ['Kandy'],
      served: true,
      baseFeeLkr: 650,
    });
    fixture.detectChanges();

    expect(component.zoneForm.valid).toBe(true);

    component.addZone();
    fixture.detectChanges();

    expect(component.zones().length).toBe(initialCount + 1);
    const added = component.zones().find((z) => z.name === 'KANDY_CENTRAL');
    expect(added).toBeDefined();
    expect(added?.baseFeeLkr).toBe(650);
    expect(added?.districts).toContain('Kandy');

    // Form should be reset
    expect(component.zoneForm.get('name')?.value).toBe('');
    expect(component.zoneForm.get('districts')?.value).toEqual([]);
  });
});

