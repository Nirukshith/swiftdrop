import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ButtonComponent, CardComponent, ErrorStateComponent, LoadingStateComponent, TextareaComponent } from 'ui';
import { RatingService } from './rating.service';
import { RATING_COMMENT_MAX, RATING_SCORES, RatingResponse } from './tracking.model';

type RatingState = 'loading' | 'form' | 'rated' | 'unavailable' | 'load-error';

/**
 * Delivery rating card for a delivered parcel (CU-07, docs/tracking-design.md section 9).
 * Shows the stored rating when there is one, otherwise a 1-5 score and optional comment form.
 * The server decides who may rate; this card only reflects its answers.
 */
@Component({
  selector: 'app-parcel-rating',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, CardComponent, ButtonComponent, TextareaComponent, LoadingStateComponent, ErrorStateComponent],
  template: `
    <ui-card title="Rate this delivery" data-testid="rating-card">
      @switch (state()) {
        @case ('loading') {
          <ui-loading-state message="Loading your rating…" />
        }
        @case ('load-error') {
          <ui-error-state title="Could not load the rating" [message]="error()" (retry)="load()" />
        }
        @case ('unavailable') {
          <p class="text-sm text-ink-muted" data-testid="rating-unavailable">{{ error() }}</p>
        }
        @case ('rated') {
          @if (rating(); as r) {
            <div data-testid="rating-saved">
              <p class="text-sm text-ink-muted">{{ notice() || 'Your rating' }}</p>
              <p class="mt-1 text-2xl text-warning-500" [attr.aria-label]="r.score + ' out of 5 stars'" data-testid="rating-stars">
                @for (s of scores; track s) {
                  <span aria-hidden="true">{{ s <= r.score ? '★' : '☆' }}</span>
                }
              </p>
              @if (r.comment) {
                <p class="mt-2 whitespace-pre-line break-words text-sm text-ink" data-testid="rating-comment">{{ r.comment }}</p>
              }
              <p class="mt-2 text-xs text-ink-muted">Rated {{ r.createdAt | date: 'medium' }}</p>
            </div>
          }
        }
        @default {
          <form (submit)="$event.preventDefault(); submit()" novalidate data-testid="rating-form">
            <fieldset [attr.aria-describedby]="scoreError() ? 'rating-score-error' : null">
              <legend class="text-sm font-medium text-ink">How was your delivery?</legend>
              <div class="mt-2 flex gap-1" role="radiogroup">
                @for (s of scores; track s) {
                  <label class="cursor-pointer">
                    <input
                      type="radio"
                      name="rating-score"
                      class="peer sr-only"
                      [value]="s"
                      [checked]="score() === s"
                      [disabled]="submitting()"
                      (change)="choose(s)"
                      [attr.data-testid]="'rating-score-' + s"
                    />
                    <span
                      class="block rounded px-1 text-3xl leading-none peer-focus-visible:ring-2 peer-focus-visible:ring-brand-500"
                      [class.text-warning-500]="(score() ?? 0) >= s"
                      [class.text-ink-subtle]="(score() ?? 0) < s"
                      aria-hidden="true"
                    >{{ (score() ?? 0) >= s ? '★' : '☆' }}</span>
                    <span class="sr-only">{{ s }} {{ s === 1 ? 'star' : 'stars' }}</span>
                  </label>
                }
              </div>
              @if (scoreError()) {
                <p id="rating-score-error" class="mt-1 text-xs text-danger-600" data-testid="rating-score-error">Choose a score from 1 to 5.</p>
              }
            </fieldset>

            <ui-textarea
              class="mt-4"
              inputId="rating-comment"
              label="Comment (optional)"
              [hint]="commentHint()"
              [rows]="3"
              [formControl]="comment"
              [messages]="{ maxlength: 'Keep the comment to ' + maxComment + ' characters or fewer.' }"
            />

            @if (error()) {
              <p class="mt-3 text-sm text-danger-700" role="alert" data-testid="rating-error">{{ error() }}</p>
            }

            <div class="mt-4">
              <ui-button type="submit" [loading]="submitting()" data-testid="rating-submit">Submit rating</ui-button>
            </div>
          </form>
        }
      }
    </ui-card>
  `,
})
export class ParcelRatingComponent {
  private readonly ratings = inject(RatingService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;

  readonly parcelId = input.required<string>();

  protected readonly scores = RATING_SCORES;
  protected readonly maxComment = RATING_COMMENT_MAX;
  readonly comment = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(RATING_COMMENT_MAX)] });

  readonly state = signal<RatingState>('loading');
  readonly rating = signal<RatingResponse | null>(null);
  readonly score = signal<number | null>(null);
  readonly scoreError = signal(false);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly commentLength = signal(0);

  constructor() {
    this.comment.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => this.commentLength.set(value.length));
    effect(() => {
      this.parcelId();
      untracked(() => this.load());
    });
  }

  protected commentHint(): string {
    return `${this.commentLength()} / ${RATING_COMMENT_MAX}`;
  }

  load(): void {
    this.request?.unsubscribe();
    this.state.set('loading');
    this.error.set('');
    this.notice.set('');
    this.request = this.ratings
      .get(this.parcelId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rating) => this.showRating(rating),
        error: (err: unknown) => {
          const status = err instanceof HttpErrorResponse ? err.status : -1;
          if (status === 404) {
            this.state.set('form'); // not rated yet
          } else if (status === 403) {
            this.error.set('Only the customer who sent this parcel can rate it.');
            this.state.set('unavailable');
          } else {
            this.error.set(loadErrorMessage(status));
            this.state.set('load-error');
          }
        },
      });
  }

  choose(score: number): void {
    this.score.set(score);
    this.scoreError.set(false);
  }

  submit(): void {
    if (this.submitting()) {
      return; // one request at a time
    }
    const score = this.score();
    this.error.set('');
    this.scoreError.set(score === null);
    this.comment.markAsTouched();
    if (score === null || this.comment.invalid) {
      return;
    }
    const comment = this.comment.value.trim();
    this.submitting.set(true);
    this.comment.disable({ emitEvent: false });
    this.ratings
      .rate(this.parcelId(), { score, comment: comment || null })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rating) => {
          this.submitting.set(false);
          this.notice.set('Thank you for your rating');
          this.showRating(rating);
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.comment.enable({ emitEvent: false });
          this.handleSubmitError(err);
        },
      });
  }

  private showRating(rating: RatingResponse): void {
    this.rating.set(rating);
    this.state.set('rated');
  }

  private handleSubmitError(err: unknown): void {
    const http = err instanceof HttpErrorResponse ? err : null;
    const body = (http?.error ?? null) as { code?: unknown; message?: unknown } | null;
    const code = body && typeof body.code === 'string' ? body.code : '';
    if (code === 'RATING_ALREADY_EXISTS') {
      // Rated already (e.g. in another tab): show the stored rating instead of the form.
      this.load();
      this.notice.set('This parcel has already been rated');
      return;
    }
    switch (http?.status) {
      case 0:
        this.error.set('Cannot reach the server. Check your connection and try again.');
        return;
      case 400:
        this.error.set(typeof body?.message === 'string' && body.message ? body.message : 'Please check your rating and try again.');
        return;
      case 401:
        this.error.set('Your session has ended. Please sign in again.');
        return;
      case 403:
        this.error.set('Only the customer who sent this parcel can rate it.');
        return;
      case 404:
        this.error.set('This parcel could not be found.');
        return;
      case 409:
        this.error.set('Only delivered parcels can be rated.');
        return;
      default:
        this.error.set('Your rating could not be saved. Please try again.');
    }
  }
}

function loadErrorMessage(status: number): string {
  if (status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  if (status === 401) {
    return 'Your session has ended. Please sign in again.';
  }
  return 'Please try again in a moment.';
}
