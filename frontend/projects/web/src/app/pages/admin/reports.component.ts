import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CardComponent } from 'ui';
import { ReportsService } from './reports.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardComponent],
  templateUrl: './reports.component.html',
})
export class ReportsComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  readonly reportsService = inject(ReportsService);
  readonly invalidRange = signal(false);
  readonly rows = this.reportsService.rows;

  readonly filterForm = this.formBuilder.nonNullable.group({
    from: ['', Validators.required],
    to: ['', Validators.required],
  });

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
    const today = new Date();
    const from = new Date(today.getTime() - 6 * 24 * 60 * 60 * 1000);
    this.filterForm.setValue({
      from: this.toColomboDate(from),
      to: this.toColomboDate(today),
    });
    this.loadReport();
  }

  loadReport(): void {
    const { from, to } = this.filterForm.getRawValue();
    this.invalidRange.set(!from || !to || from > to);
    if (this.invalidRange()) {
      return;
    }
    this.reportsService.load(from, to).subscribe({ error: () => undefined });
  }

  exportCsv(): void {
    const { from, to } = this.filterForm.getRawValue();
    this.invalidRange.set(!from || !to || from > to);
    if (this.invalidRange() || this.reportsService.exporting()) {
      return;
    }

    this.reportsService.exportCsv(from, to).subscribe({
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
