# conference-room-reservation

REST API for booking conference rooms: rooms, reservations, and a booking rule that refuses
overlapping bookings of the same room.

## Requirements

- Docker (the database runs in a container and the tests use Testcontainers)
- Java 21, for the Maven-based options only

## Running

```bash
./mvnw spring-boot:run
```

The database container starts automatically from `compose.yaml`.

## Tests

```bash
./mvnw verify
```
