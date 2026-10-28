# SwiftDrop UI kit (`ui`)

Shared Angular components for every SwiftDrop portal. Standalone components, signals, Tailwind
design tokens, and no extra runtime dependencies. Owner: M2.

```ts
import { ButtonComponent, CardComponent, TableComponent } from 'ui';
```

The `ui` path is already mapped in `frontend/tsconfig.json` and `jest.config.js`, so the web app
and its tests use the library source directly. `ng build ui` produces the packaged library in
`dist/ui`.

## Contents

| Area | Exports |
| :--- | :--- |
| Layout | `ShellComponent` (`ui-shell`), `CardComponent` (`ui-card`), `TabsComponent` / `TabComponent` (`ui-tabs`, `ui-tab`) |
| Actions & feedback | `ButtonComponent`, `SpinnerComponent`, `DialogComponent`, `ToastService` + `ToastContainerComponent`, `StatusBadgeComponent` |
| States | `LoadingStateComponent`, `EmptyStateComponent`, `ErrorStateComponent` |
| Forms | `InputComponent`, `SelectComponent`, `TextareaComponent`, `validationMessage()` |
| Data | `TableComponent` + `TableCellDirective`, `PaginationComponent` |

## Design tokens

Defined once in `frontend/tailwind.config.js`. Use the token names, not raw palette colours, so the
look can change in one place.

| Token | Classes | Use for |
| :--- | :--- | :--- |
| `brand` | `bg-brand-600`, `text-brand-700`, ... (indigo) | Primary actions, links, active nav |
| `success` / `warning` / `danger` / `info` | `bg-success-50`, `text-danger-600`, ... | Status and feedback |
| `neutral` | `bg-neutral-100`, ... (slate) | Neutral badges, overlays |
| `surface` | `bg-surface`, `bg-surface-muted`, `border-surface-border` | Cards, page background, borders |
| `ink` | `text-ink`, `text-ink-muted`, `text-ink-subtle` | Body, secondary and placeholder text |
| radius | `rounded-control`, `rounded-card` | Inputs and buttons, cards and dialogs |
| shadow | `shadow-card`, `shadow-overlay` | Cards, dialogs and toasts |
| z-index | `z-overlay`, `z-toast` | Dialogs, toasts |

## Usage

### Layout shell

```ts
nav: ShellNavItem[] = [
  { label: 'Dashboard', link: '/customer/dashboard' },
  { label: 'My parcels', link: '/customer/parcels' },
];
```

```html
<ui-shell pageTitle="My parcels" [navItems]="nav">
  <ui-button uiShellActions variant="ghost" (click)="logout()">Log out</ui-button>
  <!-- page content -->
</ui-shell>
```

Below the `md` breakpoint the navigation collapses into a menu button (NF-04, 360 px).

### Card

```html
<ui-card title="Recent parcels" subtitle="Last 30 days" [padded]="false">
  <ui-button uiCardActions variant="ghost" size="sm">View all</ui-button>
  <ui-table [columns]="columns" [rows]="rows" />
  <span uiCardFooter>Updated just now</span>
</ui-card>
```

### Buttons

```html
<ui-button (click)="save()">Save</ui-button>
<ui-button variant="secondary">Cancel</ui-button>
<ui-button variant="danger" [loading]="deleting()">Delete</ui-button>
<ui-button type="submit" [fullWidth]="true">Create parcel</ui-button>
```

Variants: `primary`, `secondary`, `danger`, `ghost`; sizes `sm`, `md`. No click is emitted while
`disabled` or `loading`.

### Form controls with validation messages

```ts
form = new FormGroup({
  recipientName: new FormControl('', Validators.required),
  weightKg: new FormControl<number | null>(null, [Validators.required, Validators.min(0.1), Validators.max(50)]),
  size: new FormControl<string | null>(null, Validators.required),
  notes: new FormControl('', Validators.maxLength(500)),
});
sizes: SelectOption[] = [
  { value: 'SMALL', label: 'Small' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LARGE', label: 'Large' },
];
```

