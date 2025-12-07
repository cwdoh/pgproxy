

## Build and Run Commands

Navigate to the project's root directory in the terminal.

### Build the Project.

This command downloads dependencies and compiles the source code.

```bash
./gradlew build
```

This command generates an executable JAR file in the build/libs/ directory (e.g., build/libs/pgproxy-0.0.1-SNAPSHOT.jar).

### Run the Application (Executable JAR)

You can run the generated JAR directly using the standard Java command.

```bash
java -jar build/libs/pgproxy-0.0.1-SNAPSHOT.jar
```

> Note: Replace pgproxy-0.0.1-SNAPSHOT.jar with if actual generated file name is changed.

## Run the Application on Development Mode
For quick development and testing without explicitly building the JAR, use the bootRun task provided by the Spring Boot plugin:

```bash
./gradlew bootRun
```

This will start the application, and you should see logs indicating that the server is running on the default port, 8081.

## Verification
Open web browser or use a tool like curl and hit the endpoint:

```bash
curl http://localhost:8081/actuator/health
```

Expected Output:

```
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

## For running with backend

The following command will run the backend at port 8080.

```
docker run --rm -p 8080:8080 -it public.ecr.aws/b5s9k2b6/nut-2025-1:server-latest
```
> source from: https://gallery.ecr.aws/b5s9k2b6/nut-2025-1
