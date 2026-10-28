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
    path: 'merchant-registration',
    loadComponent: () =>
      import('./pages/merchant/merchant-registration.component').then((m) => m.MerchantRegistrationComponent),
  },
  {
    path: 'customer',
    canActivate: [roleGuard(['CUSTOMER'])],
    loadComponent: () =>
      import('./pages/customer.component').then((m) => m.CustomerComponent),
  },
  { path: 'customer/dashboard', canActivate: [roleGuard(['CUSTOMER'])], loadComponent: () => import('./pages/customer-history/customer-dashboard.component').then((m) => m.CustomerDashboardComponent) },
  { path: 'customer/parcels', canActivate: [roleGuard(['CUSTOMER'])], loadComponent: () => import('./pages/customer-history/customer-parcels.component').then((m) => m.CustomerParcelsComponent) },
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
    path: 'dispatch',
    canActivate: [roleGuard(['DISPATCHER', 'ADMIN'])],
    loadComponent: () =>
      import('./pages/dispatch/dispatch.component').then((m) => m.DispatchComponent),
  },
  {
    path: 'dispatcher',
    redirectTo: 'dispatch',
    pathMatch: 'full',
  },
  {
    path: 'track/:trackingNo',
    loadComponent: () =>
      import('./pages/track.component').then((m) => m.TrackComponent),
  },
];
