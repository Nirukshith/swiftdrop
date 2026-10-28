import { Component } from '@angular/core';
import { DispatchComponent } from './dispatch/dispatch.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [DispatchComponent],
  template: `
    <app-dispatch></app-dispatch>
  `,
})
export class AdminComponent {}
