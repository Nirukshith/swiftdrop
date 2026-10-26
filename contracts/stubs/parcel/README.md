# Parcel Service WireMock Stub

This WireMock stub mocks the internal endpoints of `parcel-service` (Port 8082) so that downstream dependent services (`operations-service` for M4 dispatch and M8/M9 courier execution, and `integration-service` for M6 merchant order creation) can develop and test against the contract independently without running the actual service.

## Run the Stub

From the repository root, run the following one-line command:

```bash
docker run --rm -p 8082:8080 -v "${PWD}/contracts/stubs/parcel:/home/wiremock" wiremock/wiremock:3.9.1
```

*(On Windows PowerShell, use `${PWD}` or `$(Get-Location)`)*

## Supported Mock Endpoints

- `PUT /internal/v1/parcels/{id}/assignment` &rarr; Assign courier to parcel (returns updated `ParcelResponse`)
- `PATCH /internal/v1/parcels/{id}/status` &rarr; Step-by-step courier status advancement (returns updated `ParcelResponse`)
- `GET /internal/v1/parcels?unassigned=true` &rarr; Returns unassigned parcel queue for dispatcher (M4)
- `GET /internal/v1/parcels?courierId={courierId}&date={date}` &rarr; Returns assigned parcels for courier daily delivery list (M8)
- `POST /internal/v1/parcels` &rarr; Internal merchant order creation (returns 201 Created `ParcelResponse` for M6)
