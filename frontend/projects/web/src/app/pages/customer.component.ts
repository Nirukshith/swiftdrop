import { Component } from '@angular/core';

@Component({
  selector: 'app-customer',
  standalone: true,
  template: `
    <div class="p-8">
      <h1 class="text-2xl font-bold">Customer Portal</h1>
      <p class="text-slate-600 mt-2">Welcome to the Customer Portal.</p>
    </div>
  `,
})
export class CustomerComponent {}
