import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'lib-auth-callback',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="callback-container">
      <div class="callback-card">
        <div class="brand-badge">SwiftDrop</div>

        @if (isLoading()) {
          <div class="loading-state">
            <div class="spinner"></div>
            <h2>Completing Sign In...</h2>
            <p>Please wait while we establish your secure session.</p>
          </div>
        }

        @if (errorMessage()) {
          <div class="error-state">
            <div class="error-banner" role="alert">
              <span class="error-icon">⚠️</span>
              <span>{{ errorMessage() }}</span>
            </div>
            <a routerLink="/login" class="back-link">Return to Sign In</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      padding: 2rem 1rem;
      background: radial-gradient(circle at top right, #1e293b 0%, #0f172a 100%);
      color: #f8fafc;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }

    .callback-card {
      width: 100%;
      max-width: 440px;
      padding: 2.5rem;
      background: rgba(30, 41, 59, 0.7);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 1rem;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3);
      text-align: center;
    }

    .brand-badge {
      display: inline-block;
      padding: 0.25rem 0.75rem;
      background: rgba(59, 130, 246, 0.1);
      border: 1px solid rgba(59, 130, 246, 0.3);
      border-radius: 9999px;
      color: #60a5fa;
      font-size: 0.875rem;
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      margin-bottom: 1.5rem;
    }

    .loading-state h2 {
      font-size: 1.5rem;
      font-weight: 600;
      margin: 1rem 0 0.5rem;
      color: #ffffff;
    }

    .loading-state p {
      color: #94a3b8;
      font-size: 0.875rem;
    }

    .spinner {
      width: 40px;
      height: 40px;
      margin: 1rem auto;
      border: 3px solid rgba(255, 255, 255, 0.1);
      border-top-color: #3b82f6;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .error-banner {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.875rem 1rem;
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: 0.5rem;
      color: #fca5a5;
      font-size: 0.875rem;
      text-align: left;
      margin-bottom: 1.5rem;
    }

    .back-link {
      display: inline-block;
      padding: 0.75rem 1.5rem;
      background: #3b82f6;
      color: white;
      text-decoration: none;
      font-weight: 500;
      border-radius: 0.5rem;
      transition: background-color 0.2s;
    }

    .back-link:hover {
      background: #2563eb;
    }
  `]
})
export class CallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const error = this.route.snapshot.queryParamMap.get('error');

    if (error) {
      this.isLoading.set(false);
      if (error === 'access_denied') {
        this.errorMessage.set('Google sign-in was canceled or denied.');
      } else if (error === 'account_disabled') {
        this.errorMessage.set('Your SwiftDrop account is disabled. Please contact support.');
      } else {
        this.errorMessage.set('Authentication failed. Please try again.');
      }
      return;
    }

    try {
      const initialized = await this.authService.initializeSession();
      if (initialized && this.authService.isAuthenticated()) {
        const userRole = this.authService.userRole()?.toLowerCase() || 'customer';
        await this.router.navigate([`/${userRole}`], { replaceUrl: true });
      } else {
        this.isLoading.set(false);
        this.errorMessage.set('Failed to restore session. Please sign in again.');
      }
    } catch {
      this.isLoading.set(false);
      this.errorMessage.set('An unexpected error occurred while completing sign-in.');
    }
  }
}
