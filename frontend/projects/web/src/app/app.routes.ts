import { Routes } from '@angular/router';
import { roleGuard } from 'shared-auth';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('shared-auth').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('shared-auth').then((m) => m.RegisterComponent),
  },
  {
    path: 'customer',
    canActivate: [roleGuard(['CUSTOMER'])],
    loadComponent: () =>
      import('./pages/customer.component').then((m) => m.CustomerComponent),
  },
  {
    path: 'merchant',
    canActivate: [roleGuard(['MERCHANT'])],
    loadComponent: () =>
      import('./pages/merchant.component').then((m) => m.MerchantComponent),
  },
  {
    path: 'courier',
    canActivate: [roleGuard(['COURIER'])],
    loadComponent: () =>
      import('./pages/courier.component').then((m) => m.CourierComponent),
  },
  {
    path: 'admin',
    canActivate: [roleGuard(['ADMIN'])],
    loadComponent: () =>
      import('./pages/admin.component').then((m) => m.AdminComponent),
  },
  {
    path: 'track/:trackingNo',
    loadComponent: () =>
      import('./pages/track.component').then((m) => m.TrackComponent),
  },
];
