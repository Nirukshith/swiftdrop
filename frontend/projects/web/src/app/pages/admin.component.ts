import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Ui } from '../../../../ui/src/lib/ui';

type AdminRole = 'ADMIN' | 'DISPATCHER';

interface Zone {
  name: string;
  districts: string[];
  served: boolean;
  baseFeeLkr: number;
}

interface PricingRule {
  kind: 'WEIGHT_BAND' | 'SIZE';
  key: string;
  minimumWeightKg?: number;
  maximumWeightKg?: number;
  chargeLkr: number;
  active: boolean;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Ui],
  templateUrl: './admin.component.html',
})
export class AdminComponent {
  readonly activeRole = signal<AdminRole>('DISPATCHER');
  readonly districts = [
    'Colombo',
    'Gampaha',
    'Kandy',
    'Galle',
    'Kalutara',
    'Kurunegala',
  ];
  readonly zones = signal<Zone[]>([
    { name: 'COLOMBO', districts: ['Colombo'], served: true, baseFeeLkr: 500 },
    { name: 'GAMPAHA', districts: ['Gampaha'], served: true, baseFeeLkr: 500 },
  ]);
  readonly pricingRules = signal<PricingRule[]>([
    {
      kind: 'WEIGHT_BAND',
      key: 'UP_TO_5KG',
      minimumWeightKg: 0,
      maximumWeightKg: 5,
      chargeLkr: 200,
      active: true,
    },
    { kind: 'SIZE', key: 'MEDIUM', chargeLkr: 150, active: true },
  ]);

  readonly zoneForm: FormGroup;
  readonly pricingForm: FormGroup;

  constructor(private readonly formBuilder: FormBuilder) {
    this.zoneForm = this.formBuilder.nonNullable.group({
      name: ['', [Validators.required]],
      districts: [<string[]>[], [Validators.required, Validators.minLength(1)]],
      served: [true],
      baseFeeLkr: [0, [Validators.required, Validators.min(0)]],
    });
    this.pricingForm = this.formBuilder.nonNullable.group({
      kind: ['SIZE' as PricingRule['kind'], [Validators.required]],
      key: ['', [Validators.required]],
      minimumWeightKg: [0],
      maximumWeightKg: [0],
      chargeLkr: [0, [Validators.required, Validators.min(0)]],
      active: [true],
    });
  }

  setRole(role: AdminRole): void {
    this.activeRole.set(role);
  }

  addZone(): void {
    if (this.zoneForm.invalid) {
      this.zoneForm.markAllAsTouched();
      return;
    }
    this.zones.update((zones) => [...zones, this.zoneForm.getRawValue()]);
    this.zoneForm.reset({ name: '', districts: [], served: true, baseFeeLkr: 0 });
  }

  addPricingRule(): void {
    if (this.pricingForm.invalid) {
      this.pricingForm.markAllAsTouched();
      return;
    }
    this.pricingRules.update((rules) => [...rules, this.pricingForm.getRawValue()]);
    this.pricingForm.reset({
      kind: 'SIZE',
      key: '',
      minimumWeightKg: 0,
      maximumWeightKg: 0,
      chargeLkr: 0,
      active: true,
    });
  }
}
