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
    path: 'forgot-password',
    loadComponent: () =>
      import('shared-auth').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('shared-auth').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('shared-auth').then((m) => m.CallbackComponent),
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
  { path: 'customer/parcels/:id', canActivate: [roleGuard(['CUSTOMER'])], loadComponent: () => import('./pages/tracking/parcel-detail.component').then((m) => m.ParcelDetailComponent) },
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
    path: 'courier/profile',
    canActivate: [roleGuard(['COURIER'])],
    loadComponent: () => import('./pages/courier-profile.component').then((m) => m.CourierProfileComponent),
  },
  {
    path: 'courier/summary',
    canActivate: [roleGuard(['COURIER'])],
    loadComponent: () => import('./pages/courier-summary.component').then((m) => m.CourierSummaryComponent),
  },
  {
    path: 'admin',
    canActivate: [roleGuard(['ADMIN'])],
    loadComponent: () =>
      import('./pages/admin.component').then((m) => m.AdminComponent),
  },
  {
    path: 'admin/users',
    canActivate: [roleGuard(['ADMIN'])],
    loadComponent: () =>
      import('./pages/admin/admin-users.component').then((m) => m.AdminUsersComponent),
  },
  {
    path: 'admin/merchants',
    canActivate: [roleGuard(['ADMIN'])],
    loadComponent: () =>
      import('./pages/admin/merchant-approval.component').then((m) => m.MerchantApprovalComponent),
  },
  {
    path: 'dispatch',
    canActivate: [roleGuard(['DISPATCHER'])],
    loadComponent: () =>
      import('./pages/dispatch/dispatch.component').then((m) => m.DispatchComponent),
  },
  {
    path: 'dispatch/zones',
    canActivate: [roleGuard(['DISPATCHER'])],
    loadComponent: () =>
      import('./pages/dispatch/dispatch-zones.component').then((m) => m.DispatchZonesComponent),
  },
  {
    path: 'dispatch/pricing',
    canActivate: [roleGuard(['DISPATCHER'])],
    loadComponent: () =>
      import('./pages/dispatch/dispatch-pricing.component').then((m) => m.DispatchPricingComponent),
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
