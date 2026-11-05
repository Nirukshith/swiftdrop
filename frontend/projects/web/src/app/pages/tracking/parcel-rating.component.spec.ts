import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParcelRatingComponent } from './parcel-rating.component';
import { RatingResponse } from './tracking.model';

@Component({
  standalone: true,
  imports: [ParcelRatingComponent],
  template: `<app-parcel-rating [parcelId]="id()" />`,
})
class HostComponent {
  readonly id = signal('p-1');
}

const URL = '/api/v1/parcels/p-1/rating';
const SAVED: RatingResponse = { parcelId: 'p-1', score: 4, comment: 'On time', createdAt: '2026-11-04T10:00:00Z' };

describe('ParcelRatingComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const q = (testId: string) => el().querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const star = (n: number) => q(`rating-score-${n}`) as HTMLInputElement;
  const textarea = () => el().querySelector('textarea') as HTMLTextAreaElement;
  const submitButton = () => q('rating-submit')?.querySelector('button') as HTMLButtonElement;

  function notRatedYet(): void {
    http.expectOne({ method: 'GET', url: URL }).flush({ code: 'RESOURCE_NOT_FOUND', message: 'No rating' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
  }

  function type(text: string): void {
    textarea().value = text;
    textarea().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    (q('rating-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function expectPost(): TestRequest {
    return http.expectOne({ method: 'POST', url: URL });
  }

  it('shows a loading state, then the form when the parcel is not rated yet', () => {
    expect(el().textContent).toContain('Loading your rating');

    notRatedYet();

    expect(q('rating-form')).not.toBeNull();
    expect(el().querySelectorAll('input[type="radio"]')).toHaveLength(5);
    expect(el().textContent).toContain('How was your delivery?');
    expect(el().textContent).toContain('0 / 500');
  });

  it('shows the stored rating instead of the form when one exists', () => {
    http.expectOne(URL).flush(SAVED);
    fixture.detectChanges();

    expect(q('rating-form')).toBeNull();
    expect(q('rating-stars')?.getAttribute('aria-label')).toBe('4 out of 5 stars');
    expect(q('rating-stars')?.textContent?.replace(/\s/g, '')).toBe('★★★★☆');
    expect(q('rating-comment')?.textContent).toBe('On time');
  });

  it('highlights the chosen score', () => {
    notRatedYet();

    star(3).dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const labels = Array.from(el().querySelectorAll('[role="radiogroup"] label span[aria-hidden="true"]')).map((s) => s.textContent?.trim());
    expect(labels).toEqual(['★', '★', '★', '☆', '☆']);
  });

  it('asks for a score before sending anything', () => {
    notRatedYet();

    submit();

    expect(q('rating-score-error')?.textContent).toContain('Choose a score from 1 to 5');
    http.expectNone({ method: 'POST', url: URL });
  });

  it('does not send a comment longer than 500 characters', () => {
    notRatedYet();
    star(5).dispatchEvent(new Event('change'));
    type('x'.repeat(501));

    submit();

    expect(el().textContent).toContain('Keep the comment to 500 characters or fewer.');
    http.expectNone({ method: 'POST', url: URL });
  });

  it('submits the score and trimmed comment, then shows the saved rating', () => {
    notRatedYet();
    star(5).dispatchEvent(new Event('change'));
    type('  Courier was polite  ');

    submit();

    const post = expectPost();
    expect(post.request.body).toEqual({ score: 5, comment: 'Courier was polite' });
    post.flush({ parcelId: 'p-1', score: 5, comment: 'Courier was polite', createdAt: '2026-11-05T10:00:00Z' }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(q('rating-form')).toBeNull();
    expect(q('rating-saved')?.textContent).toContain('Thank you for your rating');
    expect(q('rating-comment')?.textContent).toBe('Courier was polite');
  });

  it('sends an empty comment as null', () => {
    notRatedYet();
    star(2).dispatchEvent(new Event('change'));
    type('   ');

    submit();

    expect(expectPost().request.body).toEqual({ score: 2, comment: null });
  });

  it('sends only one request while saving, even if submitted twice', () => {
    notRatedYet();
    star(4).dispatchEvent(new Event('change'));

    submit();
    submit();

    const posts = http.match({ method: 'POST', url: URL });
    expect(posts).toHaveLength(1);
    expect(textarea().disabled).toBe(true);
    expect(star(1).disabled).toBe(true);
    posts[0].flush(SAVED, { status: 201, statusText: 'Created' });
  });

  it('keeps the form with a message when saving fails, and allows another try', () => {
    notRatedYet();
    star(4).dispatchEvent(new Event('change'));

    submit();
    expectPost().flush({ code: 'INTERNAL_SERVER_ERROR', message: 'boom' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(q('rating-error')?.textContent).toContain('could not be saved');
    expect(q('rating-form')).not.toBeNull();
    expect(textarea().disabled).toBe(false);

    submit();
    expectPost().flush(SAVED, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    expect(q('rating-saved')).not.toBeNull();
  });

  it('shows the server validation message for a 400', () => {
    notRatedYet();
    star(4).dispatchEvent(new Event('change'));

    submit();
    expectPost().flush({ code: 'VALIDATION_FAILED', message: 'score must be between 1 and 5' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(q('rating-error')?.textContent).toBe('score must be between 1 and 5');
  });

  it('when the parcel was already rated elsewhere, loads and shows the stored rating', () => {
    notRatedYet();
    star(1).dispatchEvent(new Event('change'));

    submit();
    expectPost().flush({ code: 'RATING_ALREADY_EXISTS', message: 'already rated' }, { status: 409, statusText: 'Conflict' });
    http.expectOne({ method: 'GET', url: URL }).flush(SAVED);
    fixture.detectChanges();

    expect(q('rating-form')).toBeNull();
    expect(q('rating-saved')?.textContent).toContain('This parcel has already been rated');
    expect(q('rating-stars')?.getAttribute('aria-label')).toBe('4 out of 5 stars');
  });

  it('explains a not-delivered or forbidden answer', () => {
    notRatedYet();
    star(5).dispatchEvent(new Event('change'));

    submit();
    expectPost().flush({ code: 'PARCEL_NOT_DELIVERED', message: 'not delivered' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(q('rating-error')?.textContent).toBe('Only delivered parcels can be rated.');

    submit();
    expectPost().flush({ code: 'ACCESS_DENIED', message: 'no' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(q('rating-error')?.textContent).toBe('Only the customer who sent this parcel can rate it.');
  });

  it('shows no form to someone who may not rate this parcel', () => {
    http.expectOne(URL).flush({ code: 'ACCESS_DENIED', message: 'no' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(q('rating-form')).toBeNull();
    expect(q('rating-unavailable')?.textContent).toContain('Only the customer who sent this parcel can rate it');
  });

  it('shows a retryable error when the rating cannot be loaded', () => {
    http.expectOne(URL).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el().textContent).toContain('Could not load the rating');

    (el().querySelector('[role="alert"] button') as HTMLButtonElement).click();
    http.expectOne(URL).flush(SAVED);
    fixture.detectChanges();
    expect(q('rating-saved')).not.toBeNull();
  });

  it('loads the rating of the new parcel when the parcel changes', () => {
    http.expectOne(URL).flush(SAVED);
    fixture.detectChanges();

    fixture.componentInstance.id.set('p-2');
    fixture.detectChanges();

    http.expectOne('/api/v1/parcels/p-2/rating').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(q('rating-form')).not.toBeNull();
  });
});
