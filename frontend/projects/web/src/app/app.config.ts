import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor, AuthService, MockAuthService } from 'shared-auth';

import { routes } from './app.routes';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    ...(environment.useMocks
      ? [{ provide: AuthService, useClass: MockAuthService }]
      : []),
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      return authService.initializeSession();
    }),
  ],
};
