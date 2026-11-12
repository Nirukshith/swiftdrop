import { CommonModule } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import { BulkResult, MerchantBulkService } from './merchant-bulk.service';
import { CSV_HEADER, MAX_CSV_BYTES, PreviewRow, encodeMerchantCsv, parseMerchantCsv } from './merchant-csv';

@Component({
  selector: 'app-merchant-bulk', standalone: true, imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="mx-auto max-w-6xl space-y-5 p-6">
      <a routerLink="/merchant" class="text-indigo-700 underline">Back to merchant portal</a>
      <h1 class="text-2xl font-bold">Bulk CSV orders</h1>
      <p>Upload up to 100 orders in a UTF-8 CSV file (maximum 5 MiB). Preview makes no parcels. Review the rows, then confirm creation.</p>
      <a [href]="templateUrl" download="merchant-orders-template.csv" class="text-indigo-700 underline">Download CSV template</a>
      <div class="space-y-2">
        <label for="csv-file" class="block font-medium">CSV file</label>
        <input id="csv-file" type="file" accept=".csv,text/csv" [disabled]="busy() || !!submittedCsv" (change)="loadFile($event)" />
      </div>
      <p *ngIf="reading()" role="status">Reading CSV…</p>
      <p *ngIf="error()" role="alert" class="rounded border border-red-300 bg-red-50 p-3">{{ error() }}</p>
      <section *ngIf="rows().length" class="space-y-3">
        <h2 class="text-xl font-semibold">Preview</h2>
        <p>{{ validRows().length }} valid / {{ rows().length - validRows().length }} invalid rows. Invalid rows will not be submitted. Correct them in the source file and upload again, or remove them here.</p>
        <button type="button" class="rounded border px-3 py-2" [disabled]="busy() || !!submittedCsv" (click)="removeInvalid()">Remove invalid rows</button>
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm"><thead><tr><th>Source row</th><th>Reference</th><th>Recipient</th><th>Pickup → Dropoff</th><th>Weight / size</th><th>Validation</th></tr></thead>
            <tbody><tr *ngFor="let row of rows()" [class.bg-red-50]="row.errors.length" class="border-t">
              <td>{{ row.rowNumber }}</td><td>{{ row.cells[0] }}</td><td>{{ row.cells[1] }}</td>
              <td>{{ row.cells[6] }} → {{ row.cells[11] }}</td><td>{{ row.cells[14] }} kg / {{ row.cells[13] }}</td>
              <td>{{ row.errors.length ? row.errors.join('; ') : 'Valid' }}</td>
            </tr></tbody>
          </table>
        </div>
        <label for="upload-key" class="block font-medium">Upload reference (keep this to identify retries)</label>
        <input id="upload-key" class="w-full rounded border p-2" maxlength="128" [ngModel]="uploadKey()" (ngModelChange)="uploadKey.set($event)" [disabled]="busy() || !!submittedCsv" />
        <p>After confirmation, retry uses the same file and reference. Never use a new reference to repeat an uncertain parcel creation.</p>
        <button type="button" class="rounded bg-indigo-700 px-4 py-2 text-white disabled:opacity-50" (click)="confirm()" [disabled]="busy() || reading() || !validRows().length || !uploadKey().trim() || !!submittedCsv">Confirm {{ validRows().length }} valid orders</button>
      </section>
      <p *ngIf="busy()" role="status">Creating orders…</p>
      <section *ngIf="result() as report" class="space-y-3" aria-live="polite">
        <h2 class="text-xl font-semibold">Upload results</h2>
        <p>{{ report.acceptedRows }} successful / {{ report.rejectedRows }} failed or unresolved / {{ report.totalRows }} total</p>
        <ul><li *ngFor="let item of report.results" class="border-t py-2">
          Source row {{ sourceRows[item.rowNumber] || item.rowNumber }}:
          <span *ngIf="item.accepted && item.order as order">{{ order.trackingNumber }} — {{ order.status }} — {{ order.fee | number:'1.2-2' }} LKR</span>
          <span *ngIf="!item.accepted">{{ item.errors.join('; ') }}</span>
        </li></ul>
      </section>
      <div class="flex gap-3">
        <button *ngIf="submittedCsv" type="button" class="rounded border px-3 py-2" [disabled]="busy()" (click)="retry()">Check / replay this upload</button>
        <button *ngIf="rows().length || submittedCsv" type="button" class="rounded border px-3 py-2" [disabled]="busy()" (click)="reset()">Start another upload</button>
      </div>
    </main>`,
})
export class MerchantBulkComponent implements OnDestroy {
  private readonly api = inject(MerchantBulkService);
  private subscription?: Subscription;
  private reader?: FileReader;
  private generation = 0;
  readonly rows = signal<PreviewRow[]>([]);
  readonly validRows = computed(() => this.rows().filter(row => !row.errors.length));
  readonly result = signal<BulkResult | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly reading = signal(false);
  readonly uploadKey = signal('');
  readonly templateUrl = 'data:text/csv;charset=utf-8,' + encodeURIComponent(CSV_HEADER + '\nexample-001,Recipient Name,+94771234567,12 Main Road,,Colombo,Colombo,00700,45 Lake Road,,Gampaha,Gampaha,11000,SMALL,1.50\n');
  submittedCsv: string | null = null;
  sourceRows: Record<number, number> = {};

  loadFile(event: Event) {
    if (this.busy() || this.submittedCsv) return;
    const file = (event.target as HTMLInputElement).files?.[0];
    this.reset();
    if (!file) return;
    if (file.size > MAX_CSV_BYTES) { this.error.set('CSV exceeds 5 MiB.'); return; }
    const generation = ++this.generation;
    const reader = new FileReader(); this.reader = reader; this.reading.set(true);
    reader.onload = () => {
      if (generation !== this.generation) return;
      try { this.preview(new TextDecoder('utf-8', { fatal: true }).decode(reader.result as ArrayBuffer)); }
      catch { this.error.set('CSV must be valid UTF-8.'); }
      this.reading.set(false);
    };
    reader.onerror = () => { if (generation === this.generation) { this.error.set('CSV could not be read.'); this.reading.set(false); } };
    reader.readAsArrayBuffer(file);
  }
  preview(text: string) {
    try { this.rows.set(parseMerchantCsv(text)); this.uploadKey.set(crypto.randomUUID()); this.error.set(''); }
    catch (error) { this.rows.set([]); this.error.set(error instanceof Error ? error.message : 'Invalid CSV.'); }
  }
  removeInvalid() { if (!this.busy() && !this.submittedCsv) this.rows.set(this.validRows()); }
  confirm() {
    if (this.busy() || this.reading() || this.submittedCsv || !this.validRows().length || !this.uploadKey().trim()) return;
    const payload = encodeMerchantCsv(this.validRows());
    this.submittedCsv = payload.csv; this.sourceRows = payload.sourceRows;
    this.retry();
  }
  retry() {
    if (this.busy() || !this.submittedCsv) return;
    this.busy.set(true); this.error.set('');
    this.subscription = this.api.submit(this.submittedCsv, this.uploadKey()).pipe(finalize(() => this.busy.set(false))).subscribe({
      next: report => this.result.set(report),
      error: response => this.error.set(response?.error?.message || 'Upload response was not received. Check this upload with the same reference; do not submit it again with a new one.'),
    });
  }
  reset() {
    if (this.busy()) return;
    this.generation++; this.reader?.abort(); this.reading.set(false); this.rows.set([]); this.result.set(null);
    this.error.set(''); this.uploadKey.set(''); this.submittedCsv = null; this.sourceRows = {};
  }
  ngOnDestroy() { this.generation++; this.reader?.abort(); this.subscription?.unsubscribe(); }
}
