# M1 Week 8 Customer Parcel Workflow Demo

## Objective

Demonstrate the authenticated customer workflow using the real Angular frontend,
gateway, parcel-service, PostgreSQL, Kafka, and approved downstream services:

1. Customer registration and login.
2. Address-book CRUD and saved-address selection.
3. Fee preview and parcel creation.
4. Customer parcel listing and details.
5. Courier assignment and lifecycle status transitions.
6. Customer cancellation of an eligible parcel.
7. Post-commit Kafka event publication.

The demo must use temporary local data only. Do not use production credentials,
tokens, customer data, or manually persisted secrets.

## Required prerequisites

- Docker Desktop with at least 8 GB of available memory.
- Java 21 and Maven available for backend verification.
- Node 22 and npm available for frontend verification.
- A working local checkout on the latest `development` branch.
- Approved operations-service zone and pricing-rule data for the districts used
  by the demo. M5 owns this prerequisite; do not insert directly into
  `ops_db` from the parcel workflow.
- Demo identities supplied through the approved auth workflow:
  - one `CUSTOMER`;
  - one `DISPATCHER`;
  - one eligible `COURIER`.

Use environment variables or a local ignored `.env` file for credentials.
Never commit values for `POSTGRES_PASSWORD`, JWT keys, OAuth secrets, or
service tokens.

## Start the local stack

From the repository root:

```powershell
docker compose -f deploy\docker-compose.yml --env-file deploy\.env --profile full up -d --build
```

Verify service health:

```powershell
docker compose -f deploy\docker-compose.yml --env-file deploy\.env ps
```

Expected core endpoints:

```text
http://localhost:8080       Gateway
http://localhost:8081       Auth service
http://localhost:8082       Parcel service
http://localhost:8083       Operations service
http://localhost:8084       Integration service
http://localhost:8025       Mailpit
```

The full profile is required for notification verification. If the
integration-service image cannot be built because Maven Central is unavailable,
the parcel-service workflow may be tested with the core services, but downstream
notification verification must be marked **BLOCKED**.

## Verify prerequisites

Check backend health:

```powershell
$ports = 8081,8082,8083,8084
foreach ($port in $ports) {
  Invoke-RestMethod "http://localhost:$port/actuator/health"
}
```

Confirm that M5 has configured active zones covering the selected pickup and
drop-off districts and active weight-band and size pricing rules. Verify this
through the approved operations API or dispatcher UI. Do not bypass ownership
boundaries with direct SQL writes.

## Demo sequence

### 1. Customer authentication

1. Open the frontend through the gateway.
2. Register or log in with the temporary customer account.
3. Confirm the customer route guard opens the customer portal.
4. Confirm no access token is written to `localStorage` or `sessionStorage`.

### 2. Address book

1. Open **Address Book**.
2. Add a structured address with a valid district.
3. Refresh the page and confirm the address persists.
4. Edit the address and set it as the default.
5. Delete only a temporary address that is not needed by a parcel.
6. Select the saved address in the parcel wizard.
7. Confirm another customer cannot access the address by changing its ID.

### 3. Parcel creation

1. Open **New Delivery**.
2. Select or enter valid pickup and drop-off addresses.
3. Enter recipient details, weight, and size.
4. Confirm the fee preview is returned by operations-service.
5. Submit once and wait for the success response.
6. Record the returned tracking number.
7. Confirm the parcel appears in **My parcels**.
8. Open the parcel details page and confirm the persisted status is `CREATED`.

Verify persistence without exposing credentials:

```powershell
docker exec deploy-postgres-1 psql -U $env:POSTGRES_USER -d parcel_db `
  -c "select id, tracking_number, status, customer_id, created_at from parcels order by created_at desc limit 5;"
```

### 4. Assignment

1. Log in using the approved dispatcher identity.
2. Open the unassigned parcel queue.
3. Assign an eligible courier.
4. Confirm the assignment response and parcel details.
5. Confirm the assignment event is emitted on `parcel.assigned.v1`.

### 5. Status transition and tracking

Using the approved courier or dispatcher workflow, perform only valid transitions:

```text
CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
```

After each transition:

1. Confirm the API response.
2. Confirm the persisted status and status history.
3. Confirm the customer detail page shows the latest committed state.
4. Confirm a valid `parcel.status-changed.v1` event contains the parcel ID,
   previous status, new status, sequence, and event ID.

Attempt one invalid transition and confirm it returns the approved conflict
response without publishing a successful status event.

### 6. Cancellation

Use a separate parcel that remains in `CREATED`:

1. Log in as the owning customer.
2. Cancel the parcel through the customer workflow.
3. Confirm the status becomes `CANCELLED`.
4. Refresh the list and details pages.
5. Confirm a second cancellation is handled consistently.
6. Confirm cancellation of another customer's parcel is rejected.
7. Confirm cancellation after pickup is rejected.

### 7. Event and downstream verification

For each successful lifecycle change, verify the event topic and consumer
behavior using the repository's approved Kafka tooling. Check Mailpit at
`http://localhost:8025` if the integration-service notification flow is part of
the demo.

Do not claim exactly-once Kafka delivery. Report logical event deduplication and
transport behavior separately.

## Negative cases to demonstrate

- Missing or invalid parcel fields.
- Unauthenticated parcel access.
- Access to another customer's parcel.
- Invalid status transition.
- Cancellation after pickup.
- Repeated cancellation.
- Invalid pricing or unserved district.

Record the HTTP status and sanitized error code/message for each case. Do not
record access tokens, passwords, phone numbers, or private addresses.

## Troubleshooting

| Symptom | Action |
|---|---|
| Pricing returns `PRICING_RULE_NOT_FOUND` | Stop the demo and request M5 to configure approved zones and active pricing rules. Do not write directly to `ops_db`. |
| Customer creation returns `401` from pricing | Confirm the merged internal pricing token configuration in both services and check operations-service logs. |
| Gateway returns `401` | Re-authenticate and confirm the shared-auth interceptor has an in-memory access token. |
| Parcel list is empty | Confirm the authenticated customer owns the created parcel and inspect the customer ID from the token, not a request field. |
| Kafka event is absent after a failed request | Confirm the transaction rolled back; failed operations must not publish a success event. |
| Integration-service cannot build | Record the Maven dependency-download failure and mark downstream notification verification blocked. |
| A parcel is already terminal | Create a separate temporary parcel; do not mutate database state manually. |

## Cleanup

After the demo:

```powershell
docker compose -f deploy\docker-compose.yml --env-file deploy\.env --profile full down
```

Remove only temporary local demo data through the approved service workflows.
Do not commit generated tokens, credentials, database dumps, or exported
personal data.

## Current Week 8 readiness

As of the initial Week 8 verification:

- Backend parcel-service verification: PASS.
- Frontend tests and production build: PASS.
- Core Docker service health: PASS.
- Full stack image build: BLOCKED by a truncated Maven Central download for
  `org.testcontainers:testcontainers:1.20.5` while building integration-service.
- Customer parcel creation: BLOCKED until M5 supplies approved operations
  zone and pricing-rule data.
- Browser-driven demo: NOT YET VERIFIED.
