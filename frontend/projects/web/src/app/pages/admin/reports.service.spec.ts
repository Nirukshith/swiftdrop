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

  it('loads reports filtered by zone when zone parameter is provided', () => {
    service.load('2026-11-01', '2026-11-07', 'COLOMBO_ZONE').subscribe();
    const request = httpMock.expectOne('/api/v1/admin/reports/daily?from=2026-11-01&to=2026-11-07&zone=COLOMBO_ZONE');
    expect(request.request.method).toBe('GET');
    request.flush([{
      reportDate: '2026-11-01',
      totalDeliveries: 6,
      successfulDeliveries: 6,
      failedDeliveries: 0,
      successRate: 1.0,
    }]);
    expect(service.rows()[0].totalDeliveries).toBe(6);
  });

  it('exposes an API error and clears loading state', () => {
    service.load('2026-11-01', '2026-11-07').subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/v1/admin/reports/daily?from=2026-11-01&to=2026-11-07');
    request.flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    expect(service.error()).toBe('forbidden');
    expect(service.loading()).toBe(false);
  });

  it('loads zone reports for a date range', () => {
    service.loadZoneReports('2026-11-01', '2026-11-07').subscribe();
    const request = httpMock.expectOne('/api/v1/admin/reports/zones?from=2026-11-01&to=2026-11-07');
    expect(request.request.method).toBe('GET');
    request.flush([{
      zoneName: 'COLOMBO_ZONE',
      districts: ['Colombo'],
      served: true,
      totalDeliveries: 65,
      successfulDeliveries: 61,
      failedDeliveries: 4,
      successRate: 0.938,
    }]);
    expect(service.zoneSummaries().length).toBe(1);
    expect(service.zoneSummaries()[0].zoneName).toBe('COLOMBO_ZONE');
  });

  it('loads available zones', () => {
    service.loadAvailableZones().subscribe();
    const request = httpMock.expectOne('/api/v1/admin/zones');
    expect(request.request.method).toBe('GET');
    request.flush([{
      id: 'z1',
      name: 'COLOMBO_ZONE',
      districts: ['Colombo'],
      served: true,
      baseFeeLkr: 500,
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    }]);
    expect(service.availableZones().length).toBe(1);
    expect(service.availableZones()[0].name).toBe('COLOMBO_ZONE');
    expect(service.zonesLoading()).toBe(false);
  });

  it('requests CSV export with the selected date parameters', () => {
    service.exportCsv('2026-11-01', '2026-11-07').subscribe();
    const request = httpMock.expectOne('/api/v1/admin/reports/daily.csv?from=2026-11-01&to=2026-11-07');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    expect(service.exporting()).toBe(true);

    request.flush(new Blob(['report']), {
      status: 200,
      statusText: 'OK',
      headers: { 'Content-Disposition': 'attachment; filename="daily-report.csv"' },
    });

    expect(service.exporting()).toBe(false);
    expect(service.exportError()).toBeNull();
  });

  it('requests CSV export with zone filter parameter when provided', () => {
    service.exportCsv('2026-11-01', '2026-11-07', 'COLOMBO_ZONE').subscribe();
    const request = httpMock.expectOne('/api/v1/admin/reports/daily.csv?from=2026-11-01&to=2026-11-07&zone=COLOMBO_ZONE');
    expect(request.request.method).toBe('GET');

    request.flush(new Blob(['report']), {
      status: 200,
      statusText: 'OK',
      headers: { 'Content-Disposition': 'attachment; filename="daily-report.csv"' },
    });

    expect(service.exporting()).toBe(false);
  });

  it('exposes CSV export errors and clears the export loading state', () => {
    service.exportCsv('2026-11-01', '2026-11-07').subscribe({ error: () => undefined });
    const request = httpMock.expectOne('/api/v1/admin/reports/daily.csv?from=2026-11-01&to=2026-11-07');
    request.flush(new Blob(['forbidden']), { status: 403, statusText: 'Forbidden' });

    expect(service.exportError()).toBe('Failed to export delivery reports.');
    expect(service.exporting()).toBe(false);
  });
});
