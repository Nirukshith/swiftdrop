import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RatingService } from './rating.service';

describe('RatingService', () => {
  let http: HttpTestingController;
  let service: RatingService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(RatingService);
  });

  afterEach(() => http.verify());

  it('reads and submits the rating on the contract URL', () => {
    service.get('p 1').subscribe();
    expect(http.expectOne('/api/v1/parcels/p%201/rating').request.method).toBe('GET');

    service.rate('p-1', { score: 5, comment: 'Great' }).subscribe();
    const post = http.expectOne('/api/v1/parcels/p-1/rating');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ score: 5, comment: 'Great' });
  });
});
