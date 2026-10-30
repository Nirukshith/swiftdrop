import { TestBed } from '@angular/core/testing';
import { ToastContainerComponent } from './toast-container.component';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('adds toasts and removes them after their duration', () => {
    const service = TestBed.inject(ToastService);
    service.success('Saved', 1000);
    service.error('Failed', 0);
    expect(service.toasts().map((t) => [t.type, t.message])).toEqual([
      ['success', 'Saved'],
      ['error', 'Failed'],
    ]);

    jest.advanceTimersByTime(1000);
    expect(service.toasts().map((t) => t.message)).toEqual(['Failed']);
  });

  it('dismisses by id and clears all', () => {
    const service = TestBed.inject(ToastService);
    const id = service.info('One', 0);
    service.warning('Two', 0);
    service.dismiss(id);
    expect(service.toasts().map((t) => t.message)).toEqual(['Two']);
    service.clear();
    expect(service.toasts()).toEqual([]);
  });
});

describe('ToastContainerComponent', () => {
  it('renders toasts with roles and a dismiss button', () => {
    const fixture = TestBed.createComponent(ToastContainerComponent);
    const service = TestBed.inject(ToastService);
    service.success('Parcel created', 0);
    service.error('Payment failed', 0);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Parcel created');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Payment failed');

    (el.querySelector('[aria-label="Dismiss"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.textContent).not.toContain('Parcel created');
  });
});
