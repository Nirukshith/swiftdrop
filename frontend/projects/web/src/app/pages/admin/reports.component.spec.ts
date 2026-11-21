import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal, signal, WritableSignal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ReportsComponent } from './reports.component';
import { ReportsService } from './reports.service';
import { ZoneReportSummary } from './reports.model';
import { Zone } from '../dispatch/dispatch.model';

describe('ReportsComponent', () => {
  let fixture: ComponentFixture<ReportsComponent>;
  let reportsService: {
    rows: WritableSignal<ReadonlyArray<{
      reportDate: string;
      totalDeliveries: number;
      successfulDeliveries: number;
      failedDeliveries: number;
      successRate?: number;
    }>>;
    zoneSummaries: WritableSignal<ZoneReportSummary[]>;
    availableZones: WritableSignal<Zone[]>;
    loading: Signal<boolean>;
    error: Signal<string | null>;
    exporting: Signal<boolean>;
    exportError: Signal<string | null>;
    load: jest.Mock;
    loadZoneReports: jest.Mock;
    loadAvailableZones: jest.Mock;
    exportCsv: jest.Mock;
  };

  beforeEach(async () => {
    reportsService = {
      rows: signal([{
        reportDate: '2026-11-01',
        totalDeliveries: 10,
        successfulDeliveries: 8,
        failedDeliveries: 2,
        successRate: 0.8,
      }]),
      zoneSummaries: signal<ZoneReportSummary[]>([{
        zoneName: 'COLOMBO_ZONE',
        districts: ['Colombo'],
        served: true,
        totalDeliveries: 65,
        successfulDeliveries: 61,
        failedDeliveries: 4,
        successRate: 0.9385,
      }]),
      availableZones: signal<Zone[]>([{
        id: 'z1',
        name: 'COLOMBO_ZONE',
        districts: ['Colombo'],
        served: true,
        baseFeeLkr: 500,
        createdAt: '2026-01-01',
        updatedAt: '2026-01-01',
      }]),
      loading: signal(false),
      error: signal(null),
      exporting: signal(false),
      exportError: signal(null),
      load: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      loadZoneReports: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      loadAvailableZones: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
      exportCsv: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
    };
    await TestBed.configureTestingModule({
      imports: [ReportsComponent],
      providers: [
        provideRouter([]),
        { provide: ReportsService, useValue: reportsService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReportsComponent);
    fixture.detectChanges();
  });

  it('renders summaries and table data from API rows', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('10');
    expect(element.textContent).toContain('80%');
    expect(element.querySelector('#daily-reports-table')).toBeTruthy();
  });

  it('renders zone delivery performance table when zone summaries exist', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#zone-reports-table')).toBeTruthy();
    expect(element.textContent).toContain('Colombo Zone');
    expect(element.textContent).toContain('65');
  });

  it('renders the empty state when the selected range contains only zero rows', () => {
    reportsService.rows.set([{
      reportDate: '2026-11-01',
      totalDeliveries: 0,
      successfulDeliveries: 0,
      failedDeliveries: 0,
      successRate: 0,
    }]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('No delivery report data is available for this date range.');
  });

  it('does not call the API for an invalid date range', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '2026-11-07', to: '2026-11-01', zone: '' });
    component.loadReport();
    expect(component.invalidRange()).toBe(true);
    expect(reportsService.load).toHaveBeenCalledTimes(1);
  });

  it('does not call the API when a report date is missing', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '', to: '2026-11-07', zone: '' });

    component.loadReport();

    expect(component.invalidRange()).toBe(true);
    expect(reportsService.load).toHaveBeenCalledTimes(1);
  });

  it('renders a report loading error from the service', () => {
    reportsService.error.set('Failed to load delivery reports.');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('Failed to load delivery reports.');
  });

  it('renders the Export CSV button', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Export CSV');
  });

  it('exports the selected date range and zone filter', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '2026-11-01', to: '2026-11-07', zone: 'COLOMBO_ZONE' });

    component.exportCsv();

    expect(reportsService.exportCsv).toHaveBeenCalledWith('2026-11-01', '2026-11-07', 'COLOMBO_ZONE');
  });

  it('does not export an invalid date range', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '2026-11-07', to: '2026-11-01', zone: '' });

    component.exportCsv();

    expect(reportsService.exportCsv).not.toHaveBeenCalled();
    expect(component.invalidRange()).toBe(true);
  });

  it('does not export while an export is already in progress', () => {
    reportsService.exporting = signal(true);
    const component = fixture.componentInstance;

    component.exportCsv();

    expect(reportsService.exportCsv).not.toHaveBeenCalled();
  });

  it('downloads the Blob with the server-provided filename', () => {
    const click = jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      expect(this.download).toBe('report-2026-11.csv');
    });
    const createObjectUrl = jest.fn().mockReturnValue('blob:url');
    const revokeObjectUrl = jest.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectUrl });
    reportsService.exportCsv.mockReturnValue(of(new HttpResponse({
      body: new Blob(['report']),
      headers: new HttpHeaders({
        'Content-Disposition': 'attachment; filename="report-2026-11.csv"',
      }),
    })));

    fixture.componentInstance.exportCsv();

    expect(createObjectUrl).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    click.mockRestore();
    delete (URL as { createObjectURL?: unknown }).createObjectURL;
    delete (URL as { revokeObjectURL?: unknown }).revokeObjectURL;
  });

  it('updates date range when quick presets are clicked', () => {
    const component = fixture.componentInstance;
    component.setPreset(14);
    expect(reportsService.load).toHaveBeenCalled();
    const { from, to } = component.filterForm.getRawValue();
    expect(from).toBeTruthy();
    expect(to).toBeTruthy();
    expect(from <= to).toBe(true);

    component.setThisMonth();
    expect(reportsService.load).toHaveBeenCalled();
  });

  it('filters rows based on global search query', () => {
    const component = fixture.componentInstance;
    reportsService.rows.set([
      { reportDate: '2026-11-18', totalDeliveries: 10, successfulDeliveries: 9, failedDeliveries: 1, successRate: 0.9 },
      { reportDate: '2026-11-19', totalDeliveries: 15, successfulDeliveries: 14, failedDeliveries: 1, successRate: 0.93 },
    ]);
    fixture.detectChanges();

    component.adminSearchService.setQuery('2026-11-18');
    expect(component.filteredRows().length).toBe(1);
    expect(component.filteredRows()[0].reportDate).toBe('2026-11-18');

    component.adminSearchService.clear();
    expect(component.filteredRows().length).toBe(2);
  });

  it('filters out zero-activity days from display rows cleanly', () => {
    const component = fixture.componentInstance;
    reportsService.rows.set([
      { reportDate: '2026-11-17', totalDeliveries: 0, successfulDeliveries: 0, failedDeliveries: 0 },
      { reportDate: '2026-11-18', totalDeliveries: 10, successfulDeliveries: 9, failedDeliveries: 1 },
    ]);
    fixture.detectChanges();

    expect(component.displayRows().length).toBe(1);
    expect(component.displayRows()[0].reportDate).toBe('2026-11-18');
    expect(component.formatDayOfWeek('2026-11-18')).toBeTruthy();
  });

  it('formats zone names cleanly', () => {
    const component = fixture.componentInstance;
    expect(component.formatZoneName('COLOMBO_ZONE')).toBe('Colombo Zone');
    expect(component.formatZoneName('NORTH_CENTRAL_ZONE')).toBe('North Central Zone');
    expect(component.formatZoneName('')).toBe('');
  });
});
