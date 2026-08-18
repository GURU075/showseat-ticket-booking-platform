# API Gateway CORS: Browser Origins, Preflight, and Security

## 1. What problem are we solving?

Suppose the frontend runs at:

```text
http://localhost:5173
```

and the API Gateway runs at:

```text
http://localhost:8088
```

Even though both are on the same computer, their origins are different because their
ports differ. A browser applies the same-origin policy and does not automatically allow
JavaScript from port `5173` to read responses from port `8088`.

Without a CORS policy, frontend code can fail with a browser message similar to:

```text
Blocked by CORS policy: No Access-Control-Allow-Origin header is present.
```

The API may work correctly in Postman or `curl` while still failing in the browser.

## 2. What is an origin?

An origin is the combination of:

```text
scheme + host + port
```

Examples:

| URL | Origin |
| --- | --- |
| `http://localhost:5173/home` | `http://localhost:5173` |
| `http://localhost:8088/api/event/getAll` | `http://localhost:8088` |
| `https://app.showseat.example/dashboard` | `https://app.showseat.example` |

The path is not part of the origin.

These are different origins:

```text
http://localhost:5173
http://localhost:3000
https://localhost:5173
https://app.showseat.example
```

A configured origin must therefore contain only scheme, host, and optional port. Do
not configure a path or trailing slash.

## 3. What is CORS?

CORS means Cross-Origin Resource Sharing. It is a browser-facing HTTP protocol through
which a server tells the browser which origins, methods, and headers may access a
resource.

The Gateway may return:

```http
Access-Control-Allow-Origin: http://localhost:5173
```

That header tells the browser that JavaScript loaded from that exact origin may read the
response.

## 4. CORS is not authentication or authorization

CORS answers:

> May browser JavaScript from this origin read or send this cross-origin request?

Authentication answers:

> Who is making the request?

Authorization answers:

> Is that user allowed to perform this operation?

CORS is enforced mainly by browsers. Postman, `curl`, backend services, and malicious
scripts running outside a browser can still call the API directly. Therefore CORS must
never replace authentication, authorization, validation, or rate limiting.

## 5. Why configure CORS at the API Gateway?

The frontend calls the Gateway as its public entry point:

```text
Browser -> API Gateway -> Event/Venue/Show/Seat Inventory Service
```

Configuring one policy at the Gateway provides:

- one public browser contract;
- consistent headers across all downstream services;
- preflight handling without contacting business services;
- no duplicated `@CrossOrigin` annotations;
- a smaller chance of services having contradictory policies.

Downstream services should normally remain private and should not be called directly by
the browser.

## 6. Simple requests and preflight requests

### Simple cross-origin request

Some requests can be sent directly. The browser includes an `Origin` header:

```http
GET /api/event/getAll HTTP/1.1
Origin: http://localhost:5173
```

If the origin is trusted, the Gateway forwards the GET and adds this to the response:

```http
Access-Control-Allow-Origin: http://localhost:5173
```

### Preflight request

For requests such as JSON POST, authorization headers, PUT, PATCH, or DELETE, the
browser commonly asks for permission first with an OPTIONS request:

```http
OPTIONS /api/event/create HTTP/1.1
Origin: http://localhost:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Content-Type, Idempotency-Key, X-Correlation-ID
```

This OPTIONS request is called preflight.

When allowed, the Gateway responds without calling Event Service:

```http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Idempotency-Key, X-Correlation-ID
Access-Control-Max-Age: 3600
```

The browser then sends the actual POST.

When the origin or method is not allowed, the Gateway returns `403`. When a preflight
mixes allowed and unapproved request headers, Spring returns only the approved names in
`Access-Control-Allow-Headers`. Because the requested unknown header is missing from
that permission, the browser does not send the actual request.

## 7. Request flow in this project

### Allowed preflight

```text
1. Browser sends OPTIONS with Origin and requested method/headers.
2. Gateway CorsFilter checks the /api/** policy.
3. The origin, method, and headers match.
4. Gateway returns the CORS permission headers.
5. No route, circuit breaker, or downstream service is called.
6. Browser sends the actual request.
```

### Disallowed preflight

```text
1. Browser sends OPTIONS from https://evil.example.
2. Gateway CorsFilter checks the trusted-origin list.
3. The origin does not match.
4. Gateway returns HTTP 403 without an allow-origin header.
5. Event Service is not called.
6. Browser does not send the actual request.
```

## 8. Project configuration

The YAML configuration is:

```yaml
gateway:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
    allow-credentials: ${CORS_ALLOW_CREDENTIALS:false}
    max-age: ${CORS_MAX_AGE:1h}
```

