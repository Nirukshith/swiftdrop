# Pricing WireMock stub

Run the stub from the repository root with:

```text
docker run --rm -p 8089:8080 -v "${PWD}/contracts/stubs/pricing:/home/wiremock" wiremock/wiremock:3.9.1
```

The mapping returns a fixed quote for `GET /internal/v1/pricing/quote` so
parcel-service consumers can develop against the contract before operations-service
pricing logic is available.
