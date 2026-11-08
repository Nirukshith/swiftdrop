import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReportsService } from './reports.service';

describe('ReportsService', () => {
  let service: ReportsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ReportsService],
    });
    service = TestBed.inject(ReportsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads reports with inclusive from and to query parameters', () => {
    service.load('2026-11-01', '2026-11-07').subscribe();
    const request = httpMock.expectOne('/api/v1/admin/reports/daily?from=2026-11-01&to=2026-11-07');
    expect(request.request.method).toBe('GET');
    request.flush([{
      reportDate: '2026-11-01',
      totalDeliveries: 10,
      successfulDeliveries: 8,
      failedDeliveries: 2,
      successRate: 0.8,
    }]);
    expect(service.rows()[0].successRate).toBe(0.8);
    expect(service.loading()).toBe(false);
  });

  it('exposes an API error and clears loading state', () => {
    service.load('2026-11-01', '2026-11-07').subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/v1/admin/reports/daily?from=2026-11-01&to=2026-11-07');
    request.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    expect(service.error()).toBe('forbidden');
    expect(service.loading()).toBe(false);
  });
});
