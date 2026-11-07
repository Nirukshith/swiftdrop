import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  MerchantRegistrationRequest,
  MerchantRegistrationResponse,
} from './merchant-registration.model';

@Injectable({ providedIn: 'root' })
export class MerchantOnboardingService {
  private readonly http = inject(HttpClient);

  register(request: MerchantRegistrationRequest): Observable<MerchantRegistrationResponse> {
    return this.http.post<MerchantRegistrationResponse>('/api/v1/merchants/register', request);
  }
}
