import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DispatchPricingComponent } from './dispatch-pricing.component';

describe('DispatchPricingComponent', () => {
  let component: DispatchPricingComponent;
  let fixture: ComponentFixture<DispatchPricingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DispatchPricingComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchPricingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render initial pricing rules', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#pricing')).toBeTruthy();
    expect(compiled.textContent).toContain('UP_TO_5KG');
    expect(compiled.textContent).toContain('MEDIUM');
    expect(component.pricingRules().length).toBe(2);
  });

  it('should render navigation links to queue, workload, and zones', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#tab-queue-link')).toBeTruthy();
    const workloadLink = compiled.querySelector('#tab-workload-link') as HTMLAnchorElement;
    expect(workloadLink).toBeTruthy();
    expect(workloadLink.getAttribute('href')).toContain('tab=workload');
    expect(compiled.querySelector('#tab-zones-link')).toBeTruthy();
    expect(compiled.querySelector('#tab-pricing-btn')).toBeTruthy();
  });

  it('should show validation feedback when form inputs are invalid and touched', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const keyControl = component.pricingForm.get('key');
    keyControl?.setValue('');
    keyControl?.markAsTouched();

    const chargeControl = component.pricingForm.get('chargeLkr');
    chargeControl?.setValue(-50);
    chargeControl?.markAsTouched();

    fixture.detectChanges();

    expect(component.pricingForm.invalid).toBe(true);
    expect(compiled.querySelector('#pricing-key-error')?.textContent).toContain('Key identifier is required');
    expect(compiled.querySelector('#pricing-charge-error')?.textContent).toContain('Charge must be 0 or greater');
  });

  it('should add a new pricing rule upon valid submission and reset form', () => {
    const initialCount = component.pricingRules().length;

    component.pricingForm.setValue({
      kind: 'SIZE',
      key: 'EXTRA_LARGE',
      minimumWeightKg: 0,
      maximumWeightKg: 0,
      chargeLkr: 350,
      active: true,
    });
    fixture.detectChanges();

    expect(component.pricingForm.valid).toBe(true);

    component.addPricingRule();
    fixture.detectChanges();

    expect(component.pricingRules().length).toBe(initialCount + 1);
    const added = component.pricingRules().find((r) => r.key === 'EXTRA_LARGE');
    expect(added).toBeDefined();
    expect(added?.chargeLkr).toBe(350);

    // Form should be reset
    expect(component.pricingForm.get('key')?.value).toBe('');
    expect(component.pricingForm.get('chargeLkr')?.value).toBe(0);
  });

  it('should show weight band min/max inputs when rule type is WEIGHT_BAND', () => {
    component.pricingForm.get('kind')?.setValue('WEIGHT_BAND');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#pricing-min-kg-input')).toBeTruthy();
    expect(compiled.querySelector('#pricing-max-kg-input')).toBeTruthy();
  });
});

