import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from 'shared-auth';
import { CardComponent } from 'ui';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, RouterLink, CardComponent],
  templateUrl: './admin.component.html',
})
export class AdminComponent {
  readonly authService = inject(AuthService, { optional: true });
}
