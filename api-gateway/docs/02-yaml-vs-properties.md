# 2. YAML Versus Properties

## Short answer

Spring Boot supports both of these files:

```text
application.properties
application.yml
```

YAML is not required for Spring Cloud Gateway. We often choose it because gateway
configuration is hierarchical and contains lists of routes, predicates, and filters.
YAML makes that shape easier for humans to see.

## A simple property in both formats

Properties format:

```properties
server.port=8088
spring.application.name=api-gateway
```

YAML format:

```yaml
server:
  port: 8088

spring:
  application:
    name: api-gateway
```

Both produce the same Spring configuration keys:

```text
server.port
spring.application.name
```

Indentation in YAML represents the dots used in a properties key.

## A gateway route in YAML

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: event-service-route
              uri: ${EVENT_SERVICE_URL:http://localhost:8081}
              predicates:
                - Path=/api/event/**
```

The `-` characters indicate list items. `routes` is a list, and `predicates` inside
each route is another list.

## The identical route in properties format

```properties
spring.cloud.gateway.server.webmvc.routes[0].id=event-service-route
spring.cloud.gateway.server.webmvc.routes[0].uri=${EVENT_SERVICE_URL:http://localhost:8081}
spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/api/event/**
```

The indexes express the same lists:

- `routes[0]` means the first route;
- `predicates[0]` means the first predicate of that route;
- a second route would begin with `routes[1]`;
- a second predicate on the first route would use `routes[0].predicates[1]`.

YAML is normally clearer once several routes and filters are added because you do not
have to repeat the full prefix and manually manage as many indexes.

## Which file should this project use?

Either format works. Use YAML for the learning example because the hierarchy is
visible and most Gateway documentation uses YAML. Keep properties if you strongly
prefer flat key-value configuration.

The important rule is consistency: define a setting in one place. Do not keep an old
route in `application.properties` while adding a different copy to `application.yml`.
Spring Boot can load both files, which makes duplicates difficult to understand.

For a clean switch:

1. Rename `application.properties` to `application.yml`.
2. Translate its existing `spring.application.name` property into YAML.
3. Add the route under the same YAML document.

## YAML syntax rules

### Indentation is meaningful

Use spaces, normally two per level:

```yaml
server:
  port: 8088
```

This is wrong because `port` is not nested beneath `server`:

```yaml
server:
port: 8088
```

### Do not use tabs

Tabs can cause YAML parsing errors or confusing indentation. Configure the editor to
insert spaces when the Tab key is pressed.

### A colon separates a key and value

```yaml
name: api-gateway
```

Usually place a space after the colon.

### A dash creates a list item

```yaml
predicates:
  - Path=/api/event/**
```

### Comments begin with `#`

```yaml
server:
  port: 8088 # public gateway port
```

### Values can be quoted when needed

Most values in this example do not require quotes. Quotes can help if YAML might
interpret a value as a number, Boolean, date, or special syntax.

## Environment-variable placeholders

This expression:

```yaml
uri: ${EVENT_SERVICE_URL:http://localhost:8081}
```

means:

1. read the environment variable `EVENT_SERVICE_URL`;
2. if it exists, use its value;
3. otherwise use `http://localhost:8081` as the default.

Examples:

```text
Local default: http://localhost:8081
Docker value:  http://event-service:8081
```

This keeps environment-specific addresses out of Java source code.

## Do we need to mention every possible setting?

No. Spring Boot supplies defaults. For one basic route, the important settings are:

| Setting | Required for this example? | Meaning |
| --- | --- | --- |
| `spring.application.name` | Recommended | Identifies the application |
| `server.port` | Yes for the chosen port | Makes Gateway listen on `8088` |
| route `id` | Yes | Unique route name |
| route `uri` | Yes | Destination service |
| route `predicates` | Yes | Conditions that select the route |
| route `filters` | No | Optional request/response transformations |

Do not add configuration merely because it appears in a tutorial. Add a property when
you understand the behavior that it controls.

