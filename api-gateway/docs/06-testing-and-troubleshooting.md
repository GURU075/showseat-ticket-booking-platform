# 6. Running, Testing, and Troubleshooting

## Test in layers

Do not start by testing everything simultaneously. Verify each boundary separately:

1. Event Service starts.
2. Event Service works directly.
3. Gateway starts.
4. The same operation works through Gateway.
5. An unrelated path does not route.

This tells you which application owns a failure.

## Step 1: Start Event Service

Open one PowerShell terminal:

```powershell
cd D:\Personal\showseat-ticket-booking-platform\event-service
.\mvnw.cmd spring-boot:run
```

Event Service requires its configured PostgreSQL database. A database startup failure
must be resolved before an endpoint backed by its repository can work.

## Step 2: Test Event Service directly

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8081/api/event/getAll"
```

This request bypasses Gateway. If it fails, investigate Event Service or its database.

## Step 3: Start Gateway

Open a second PowerShell terminal:

```powershell
cd D:\Personal\showseat-ticket-booking-platform\api-gateway
.\mvnw.cmd spring-boot:run
```

Look for a successful startup on port `8088`. A successful Maven build alone does not
prove that a route works; the running applications must still be tested.

## Step 4: Test through Gateway

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8088/api/event/getAll"
```

The direct request and gateway request should return equivalent Event Service data.

## Step 5: Test the route boundary

This should not reach any downstream service because no route owns the path:

```powershell
Invoke-WebRequest `
  -Method Get `
  -Uri "http://localhost:8088/api/v1/unknown" `
  -SkipHttpErrorCheck
```

Expect a not-found result. This negative test proves the configured routes do not
capture every possible path.

## Useful test cases

| Test | Expected result | What it proves |
| --- | --- | --- |
| Direct Event request on `8081` | Event response | Downstream service works |
| Same Event request on `8088` | Equivalent response | Gateway route works |
| Event Service stopped, request on `8088` | 5xx failure | Gateway cannot connect downstream |
| `/api/venue/...` on `8088` | 404 | Unconfigured paths are not routed |
| Search request with query parameter | Correct search result | Query string is forwarded |
| POST create request | Event response or validation error | Method, headers, and body are forwarded |

## Common problem: Gateway starts but every request is 404

Check:

- the Maven starter is `spring-cloud-starter-gateway-server-webmvc`;
- the configuration prefix is `spring.cloud.gateway.server.webmvc`;
- the YAML indentation is correct;
- the requested path begins with `/api/event/`;
- the configuration file is under `src/main/resources`.

A common mistake is using the WebFlux property prefix with the MVC dependency.

## Common problem: Gateway returns 5xx

Check Event Service directly:

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8081/api/event/getAll" `
  -SkipHttpErrorCheck
```

Then check:

- Event Service is running;
- it is actually listening on `8081`;
- PostgreSQL is available;
- the route URI uses `http://`, not only `localhost:8081`;
- `EVENT_SERVICE_URL` is not set to an incorrect value.

## Common problem: `Address already in use`

Another process already owns port `8088` or `8081`. Identify it in PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue
```

Stop the unintended process or deliberately configure a different port.

## Common problem: YAML parsing failure

Check for:

- tabs instead of spaces;
- missing spaces after colons;
- a list dash at the wrong indentation;
- duplicate keys at the same level;
- `webflux` copied into this MVC project.

## Common problem: direct URL works but gateway URL has a wrong path

Compare these three values:

```text
Incoming gateway path
Configured Path predicate
Event Controller mapping
```

With no path filter, the incoming path is forwarded unchanged. Do not add a rewrite
filter until you can state exactly how the public path differs from the downstream
path.

## Compile-only verification

You can verify dependency resolution and compilation without starting the servers:

```powershell
cd D:\Personal\showseat-ticket-booking-platform\api-gateway
.\mvnw.cmd test
```

This catches build and application-context problems, but it does not replace the HTTP
tests against a running Event Service.
