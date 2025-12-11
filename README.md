# High-Throughput Gateway Proxy: Backpressure Handling

## Goal

The primary goal of this proxy implementation is to **maximize the total amount processed** while gracefully handling backend overload.
This requires implementing a robust strategy for concurrency and prioritization.

## Design Strategy & Ideation

The core challenge lies in managing the bottleneck at the backend dependency which can respond with HTTP status 503 under overload.

### Key Designs:

| Principle        | Description & Implementation                                                                                                                     | Idea                          |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|
| Prioritization   | Prevent dropping requests as many as possible.<br/>Requests are held in a Queue based on the priority by the weighted value  to maximize impact. | PriorityQueue                 |
| Concurrency      | Requests will be forwarded via backpressure handler to scale up and down of concurrency.                                                         | Down = C * 0.9<br/>Up = C + 1 |
| Non-blocking I/O | I/O intensive work is handled efficiently using light thread to be able to scalability.                                                          | Virtual Threads w/Java 21     |
| CPU Isolation    | CPU intensive work is handled by a dedicated worker pool to prevent blocking the main I/O threads.                                               | Dedicated Worker Threads      |

### Handling Backpressure

* The proxy holds requests in the Priority Queue until the Rate Limiter allows forwarding, ensuring the highest value transactions are processed first.
* The backpressure handling strategy is implemented using an interface, making it flexible to be changed like from initial approach to some improved hybrid way.

## Development environment

* Java 21 for supporting vThread
* Spring Boot 3

## Build and Run Commands

Navigate to the project's root directory in the terminal.

### Build the Project.

This command downloads dependencies and compiles the source code.

```bash
./gradlew build
```

This command generates an executable JAR file in the `build/libs/` directory (e.g., `build/libs/pgproxy-0.0.1-SNAPSHOT.jar`).

### Run the Application (Executable JAR)

You can run the generated JAR directly using the standard Java command.

```bash
java -jar build/libs/pgproxy-0.0.1-SNAPSHOT.jar
```

> Note: Replace `pgproxy-0.0.1-SNAPSHOT.jar` with the actual generated file name.

### Run the Application on Development Mode

For quick development and testing without explicitly building the JAR, use the bootRun task provided by the Spring Boot plugin:

```bash
./gradlew bootRun
```

This will start the application, and the server will be running on the default port, 8081.

## Verification

Open web browser or use a tool like `curl` and hit the health endpoint:

```bash
curl http://localhost:8081/actuator/health
```

Expected Output:

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

## Running with the Backend Dependency

The Payment Gateway Backend server must be running on port 8080 for the proxy to function.

```bash
docker run --rm -p 8080:8080 -it public.ecr.aws/b5s9k2b6/nut-2025-1:server-latest
```

> Source from: [https://gallery.ecr.aws/b5s9k2b6/nut-2025-1](https://gallery.ecr.aws/b5s9k2b6/nut-2025-1)
