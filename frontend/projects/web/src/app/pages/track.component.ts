import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-track',
  standalone: true,
  template: `
    <div class="p-8">
      <h1 class="text-2xl font-bold">Public Tracking Portal</h1>
      <p class="text-slate-600 mt-2">Tracking Number: <span class="font-mono font-semibold">{{ trackingNo }}</span></p>
    </div>
  `,
})
export class TrackComponent {
  private route = inject(ActivatedRoute);
  trackingNo = this.route.snapshot.paramMap.get('trackingNo');
}