### `allowed-origins`

This is the exact list of trusted frontend origins.

Local defaults:

```text
http://localhost:3000
http://localhost:5173
```

For production, override the list:

```powershell
$env:CORS_ALLOWED_ORIGINS="https://app.showseat.example,https://admin.showseat.example"
```

Rules enforced at startup:

- at least one origin is required;
- blank origins are rejected;
- wildcard `*` is rejected;
- only `http` and `https` schemes are accepted;
- paths, queries, fragments, and user information are rejected.

Failing at startup is safer than silently running with a broken or overly broad policy.

### `allow-credentials`

The default is:

```yaml
allow-credentials: false
```

This project does not currently use browser cookies for authentication. Enabling
credentials creates a higher-trust relationship because browsers may send cookies or
client certificates cross-origin.

If cookie-based authentication is added later:

1. keep an explicit origin allow-list;
2. set `CORS_ALLOW_CREDENTIALS=true` only when required;
3. add CSRF protection;
4. configure cookies with appropriate `SameSite`, `Secure`, and domain settings;
5. test browser behavior end to end.

An `Authorization` request header does not by itself require
`Access-Control-Allow-Credentials: true`. That flag is primarily relevant to browser
credential mode such as cookies and HTTP authentication state.

### `max-age`

```yaml
max-age: 1h
```

The browser may cache a successful preflight result for 3600 seconds. This reduces
repeated OPTIONS traffic.

During CORS-policy development, a shorter duration can make changes easier to test.

## 9. Allowed methods

The Gateway permits these methods for public API paths:

```text
GET
HEAD
POST
PUT
PATCH
DELETE
OPTIONS
```

Allowing a method through CORS does not create a route or bypass authorization. It only
permits the browser to attempt that method. The actual route and service must still
support it.

## 10. Allowed request headers

The policy permits:

| Header | Purpose |
| --- | --- |
| `Accept` | Requested response representation |
| `Authorization` | Future bearer-token authentication |
| `Content-Type` | JSON and other request body formats |
| `Idempotency-Key` | Future duplicate-write protection |
| `X-Correlation-ID` | End-to-end request tracing |

The policy intentionally avoids `allowedHeaders: "*"`. A finite list makes the public
browser contract easier to review and limits accidental header exposure.

## 11. Exposed response headers

The browser may read these non-simple response headers:

```text
X-Correlation-ID
Retry-After
```

`X-Correlation-ID` lets the frontend show or report a request tracking ID.

`Retry-After` lets the frontend understand the circuit-breaker fallback hint.

Without `Access-Control-Expose-Headers`, the headers may exist in the network response
but frontend JavaScript cannot read them.

## 12. Why CORS covers only `/api/**`

The filter registers this URL pattern:

```text
/api/**
```

It deliberately does not grant cross-origin access to:

```text
/actuator/**
/internal/fallback/**
```

Actuator is operational infrastructure, and fallback paths are internal implementation
details. Neither is part of the browser-facing API contract.

## 13. Why use a highest-precedence Servlet filter?

This project uses Spring Cloud Gateway Server Web MVC, which runs on the Servlet stack.
The configured Spring `CorsFilter` executes at highest precedence.

This ensures that preflight is decided before:

- route forwarding;
- circuit-breaker execution;
- downstream network calls;
- controller business logic.

The integration test proves this by comparing the Event Service invocation count before
and after OPTIONS requests.

## 14. Manual testing with PowerShell

### Allowed preflight

```powershell
curl.exe -i -X OPTIONS `
  -H "Origin: http://localhost:5173" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: Content-Type, Idempotency-Key, X-Correlation-ID" `
  http://localhost:8088/api/event/create
```

Expected:

```text
HTTP 200
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods includes POST
Access-Control-Max-Age: 3600
```

### Disallowed preflight

```powershell
curl.exe -i -X OPTIONS `
  -H "Origin: https://evil.example" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: Content-Type" `
  http://localhost:8088/api/event/create
```

Expected:

```text
HTTP 403
No Access-Control-Allow-Origin header
```

### Allowed actual GET

```powershell
curl.exe -i `
  -H "Origin: http://localhost:3000" `
  http://localhost:8088/api/event/getAll
