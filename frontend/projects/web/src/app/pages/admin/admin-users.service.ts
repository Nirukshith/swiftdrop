import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, Observable, tap, throwError } from 'rxjs';
import {
  AdminUserFilterParams,
  CreateStaffUserRequest,
  UpdateUserStatusRequest,
  UserListPage,
  UserSummary,
} from './admin-users.model';

@Injectable({
  providedIn: 'root',
})
export class AdminUsersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/admin/users';

  // Internal Signals
  private usersSignal = signal<UserSummary[]>([]);
  private totalElementsSignal = signal<number>(0);
  private totalPagesSignal = signal<number>(0);
  private currentPageSignal = signal<number>(0);
  private pageSizeSignal = signal<number>(20);
  private loadingSignal = signal<boolean>(false);
  private errorSignal = signal<string | null>(null);

  // Public Readonly Signals
  public users = this.usersSignal.asReadonly();
  public totalElements = this.totalElementsSignal.asReadonly();
  public totalPages = this.totalPagesSignal.asReadonly();
  public currentPage = this.currentPageSignal.asReadonly();
  public pageSize = this.pageSizeSignal.asReadonly();
  public loading = this.loadingSignal.asReadonly();
  public error = this.errorSignal.asReadonly();

  private lastFilterParams: AdminUserFilterParams = { page: 0, size: 20 };

  /**
   * Loads paginated users with optional role and enabled filters.
   */
  public loadUsers(
    page: number = 0,
    size: number = 20,
    role?: string,
    enabled?: boolean
  ): Observable<UserListPage> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);

    this.lastFilterParams = { page, size, role, enabled };

    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (role && role.trim().length > 0) {
      params = params.set('role', role.trim().toUpperCase());
    }

    if (enabled !== undefined && enabled !== null) {
      params = params.set('enabled', enabled.toString());
    }

    return this.http.get<UserListPage>(this.baseUrl, { params }).pipe(
      tap((response) => {
        this.usersSignal.set(response.content || []);
        this.totalElementsSignal.set(response.totalElements ?? 0);
        this.totalPagesSignal.set(response.totalPages ?? 0);
        this.currentPageSignal.set(response.page ?? page);
        this.pageSizeSignal.set(response.size ?? size);
        this.loadingSignal.set(false);
      }),
      catchError((err) => {
        const errorMsg =
          err?.error?.message ||
          err?.message ||
          'Failed to load users from auth service.';
        this.errorSignal.set(errorMsg);
        this.loadingSignal.set(false);
        return throwError(() => err);
      }),
      finalize(() => {
        this.loadingSignal.set(false);
      })
    );
  }

  /**
   * Provisions a new COURIER or DISPATCHER staff account.
   */
  public createStaffUser(payload: CreateStaffUserRequest): Observable<UserSummary> {
    return this.http.post<UserSummary>(this.baseUrl, payload).pipe(
      tap((created) => {
        // Reconcile against current active filters and pagination
        const matchesRole =
          !this.lastFilterParams.role ||
          this.lastFilterParams.role === created.role;
        const matchesEnabled =
          this.lastFilterParams.enabled === undefined ||
          this.lastFilterParams.enabled === created.enabled;

        if (matchesRole && matchesEnabled && (this.lastFilterParams.page ?? 0) === 0) {
          const pageSize = this.lastFilterParams.size ?? 20;
          this.usersSignal.update((users) => [created, ...users].slice(0, pageSize));
        }
        this.totalElementsSignal.update((total) => total + 1);
      })
    );
  }

  /**
   * Toggles active/enabled status for an existing user account.
   */
  public updateUserStatus(userId: string, enabled: boolean): Observable<UserSummary> {
    const body: UpdateUserStatusRequest = { enabled };
    return this.http.patch<UserSummary>(`${this.baseUrl}/${userId}`, body).pipe(
      tap((updated) => {
        this.usersSignal.update((users) =>
          users.map((u) =>
            u.id === userId
              ? { ...u, enabled: updated.enabled, updatedAt: updated.updatedAt }
              : u
          )
        );
      })
    );
  }

  /**
   * Re-fetches the current page of users using stored filter parameters.
   */
  public refresh(): Observable<UserListPage> {
    const { page = 0, size = 20, role, enabled } = this.lastFilterParams;
    return this.loadUsers(page, size, role, enabled);
  }

  /**
   * Resets all signal states on user logout or session termination.
   */
  public clearState(): void {
    this.usersSignal.set([]);
    this.totalElementsSignal.set(0);
    this.totalPagesSignal.set(0);
    this.currentPageSignal.set(0);
    this.pageSizeSignal.set(20);
    this.loadingSignal.set(false);
    this.errorSignal.set(null);
    this.lastFilterParams = { page: 0, size: 20 };
  }
}

