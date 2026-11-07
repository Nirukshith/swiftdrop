import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MerchantRegistrationRequest, MerchantRegistrationResponse } from './merchant-registration.model';
import { MerchantOnboardingService } from './merchant-onboarding.service';

describe('MerchantOnboardingService', () => {
  let service: MerchantOnboardingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MerchantOnboardingService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(MerchantOnboardingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the registration request and returns the backend pending response', () => {
    const request: MerchantRegistrationRequest = {
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: '+94771234567',
    };
    const response: MerchantRegistrationResponse = {
      id: 'a4924978-5007-4fad-9fd1-d691481ea43c',
      businessName: request.businessName,
      contactName: request.contactName,
      email: 'asha@example.test',
      phoneNumber: request.phoneNumber,
      status: 'PENDING',
      createdAt: '2026-11-07T10:00:00Z',
      message: 'Registration submitted for administrator review.',
    };

    service.register(request).subscribe((result) => expect(result).toEqual(response));

    const req = http.expectOne('/api/v1/merchants/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(response, { status: 201, statusText: 'Created' });
  });

  it('passes a duplicate-registration conflict from the backend to the caller', () => {
    let status: number | undefined;
    service.register({
      businessName: 'North Star Stores',
      contactName: 'Asha Perera',
      email: 'asha@example.test',
      phoneNumber: '+94771234567',
    }).subscribe({ error: (error: { status: number }) => status = error.status });

    const req = http.expectOne('/api/v1/merchants/register');
    expect(req.request.method).toBe('POST');
    req.flush({ code: 'CONFLICT' }, { status: 409, statusText: 'Conflict' });
    expect(status).toBe(409);
  });
});
