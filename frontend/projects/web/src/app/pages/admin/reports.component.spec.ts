import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal, signal, WritableSignal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ReportsComponent } from './reports.component';
import { ReportsService } from './reports.service';

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
    loading: Signal<boolean>;
    error: Signal<string | null>;
    exporting: Signal<boolean>;
    exportError: Signal<string | null>;
    load: jest.Mock;
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
      loading: signal(false),
      error: signal(null),
      exporting: signal(false),
      exportError: signal(null),
      load: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
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

  it('renders summaries and chart data from API rows', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('10');
    expect(element.textContent).toContain('80%');
    expect(element.textContent).toContain('Daily delivery trend');
    expect(element.textContent).toContain('Success versus failure');
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
    component.filterForm.setValue({ from: '2026-11-07', to: '2026-11-01' });
    component.loadReport();
    expect(component.invalidRange()).toBe(true);
    expect(reportsService.load).toHaveBeenCalledTimes(1);
  });

  it('does not call the API when a report date is missing', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '', to: '2026-11-07' });

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

  it('exports the selected date range', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '2026-11-01', to: '2026-11-07' });

    component.exportCsv();

    expect(reportsService.exportCsv).toHaveBeenCalledWith('2026-11-01', '2026-11-07');
  });

  it('does not export an invalid date range', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '2026-11-07', to: '2026-11-01' });

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
});
