import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MerchantComponent } from '../merchant.component';

@Component({
  selector: 'app-merchant-portal', standalone: true, imports: [RouterLink, MerchantComponent],
  template: `<nav class="mx-auto mb-4 max-w-7xl text-right"><a routerLink="/merchant/bulk" class="inline-block rounded bg-indigo-700 px-4 py-2 text-white">Bulk CSV orders</a></nav><app-merchant />`,
})
export class MerchantPortalComponent {}
