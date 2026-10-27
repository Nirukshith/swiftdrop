import { Component, Input } from '@angular/core';

@Component({
  selector: 'lib-ui',
  standalone: true,
  imports: [],
  template: `
    <section class="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      @if (title) {
        <h2 class="mb-4 text-lg font-semibold text-slate-800">{{ title }}</h2>
      }
      <ng-content />
    </section>
  `,
})
export class Ui {
  @Input() title = '';
}
