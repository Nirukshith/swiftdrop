import { ValidationErrors } from '@angular/forms';

/** Overrides per error key, e.g. `{ pattern: 'Use the format SD-YYYYMMDD-XXXXXX' }`. */
export type ValidationMessageOverrides = Record<string, string>;

/**
 * Turns Angular validation errors into one human-readable message.
 * Order: caller overrides, then a string error value (custom validators), then built-in texts.
 * Returns null when there are no errors.
 */
export function validationMessage(
  errors: ValidationErrors | null | undefined,
  label = 'This field',
  overrides: ValidationMessageOverrides = {},
): string | null {
  if (!errors) {
    return null;
  }
  const key = Object.keys(errors)[0];
  if (key === undefined) {
    return null;
  }
  if (overrides[key]) {
    return overrides[key];
  }
  const value = errors[key];
  if (typeof value === 'string') {
    return value;
  }
  switch (key) {
    case 'required':
      return `${label} is required`;
    case 'email':
      return 'Enter a valid email address';
    case 'minlength':
      return `${label} must be at least ${value.requiredLength} characters`;
    case 'maxlength':
      return `${label} must be at most ${value.requiredLength} characters`;
    case 'min':
      return `${label} must be at least ${value.min}`;
    case 'max':
      return `${label} must be at most ${value.max}`;
    case 'pattern':
      return `${label} has an invalid format`;
    default:
      return `${label} is invalid`;
  }
}
