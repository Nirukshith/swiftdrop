import { validationMessage } from './validation-messages';

describe('validationMessage', () => {
  it('returns null without errors', () => {
    expect(validationMessage(null)).toBeNull();
    expect(validationMessage({})).toBeNull();
  });

  it('builds built-in messages with the label', () => {
    expect(validationMessage({ required: true }, 'Email')).toBe('Email is required');
    expect(validationMessage({ email: true })).toBe('Enter a valid email address');
    expect(validationMessage({ minlength: { requiredLength: 8, actualLength: 3 } }, 'Password')).toBe(
      'Password must be at least 8 characters',
    );
    expect(validationMessage({ maxlength: { requiredLength: 500, actualLength: 501 } }, 'Comment')).toBe(
      'Comment must be at most 500 characters',
    );
    expect(validationMessage({ min: { min: 0.1, actual: 0 } }, 'Weight')).toBe('Weight must be at least 0.1');
    expect(validationMessage({ max: { max: 50, actual: 60 } }, 'Weight')).toBe('Weight must be at most 50');
    expect(validationMessage({ pattern: { requiredPattern: '^SD-' } }, 'Tracking number')).toBe(
      'Tracking number has an invalid format',
    );
    expect(validationMessage({ somethingElse: true }, 'Field')).toBe('Field is invalid');
  });

  it('prefers overrides, then string error values', () => {
    expect(validationMessage({ pattern: true }, 'X', { pattern: 'Use SD-YYYYMMDD-XXXXXX' })).toBe('Use SD-YYYYMMDD-XXXXXX');
    expect(validationMessage({ district: 'Choose a supported district' })).toBe('Choose a supported district');
  });
});