```html
<form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
  <ui-input label="Recipient name" formControlName="recipientName" autocomplete="name" />
  <ui-input label="Weight (kg)" type="number" formControlName="weightKg" hint="0.1 to 50 kg" />
  <ui-select label="Size" [options]="sizes" formControlName="size" />
  <ui-textarea label="Notes" formControlName="notes" [messages]="{ maxlength: 'Keep notes under 500 characters' }" />
  <ui-button type="submit">Continue</ui-button>
</form>
```

- Works with `formControlName`, `[formControl]` and `ngModel`; no providers needed.
- Messages appear after the field is touched or changed. Call `form.markAllAsTouched()` on submit
  to show all of them.
- A required validator adds `*` to the label and `aria-required`; errors set `aria-invalid` and
  `aria-describedby`.
- `type="number"` gives the form a number (or `null`); `ui-select` keeps the option value's type.
- Messages come from `validationMessage(errors, label, overrides)`. Custom validators can return a
  string, e.g. `{ district: 'Choose a supported district' }`, and it is shown as is.

### Table and pagination

```ts
columns: TableColumn<ParcelResponse>[] = [
  { key: 'trackingNumber', header: 'Tracking no.' },
  { key: 'createdAt', header: 'Created', value: (p) => new Date(p.createdAt).toLocaleDateString() },
  { key: 'fee', header: 'Fee (LKR)', align: 'right' },
  { key: 'status', header: 'Status' },
];
```

```html
<ui-table
  [columns]="columns"
  [rows]="page()?.content ?? []"
  [loading]="loading()"
  [clickable]="true"
  [rowKey]="byId"
  emptyTitle="No parcels yet"
  (rowClick)="open($event)"
>
  <ng-template uiCell="status" let-parcel>
    <ui-status-badge [status]="parcel.status" />
  </ng-template>
</ui-table>

<ui-pagination
  [page]="page()?.page ?? 0"
  [size]="page()?.size ?? 20"
  [totalElements]="page()?.totalElements ?? 0"
  (pageChange)="load($event)"
/>
```

Pagination is 0-based, matching `ParcelPageResponse` (`page`, `size`, `totalElements`).

### Tabs

```html
<ui-tabs [(selectedIndex)]="tab" ariaLabel="Parcel filters">
  <ui-tab label="Active">…</ui-tab>
  <ui-tab label="Delivered">…</ui-tab>
</ui-tabs>
```

Arrow keys, Home and End move between tabs.

### Dialog

```html
<ui-button variant="danger" (click)="confirmOpen.set(true)">Cancel parcel</ui-button>

<ui-dialog [(open)]="confirmOpen" title="Cancel this parcel?" size="sm">
  <p>The courier will no longer collect it.</p>
  <ng-container uiDialogFooter>
    <ui-button variant="secondary" (click)="confirmOpen.set(false)">Keep</ui-button>
    <ui-button variant="danger" (click)="cancelParcel()">Cancel parcel</ui-button>
  </ng-container>
</ui-dialog>
```

Bind `[(open)]` to a signal. Escape and the backdrop close it unless `[dismissible]="false"`;
`(closed)` fires on every close.

### Toasts

Add the container once in the app shell:

```html
<ui-toast-container />
```

```ts
private readonly toast = inject(ToastService);

this.toast.success('Parcel created');
this.toast.error('Could not load parcels', 0); // 0 = stays until dismissed
```

### Status badge

```html
<ui-status-badge status="OUT_FOR_DELIVERY" />            <!-- "Out for delivery", warning tone -->
<ui-status-badge status="PENDING" tone="warning" />       <!-- non-parcel statuses pass a tone -->
<ui-status-badge status="APPROVED" tone="success" label="Approved" />
```

Parcel statuses from `contracts/openapi/parcel.yaml` get their tone automatically.

### Loading, empty and error states

```html
@if (loading()) {
  <ui-loading-state message="Loading parcels…" />
} @else if (error()) {
  <ui-error-state message="Check your connection." (retry)="reload()" />
} @else if (!parcels().length) {
  <ui-empty-state title="No parcels yet" message="Create your first delivery.">
    <ui-button routerLink="/customer">New delivery</ui-button>
  </ui-empty-state>
}
```

## Development

```bash
cd frontend
npx jest projects/ui      # kit tests only
npm test -- --ci          # all frontend tests
npx ng build ui           # package the library into dist/ui
```

Each component has a Jest spec next to it. New components need a spec, an export in
`src/public-api.ts`, and an example here.
