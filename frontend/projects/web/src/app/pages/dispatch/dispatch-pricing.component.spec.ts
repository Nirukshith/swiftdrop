import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DispatchPricingComponent } from './dispatch-pricing.component';

describe('DispatchPricingComponent', () => {
  let component: DispatchPricingComponent;
  let fixture: ComponentFixture<DispatchPricingComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DispatchPricingComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(DispatchPricingComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne('/api/v1/admin/pricing-rules').flush([
      { id: '1', kind: 'WEIGHT_BAND', key: 'UP_TO_5KG', minimumWeightKg: 0, maximumWeightKg: 5, chargeLkr: 200, active: true, createdAt: '', updatedAt: '' },
      { id: '2', kind: 'SIZE', key: 'MEDIUM', chargeLkr: 150, active: true, createdAt: '', updatedAt: '' },
    ]);
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

    component.savePricingRule();
    const createRequest = httpMock.expectOne('/api/v1/admin/pricing-rules');
    expect(createRequest.request.method).toBe('POST');
    createRequest.flush({ id: '3', kind: 'SIZE', key: 'EXTRA_LARGE', chargeLkr: 350, active: true, createdAt: '', updatedAt: '' });
    httpMock.expectOne('/api/v1/admin/pricing-rules').flush([
      { id: '3', kind: 'SIZE', key: 'EXTRA_LARGE', chargeLkr: 350, active: true, createdAt: '', updatedAt: '' },
    ]);
    component.pricingRules.set([{ id: '3', kind: 'SIZE', key: 'EXTRA_LARGE', chargeLkr: 350, active: true, createdAt: '', updatedAt: '' }]);
    fixture.detectChanges();

    expect(component.pricingRules().length).toBe(1);
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

  it('should reject a zero maximum weight for a weight band', () => {
    setWeightBand(0, 0);

    expect(component.pricingForm.invalid).toBe(true);
  });

  it('should reject a negative maximum weight for a weight band', () => {
    setWeightBand(0, -1);

    expect(component.pricingForm.invalid).toBe(true);
  });

  it('should reject a negative minimum weight for a weight band', () => {
    setWeightBand(-1, 5);

    expect(component.pricingForm.invalid).toBe(true);
  });

  it('should reject equal minimum and maximum weights', () => {
    setWeightBand(5, 5);

    expect(component.pricingForm.invalid).toBe(true);
  });

  it('should reject a minimum weight greater than the maximum', () => {
    setWeightBand(5, 1);

    expect(component.pricingForm.invalid).toBe(true);
  });

  it('should accept a zero minimum with a positive maximum', () => {
    setWeightBand(0, 5);

    expect(component.pricingForm.valid).toBe(true);
  });

  it('should accept a positive minimum below the maximum', () => {
    setWeightBand(1, 5);

    expect(component.pricingForm.valid).toBe(true);
  });

  it('should not apply weight-bound validation to size rules', () => {
    component.pricingForm.setValue({
      kind: 'SIZE',
      key: 'MEDIUM',
      minimumWeightKg: 0,
      maximumWeightKg: 0,
      chargeLkr: 150,
      active: true,
    });

    expect(component.pricingForm.valid).toBe(true);
  });

  it('should not submit an invalid weight band', () => {
    setWeightBand(5, 1);

    component.savePricingRule();

    httpMock.expectNone('/api/v1/admin/pricing-rules');
  });

  it('renders the Try a Quote calculator card and form controls', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#quote-calculator')).toBeTruthy();
    expect(compiled.querySelector('#quote-pickup-select')).toBeTruthy();
    expect(compiled.querySelector('#quote-dropoff-select')).toBeTruthy();
    expect(compiled.querySelector('#quote-weight-input')).toBeTruthy();
    expect(compiled.querySelector('#quote-size-select')).toBeTruthy();
    expect(compiled.querySelector('#quote-calculate-btn')).toBeTruthy();
  });

  it('validates quote input parameters and rejects zero weight', () => {
    component.quoteForm.patchValue({ weightKg: 0 });
    component.quoteForm.get('weightKg')?.markAsTouched();
    fixture.detectChanges();

    expect(component.quoteForm.invalid).toBe(true);

    component.calculateQuote();
    httpMock.expectNone((req) => req.url.includes('/api/v1/parcels/quote'));
  });

  it('calculates quote from backend and displays itemized breakdown', () => {
    component.quoteForm.setValue({
      pickupDistrict: 'Colombo',
      dropoffDistrict: 'Kandy',
      weightKg: 2.5,
      size: 'MEDIUM',
    });
    fixture.detectChanges();

    component.calculateQuote();

    const quoteReq = httpMock.expectOne((req) =>
      req.url === '/api/v1/parcels/quote' &&
      req.params.get('pickupDistrict') === 'Colombo' &&
      req.params.get('dropoffDistrict') === 'Kandy' &&
      req.params.get('weightKg') === '2.5' &&
      req.params.get('size') === 'MEDIUM'
    );
    expect(quoteReq.request.method).toBe('GET');

    quoteReq.flush({
      currency: 'LKR',
      totalFee: 850,
      breakdown: {
        dropoffZoneBaseFee: 400,
        weightBandCharge: 250,
        sizeCharge: 200,
      },
      pickupZone: 'Western',
      dropoffZone: 'Central',
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#quote-result')).toBeTruthy();
    expect(compiled.querySelector('#quote-total-fee')?.textContent).toContain('850');
    expect(compiled.querySelector('#quote-base-fee')?.textContent).toContain('400');
    expect(compiled.querySelector('#quote-weight-fee')?.textContent).toContain('250');
    expect(compiled.querySelector('#quote-size-fee')?.textContent).toContain('200');
    expect(compiled.textContent).toContain('Western to Central');
  });

  it('displays quote error when API call fails', () => {
    component.quoteForm.setValue({
      pickupDistrict: 'Mullaitivu',
      dropoffDistrict: 'Colombo',
      weightKg: 1.0,
      size: 'SMALL',
    });
    fixture.detectChanges();

    component.calculateQuote();

    const quoteReq = httpMock.expectOne((req) => req.url === '/api/v1/parcels/quote');
    quoteReq.flush({ message: 'Pickup district is not served' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#quote-error')?.textContent).toContain('not an active served area');
    expect(component.quoteResult()).toBeNull();
  });

  it('clears quote result when clear button is clicked', () => {
    component.quoteResult.set({
      currency: 'LKR',
      totalFee: 850,
      breakdown: { dropoffZoneBaseFee: 400, weightBandCharge: 250, sizeCharge: 200 },
    });
    fixture.detectChanges();

    component.clearQuote();
    fixture.detectChanges();

    expect(component.quoteResult()).toBeNull();
    expect(component.quoteError()).toBeNull();
  });

  it('displays success banner after creating a pricing rule', () => {
    component.pricingForm.setValue({
      kind: 'SIZE',
      key: 'SMALL_RULE',
      minimumWeightKg: 0,
      maximumWeightKg: 0,
      chargeLkr: 100,
      active: true,
    });
    component.savePricingRule();

    const postReq = httpMock.expectOne('/api/v1/admin/pricing-rules');
    postReq.flush({ id: '99', kind: 'SIZE', key: 'SMALL_RULE', chargeLkr: 100, active: true });
    httpMock.expectOne('/api/v1/admin/pricing-rules').flush([]);
    fixture.detectChanges();

    expect(component.successMessage()).toContain('SMALL_RULE');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#pricing-success-banner')?.textContent).toContain('SMALL_RULE');
  });

  it('displays success banner after deleting a pricing rule', () => {
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    const ruleToDelete: any = { id: '1', key: 'UP_TO_5KG' };
    component.removePricingRule(ruleToDelete);

    const delReq = httpMock.expectOne('/api/v1/admin/pricing-rules/1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null);
    httpMock.expectOne('/api/v1/admin/pricing-rules').flush([]);
    fixture.detectChanges();

    expect(component.successMessage()).toContain('deleted successfully');
  });

  it('accepts valid positive decimal weights and rejects negative or zero weights', () => {
    component.quoteForm.patchValue({ weightKg: 0.05 });
    expect(component.quoteForm.get('weightKg')?.valid).toBe(true);

    component.quoteForm.patchValue({ weightKg: 0.001 });
    expect(component.quoteForm.get('weightKg')?.valid).toBe(true);

    component.quoteForm.patchValue({ weightKg: 0 });
    expect(component.quoteForm.get('weightKg')?.valid).toBe(false);

    component.quoteForm.patchValue({ weightKg: -1.5 });
    expect(component.quoteForm.get('weightKg')?.valid).toBe(false);
  });

  it('prevents duplicate quote calculation calls while calculation is in progress', () => {
    component.isCalculatingQuote.set(true);
    component.calculateQuote();
    httpMock.expectNone((req) => req.url.includes('/api/v1/parcels/quote'));
  });

  it('prevents duplicate pricing rule submissions while save is in progress', () => {
    component.isSaving.set(true);
    component.pricingForm.setValue({
      kind: 'SIZE',
      key: 'TEST_RULE',
      minimumWeightKg: 0,
      maximumWeightKg: 0,
      chargeLkr: 100,
      active: true,
    });
    component.savePricingRule();
    httpMock.expectNone('/api/v1/admin/pricing-rules');
  });

  it('safely handles missing or unexpected quote breakdown data and sets error message', () => {
    component.calculateQuote();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/parcels/quote');
    req.flush({ totalFee: 500 }); // missing breakdown
    fixture.detectChanges();

    expect(component.quoteResult()).toBeNull();
    expect(component.quoteError()).toContain('incomplete quote response');
  });

  function setWeightBand(minimumWeightKg: number, maximumWeightKg: number): void {
    component.pricingForm.setValue({
      kind: 'WEIGHT_BAND',
      key: 'UP_TO_5KG',
      minimumWeightKg,
      maximumWeightKg,
      chargeLkr: 200,
      active: true,
    });
  }
});
