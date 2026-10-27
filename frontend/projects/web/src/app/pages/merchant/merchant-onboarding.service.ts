import { Injectable, signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  MerchantRegistrationRequest,
  MerchantRegistrationResponse,
} from './merchant-registration.model';

@Injectable({ providedIn: 'root' })
export class MerchantOnboardingService {
  private registrations = signal<MerchantRegistrationResponse[]>([]);

  register(request: MerchantRegistrationRequest): Observable<MerchantRegistrationResponse> {
    const duplicate = this.registrations().some(
      (registration) => registration.email.toLowerCase() === request.email.toLowerCase()
    );
    if (duplicate) {
      return throwError(() => ({ status: 409 }));
    }

    const registration: MerchantRegistrationResponse = {
      ...request,
      id: crypto.randomUUID(),
      status: 'PENDING',
      createdAt: new Date().toISOString(),
      message: 'Registration submitted for administrator review.',
    };
    this.registrations.update((current) => [...current, registration]);
    return of(registration).pipe(delay(150));
  }
}
