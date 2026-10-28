import { Component, input } from '@angular/core';
import { FormControlBase } from './form-control-base';

export interface SelectOption<V = string> {
  value: V;
  label: string;
  disabled?: boolean;
}

/**
 * Native select with label, hint and validation message. Option values keep their type
 * (numbers stay numbers). The placeholder option maps to null.
 */
@Component({
  selector: 'ui-select',
  standalone: true,
  host: { class: 'block' },
  template: `
    <div class="flex flex-col gap-1">
      @if (label()) {
        <label [for]="inputId()" class="text-sm font-medium text-ink">
          {{ label() }}
          @if (isRequired) {
            <span class="text-danger-600" aria-hidden="true">*</span>
          }
        </label>
      }
      <select
        [id]="inputId()"
        [disabled]="isDisabled()"
        [attr.aria-invalid]="errorMessage ? true : null"
        [attr.aria-describedby]="describedBy"
        [attr.aria-required]="isRequired || null"
        [class]="controlClasses()"
        (change)="onSelect($any($event.target).selectedIndex)"
        (blur)="onTouched()"
      >
        <option value="" [selected]="selectedIndex() === -1">{{ placeholder() }}</option>
        @for (option of options(); track $index) {
          <option [disabled]="option.disabled ?? false" [selected]="selectedIndex() === $index">
            {{ option.label }}
          </option>
        }
      </select>
      @if (errorMessage; as message) {
        <p [id]="inputId() + '-error'" class="text-xs text-danger-600">{{ message }}</p>
      } @else if (hint()) {
        <p [id]="inputId() + '-hint'" class="text-xs text-ink-muted">{{ hint() }}</p>
      }
    </div>
  `,
})
export class SelectComponent<V = string> extends FormControlBase<V> {
  readonly options = input<SelectOption<V>[]>([]);
  readonly placeholder = input('Select…');

  protected selectedIndex(): number {
    return this.options().findIndex((option) => option.value === this.value());
  }

  /** Index 0 is the placeholder option. */
  protected onSelect(index: number): void {
    const option = this.options()[index - 1];
    this.updateValue(option ? option.value : null);
  }
}
