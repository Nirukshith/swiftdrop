import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MerchantOnboardingService } from './merchant-onboarding.service';

// Exercise the real HTTP boundary rather than the former browser-only registration mock.
describe('MerchantOnboardingService', () => {
  let service: MerchantOnboardingService;
  let http: HttpTestingController;
  const registration = {
    businessName: 'North Star Stores', contactName: 'Asha Perera',
    email: 'asha@example.test', phoneNumber: '+94771234567',
  };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MerchantOnboardingService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('posts contract fields and uses the persisted pending response', () => {
    const response = { ...registration, id: 'server-id', status: 'PENDING',
      createdAt: '2026-11-10T00:00:00Z', message: 'Registration submitted for administrator review.' };
    service.register(registration).subscribe(result => expect(result).toEqual(response));
    const request = http.expectOne('/api/v1/merchants/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(registration);
    request.flush(response);
  });

  it('preserves server conflicts instead of creating a local registration', () => {
    const error = jest.fn();
    service.register(registration).subscribe({ next: () => { throw new Error('Unexpected success'); }, error });
    http.expectOne('/api/v1/merchants/register').flush({}, { status: 409, statusText: 'Conflict' });
    expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 409 }));
  });
});
