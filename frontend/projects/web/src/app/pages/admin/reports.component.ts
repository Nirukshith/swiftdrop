import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ReportsService } from './reports.service';
import { AdminSearchService } from './admin-search.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reports.component.html',
})
export class ReportsComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  readonly reportsService = inject(ReportsService);
  readonly adminSearchService = inject(AdminSearchService);
  readonly invalidRange = signal(false);
  readonly rows = this.reportsService.rows;
  readonly availableZones = this.reportsService.availableZones;
  readonly zoneSummaries = this.reportsService.zoneSummaries;

  readonly filterForm = this.formBuilder.nonNullable.group({
    from: ['', Validators.required],
    to: ['', Validators.required],
    zone: [''],
  });

  readonly filteredRows = computed(() => {
    const q = this.adminSearchService.query().trim().toLowerCase();
    if (!q) {
      return this.rows();
    }
    return this.rows().filter((row) => row.reportDate.toLowerCase().includes(q));
  });

  readonly displayRows = computed(() => {
    return this.filteredRows().filter((r) => r.totalDeliveries > 0);
  });

  readonly activeDaysCount = computed(() =>
    this.rows().filter((r) => r.totalDeliveries > 0).length
  );

  readonly totalDeliveries = computed(() =>
    this.rows().reduce((total, row) => total + row.totalDeliveries, 0)
  );
  readonly successfulDeliveries = computed(() =>
    this.rows().reduce((total, row) => total + row.successfulDeliveries, 0)
  );
  readonly failedDeliveries = computed(() =>
    this.rows().reduce((total, row) => total + row.failedDeliveries, 0)
  );
  readonly hasDeliveries = computed(() => this.rows().some((row) => row.totalDeliveries > 0));
  readonly successRate = computed(() => {
    const total = this.totalDeliveries();
    return total === 0 ? 0 : this.successfulDeliveries() / total;
  });
  readonly maxDailyTotal = computed(() =>
    Math.max(1, ...this.rows().map((row) => row.totalDeliveries))
  );

  ngOnInit(): void {
    this.reportsService.loadAvailableZones().subscribe({ error: () => undefined });
    const today = new Date();
    const from = new Date(today.getTime() - 6 * 24 * 60 * 60 * 1000);
    this.filterForm.setValue({
      from: this.toColomboDate(from),
      to: this.toColomboDate(today),
      zone: '',
    });
    this.loadReport();
  }

  setPreset(days: number): void {
    const today = new Date();
    const from = new Date(today.getTime() - (days - 1) * 24 * 60 * 60 * 1000);
    this.filterForm.patchValue({
      from: this.toColomboDate(from),
      to: this.toColomboDate(today),
    });
    this.loadReport();
  }

  setThisMonth(): void {
    const today = new Date();
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
    this.filterForm.patchValue({
      from: this.toColomboDate(firstDay),
      to: this.toColomboDate(today),
    });
    this.loadReport();
  }


  formatDayOfWeek(dateStr: string): string {
    try {
      const [y, m, d] = dateStr.split('-').map(Number);
      const date = new Date(y, m - 1, d);
      return date.toLocaleDateString('en-US', { weekday: 'short' });
    } catch {
      return '';
    }
  }

  formatZoneName(zoneName: string): string {
    if (!zoneName) return '';
    return zoneName
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (c) => c.toUpperCase());
  }

  loadReport(): void {
    const { from, to, zone } = this.filterForm.getRawValue();
    this.invalidRange.set(!from || !to || from > to);
    if (this.invalidRange()) {
      return;
    }
    this.reportsService.load(from, to, zone).subscribe({ error: () => undefined });
    this.reportsService.loadZoneReports(from, to).subscribe({ error: () => undefined });
  }

  exportCsv(): void {
    const { from, to, zone } = this.filterForm.getRawValue();
    this.invalidRange.set(!from || !to || from > to);
    if (this.invalidRange() || this.reportsService.exporting()) {
      return;
    }

    this.reportsService.exportCsv(from, to, zone).subscribe({
      next: (response) => this.downloadCsv(response),
      error: () => undefined,
    });
  }

  dailyBarWidth(total: number): string {
    return `${(total / this.maxDailyTotal()) * 100}%`;
  }

  successBarWidth(row: { totalDeliveries: number; successfulDeliveries: number }): string {
    return row.totalDeliveries === 0
      ? '0%'
      : `${(row.successfulDeliveries / row.totalDeliveries) * 100}%`;
  }

  failureBarWidth(row: { totalDeliveries: number; failedDeliveries: number }): string {
    return row.totalDeliveries === 0
      ? '0%'
      : `${(row.failedDeliveries / row.totalDeliveries) * 100}%`;
  }

  private toColomboDate(date: Date): string {
    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Colombo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    return formatter.format(date);
  }

  private toDateInput(date: Date): string {
    return this.toColomboDate(date);
  }

  private downloadCsv(response: HttpResponse<Blob>): void {
    if (!response.body) {
      return;
    }

    const filename = this.filenameFrom(response.headers.get('Content-Disposition'));
    const url = URL.createObjectURL(response.body);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  private filenameFrom(contentDisposition: string | null): string {
    const match = contentDisposition?.match(/filename="?([^";]+)"?/i);
    return match?.[1] || 'daily-report.csv';
  }
}
