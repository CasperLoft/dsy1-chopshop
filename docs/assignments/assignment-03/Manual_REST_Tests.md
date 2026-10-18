# Manual REST testing with Kreya

**Endpoint:** `http://localhost:8081/registrations`  
**Mode:** REST over HTTP with JSON request and response bodies  
**Database:** local `registration-postgres` PostgreSQL Docker Compose container

| Test | Method | Request | Expected result | Actual result | Status |
|---|---|---|---|---|---|
| Register animal | `POST /registrations` | `earTag = DK-A10111`, `arrivalDate = 2026-10-18`, `weight = 525.5`, `origin = Test Farm` | `201 Created`; generated UUID and submitted registration data | Returned `201 Created` with a generated UUID and matching fields | Pass |
| Registration lookup | `GET /registrations/{earTag}` | `earTag = DK-A1001` | The stored registration for `DK-A1001` | Matched expected registration | Pass |
| Registrations by arrival date | `GET /registrations?arrivalDate=...` | `arrivalDate = 2026-10-18` | All registrations arriving on that date | Returned matching registrations, including the manually registered and seed data | Pass |
| Registrations by origin | `GET /registrations?origin=...` | `origin = Peperridge Farm` | All registrations from `Peperridge Farm` | Matched expected registrations | Pass |

The successful registration and arrival-date lookup are captured in `manual_test01.png` and `manual_test02.png` in this directory.

## Automated error coverage

`RegistrationServiceIntegrationTest` uses Testcontainers with a temporary PostgreSQL container. Its six tests cover the four successful REST flows above, plus:

| Test | Expected result | Status |
|---|---|---|
| Unknown ear tag | `404 Not Found` | Pass |
| Duplicate ear tag | `409 Conflict` | Pass |