```

Expected response header:

```text
Access-Control-Allow-Origin: http://localhost:3000
```

Remember that `curl` displays CORS headers but does not enforce browser CORS rules.

## 15. Common mistakes

### Using `*` everywhere

A wildcard allows every website origin. It cannot be used as `allowedOrigins` with
credentials enabled, and it is usually too broad for a production application.

### Adding `@CrossOrigin` to every downstream controller

This duplicates the public policy, makes reviews harder, and can produce inconsistent
behavior. The Gateway is the browser entry point, so the policy belongs there.

### Confusing CORS with API security

CORS does not stop Postman, `curl`, mobile applications, or backend callers. Protected
operations still need authentication and authorization.

### Forgetting preflight

Testing only GET requests can hide problems with POST, Authorization, custom headers,
PUT, PATCH, and DELETE.

### Forwarding OPTIONS to downstream services

Preflight is Gateway policy work. Sending it downstream wastes resources and can fail
when controllers have no OPTIONS mapping.

### Forgetting exposed response headers

The browser can receive `X-Correlation-ID` but hide it from JavaScript unless it appears
in `Access-Control-Expose-Headers`.

### Putting a path in the origin

This is invalid:

```text
https://app.showseat.example/dashboard
```

This is the correct origin:

```text
https://app.showseat.example
```

### Enabling credentials without CSRF review

Cross-origin cookies increase the security surface. Cookie authentication and CORS must
be designed together with CSRF protection.

## 16. Automated tests

The Gateway test suite verifies:

1. trusted localhost preflight returns `200`;
2. the exact trusted origin is echoed, never `*`;
3. required methods and request headers are allowed;
4. the one-hour max age becomes 3600 seconds;
5. credentials are disabled by default;
6. preflight does not reach Event Service;
7. an untrusted origin returns `403`;
8. actual allowed responses contain CORS and exposed-header metadata;
9. Actuator receives no cross-origin permission;
10. unapproved actual origins and methods return `403`;
11. an unapproved request header is omitted from `Access-Control-Allow-Headers`, so the
    browser blocks the actual request;
12. circuit-breaker `503` responses remain readable by trusted frontend origins;
13. wildcard, invalid-path origin, and negative max-age configuration are rejected.

## 17. Interview questions and short answers

### What is CORS?

CORS is a browser-enforced HTTP protocol through which a server declares which origins,
methods, and headers may make cross-origin requests.

### What is an origin?

An origin is scheme, host, and port. Paths are not part of it.

### What is a preflight request?

It is an OPTIONS request sent by the browser before certain cross-origin requests to ask
whether the intended origin, method, and headers are allowed.

### Why handle CORS at the Gateway?

The Gateway is the public browser entry point, so one policy consistently protects all
downstream routes and prevents preflight from reaching business services.

### Does CORS protect an API from Postman or curl?

No. CORS is mainly enforced by browsers. Authentication and authorization protect the
API itself.

### Why avoid wildcard origins?

Wildcards trust every website origin, reduce control, and are incompatible with
credentialed requests when used as allowed origins.

### What does `Access-Control-Allow-Origin` do?

It tells the browser which requesting origin may access the response.

### What does `Access-Control-Expose-Headers` do?

It lists non-simple response headers that frontend JavaScript is permitted to read.

### What does `Access-Control-Max-Age` do?

It lets the browser cache successful preflight permission for a number of seconds.

### Is the `Authorization` header the same as CORS credentials mode?

No. The header must be listed in allowed request headers, but
`Access-Control-Allow-Credentials` primarily controls browser credentials such as
cookies and HTTP authentication state.

## 18. Production checklist

- [ ] Production frontend origins are explicit HTTPS origins.
- [ ] Localhost origins are removed from production configuration when unnecessary.
- [ ] No origin contains a path or trailing slash.
- [ ] Wildcard origins are not used.
- [ ] Only required methods and headers are permitted.
- [ ] Only required response headers are exposed.
- [ ] Preflight is answered without downstream calls.
- [ ] Actuator and internal endpoints are outside the CORS mapping.
- [ ] Credential mode is enabled only when the authentication design requires it.
- [ ] Cookie authentication includes a CSRF and SameSite review.
- [ ] Allowed and disallowed origins are covered by automated tests.
- [ ] CORS configuration is reviewed whenever frontend domains change.

## 19. One-minute summary

The Gateway uses a highest-precedence Servlet CORS filter for `/api/**`. It trusts only
explicitly configured frontend origins, permits the API's required methods and headers,
exposes correlation and retry metadata, caches successful preflight for one hour, and
keeps credentials disabled by default. OPTIONS preflight is completed at the Gateway,
so downstream services are not called. CORS controls browser cross-origin access; it
does not replace authentication or authorization.

## 20. Further reading

- [Spring Framework MVC CORS documentation](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html)
- [MDN CORS guide](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)
