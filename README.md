# Conference Room Reservation API

A Spring Boot REST API for managing conference rooms and their reservations. Active reservations of
the same room can never overlap, and that holds when requests arrive at the same instant.

## Requirements

- Java 21
- Docker, with Docker Compose

Maven does not need to be installed: the repository ships the Maven Wrapper (`./mvnw`). Docker is
also required to run the tests, which start a real PostgreSQL container through Testcontainers.

## Running the application

Everything in containers:

```bash
docker compose up --build
```

Database in Docker, application from Maven:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. Swagger UI is at
`http://localhost:8080/swagger-ui.html` and the OpenAPI document at
`http://localhost:8080/v3/api-docs`.

## Running the tests

```bash
./mvnw verify
```

The suite covers the domain in isolation, the HTTP layer, the PostgreSQL constraints, and real
concurrency - twenty simultaneous requests for one slot.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/rooms` | Create a conference room |
| `GET` | `/api/v1/rooms` | List all conference rooms |
| `GET` | `/api/v1/rooms/{roomId}` | Get a conference room |
| `POST` | `/api/v1/rooms/{roomId}/reservations` | Create a reservation |
| `GET` | `/api/v1/rooms/{roomId}/reservations` | List and optionally filter reservations |
| `DELETE` | `/api/v1/reservations/{reservationId}` | Cancel a reservation |

The listing accepts three optional query parameters: `from` and `to` select the reservations
intersecting that window, and `status` narrows the result to `ACTIVE` or `CANCELLED`.

## Reservation rules

A reservation is refused unless all of these hold:

- the room exists,
- `endTime` is after `startTime`,
- the interval does not overlap an active reservation of the same room.

Intervals are half-open, `[start, end)`, so a booking of `10:00-11:00` and one of `11:00-12:00` are
both accepted: the room is free again at 11:00. Timestamps are ISO 8601 with an offset, for example
`2026-09-10T09:00:00+02:00`, and are compared as instants, so the same moment written in two zones
is one moment.

The rule is enforced twice. The service queries for an overlap before writing, which is what
produces the readable `409` for almost every rejection. Behind it, a PostgreSQL exclusion constraint
on the same condition rejects the write itself, which is what settles two transactions racing for
the same slot - and it holds for writes that never go through the application.

## Error responses

Errors are returned as `application/problem+json` (RFC 9457). The `type` URI is stable and safe to
branch on; the `detail` text is not part of the contract.

```json
{
  "type": "/problems/reservation-overlap",
  "title": "Reservation time conflict",
  "status": 409,
  "detail": "Room 12 is already booked between 2026-09-10T09:00Z and 2026-09-10T10:00Z"
}
```

## Additional features

- cancelling a reservation
- filtering reservations by date window and status
- Swagger UI and an OpenAPI document
- Docker image and Compose setup
- optimistic locking, which protects updates of an existing reservation
- a database-level guarantee against two overlapping reservations being created at once

## Assumptions and limitations

- No authentication, no pagination, and no editing of an existing reservation.
- Cancelling is idempotent: it sets the status to `CANCELLED` rather than deleting the row, so the
  reservation stays as an audit trail while its slot becomes bookable again.
- The listing returns both active and cancelled reservations; `status` is how you narrow it.
- Nothing stops a reservation being created in the past. Enforcing it would mean comparing
  `startTime` against an injected `Clock` in the service.
