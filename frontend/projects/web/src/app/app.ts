import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { AuthService } from 'shared-auth';
import { NotificationBellComponent } from './notification/notification-bell/notification-bell.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, NotificationBellComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  public authService = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly title = signal('web');

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects || e.url)
    ),
    { initialValue: this.router.url }
  );

  isAuthPage(): boolean {
    const url = this.currentUrl() || this.router.url || '';
    const cleanUrl = url.split('?')[0].split('#')[0];
    return (
      cleanUrl === '' ||
      cleanUrl === '/' ||
      cleanUrl === '/login' ||
      cleanUrl === '/register' ||
      cleanUrl === '/merchant-registration' ||
      cleanUrl === '/forgot-password' ||
      cleanUrl === '/reset-password'
    );
  }
}
