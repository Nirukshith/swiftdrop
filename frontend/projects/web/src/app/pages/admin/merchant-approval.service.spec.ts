import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MerchantApprovalService } from './merchant-approval.service';
import { MerchantApprovalResponse, MerchantPage, MerchantSummary } from './merchant-approval.model';

describe('MerchantApprovalService', () => {
  let service: MerchantApprovalService;
  let httpMock: HttpTestingController;
  const merchant: MerchantSummary = {
    id: 'm-1',
    businessName: 'North Star Stores',
    contactName: 'Asha Perera',
    email: 'asha@example.test',
    phoneNumber: '+94771234567',
    status: 'PENDING',
    rejectionReason: null,
    createdAt: '2026-11-01T10:00:00Z',
  };
  const page: MerchantPage = { content: [merchant], totalElements: 1, totalPages: 1, page: 0, size: 20 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MerchantApprovalService],
    });
    service = TestBed.inject(MerchantApprovalService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads pending merchants using contract pagination', () => {
    service.loadMerchants(1, 10).subscribe();
    const request = httpMock.expectOne('/api/v1/admin/merchants?status=PENDING&page=1&size=10');
    expect(request.request.method).toBe('GET');
    request.flush({ ...page, page: 1, size: 10 });
    expect(service.merchants()).toEqual([merchant]);
  });

  it('forwards the one-time approval response for the admin handoff without caching it', () => {
    const response: MerchantApprovalResponse = {
      merchant: { ...merchant, status: 'APPROVED' },
      clientId: 'client-1',
      clientSecret: 'must-not-be-retained',
    };

    service.approve(merchant.id).subscribe((result) => {
      expect(result).toEqual(response);
      expect(service.merchants()).toEqual([]);
    });
    const request = httpMock.expectOne('/api/v1/admin/merchants/m-1/approve');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(response);
  });

  it('posts the exact rejection payload', () => {
    service.reject(merchant.id, { reason: 'Details could not be verified' }).subscribe();
    const request = httpMock.expectOne('/api/v1/admin/merchants/m-1/reject');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Details could not be verified' });
    request.flush({ ...merchant, status: 'REJECTED', rejectionReason: 'Details could not be verified' });
  });

  it('maps conflict errors to an actionable message', () => {
    service.loadMerchants().subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/v1/admin/merchants?status=PENDING&page=0&size=20');
    request.flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });
    expect(service.error()).toContain('no longer pending');
  });
});
