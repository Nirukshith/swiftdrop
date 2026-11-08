import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ReportsComponent } from './reports.component';
import { ReportsService } from './reports.service';

describe('ReportsComponent', () => {
  let fixture: ComponentFixture<ReportsComponent>;
  let reportsService: {
    rows: Signal<ReadonlyArray<{
      reportDate: string;
      totalDeliveries: number;
      successfulDeliveries: number;
      failedDeliveries: number;
      successRate?: number;
    }>>;
    loading: Signal<boolean>;
    error: Signal<string | null>;
    load: jest.Mock;
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
      load: jest.fn().mockReturnValue({ subscribe: jest.fn() }),
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

  it('does not call the API for an invalid date range', () => {
    const component = fixture.componentInstance;
    component.filterForm.setValue({ from: '2026-11-07', to: '2026-11-01' });
    component.loadReport();
    expect(component.invalidRange()).toBe(true);
    expect(reportsService.load).toHaveBeenCalledTimes(1);
  });
});
