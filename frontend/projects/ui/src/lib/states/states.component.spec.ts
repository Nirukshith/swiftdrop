import { TestBed } from '@angular/core/testing';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from './states.component';

describe('LoadingStateComponent', () => {
  it('shows a polite status message', () => {
    const fixture = TestBed.createComponent(LoadingStateComponent);
    fixture.componentRef.setInput('message', 'Loading parcels…');
    fixture.detectChanges();
    const status = fixture.nativeElement.querySelector('[role="status"]');
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(status.textContent).toContain('Loading parcels…');
  });
});

describe('EmptyStateComponent', () => {
  it('shows title and message', () => {
    const fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.componentRef.setInput('title', 'No parcels yet');
    fixture.componentRef.setInput('message', 'Create your first delivery.');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No parcels yet');
    expect(fixture.nativeElement.textContent).toContain('Create your first delivery.');
  });
});

describe('ErrorStateComponent', () => {
  it('shows an alert and emits retry', () => {
    const fixture = TestBed.createComponent(ErrorStateComponent);
    fixture.componentRef.setInput('message', 'Network error');
    fixture.detectChanges();
    let retried = 0;
    fixture.componentInstance.retry.subscribe(() => retried++);

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Network error');
    fixture.nativeElement.querySelector('button').click();
    expect(retried).toBe(1);
  });

  it('hides the retry button when retryLabel is empty', () => {
    const fixture = TestBed.createComponent(ErrorStateComponent);
    fixture.componentRef.setInput('retryLabel', '');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});
