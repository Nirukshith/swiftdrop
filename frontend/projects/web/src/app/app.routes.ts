import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'customer',
    loadComponent: () =>
      import('./pages/customer.component').then((m) => m.CustomerComponent),
  },
  {
    path: 'merchant',
    loadComponent: () =>
      import('./pages/merchant.component').then((m) => m.MerchantComponent),
  },
  {
    path: 'courier',
    loadComponent: () =>
      import('./pages/courier.component').then((m) => m.CourierComponent),
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./pages/admin.component').then((m) => m.AdminComponent),
  },
  {
    path: 'track/:trackingNo',
    loadComponent: () =>
      import('./pages/track.component').then((m) => m.TrackComponent),
  },
];
