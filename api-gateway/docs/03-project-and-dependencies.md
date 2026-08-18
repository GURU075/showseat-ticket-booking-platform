# 3. Project Structure and Dependencies

## Current project structure

The gateway is an independent Maven application:

```text
api-gateway/
|-- pom.xml
|-- mvnw
|-- mvnw.cmd
`-- src/
    |-- main/
    |   |-- java/com/guru/api_gateway/ApiGatewayApplication.java
    |   `-- resources/application.properties
    `-- test/
```

It does not need a controller for declarative routes. Spring Cloud Gateway reads the
route definitions from application configuration and creates the routing machinery.

## Spring Boot parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.7</version>
    <relativePath/>
</parent>
```

The parent supplies compatible dependency versions, Maven plugin configuration, and
Java build defaults for Spring Boot.

## Java version

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2025.1.2</spring-cloud.version>
</properties>
```

`java.version` tells the build which Java language and bytecode level to use.
`spring-cloud.version` chooses the Spring Cloud release train compatible with this
Spring Boot generation.

## Gateway dependency used by this project

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
</dependency>
```

This starter provides Spring Cloud Gateway Server Web MVC. It runs on the Servlet
stack and uses Spring MVC functional routing internally. The gateway application can
still forward requests to any HTTP service; the downstream service does not have to
use the same web stack.

## Test dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

This provides common Spring Boot and Java testing support. `test` scope means it is
available during tests but is not packaged as a production runtime dependency.

## Spring Cloud BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

BOM means Bill of Materials. It manages a tested group of Spring Cloud dependency
versions. It does not add Gateway to the application; the starter dependency does
that. The BOM is why the Gateway dependency does not need its own `<version>`.

## Spring Boot Maven plugin

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

This supports packaging and running the Spring Boot application, including:

```powershell
.\mvnw.cmd spring-boot:run
```

## Web MVC versus WebFlux Gateway

Spring Cloud Gateway has two server variants:

| Topic | Server Web MVC | Server WebFlux |
| --- | --- | --- |
| Starter | `spring-cloud-starter-gateway-server-webmvc` | `spring-cloud-starter-gateway-server-webflux` |
| Web model | Servlet / MVC | Reactive / Reactor |
| Typical server | Tomcat or Jetty | Reactor Netty |
| Configuration prefix | `spring.cloud.gateway.server.webmvc` | `spring.cloud.gateway.server.webflux` |
| Current project | Yes | No |

Both can perform routing, predicates, and filters. For your first route, remain with
the generated Web MVC choice. Learning routing is more important than switching web
stacks now.

Never combine the MVC starter with a copied `server.webflux.routes` example. The
configuration belongs to a different gateway variant and your expected route may not
be created.

## Why no database dependency?

The gateway does not own Event, Venue, Show, or Seat data. It forwards HTTP requests,
so it does not need JPA, PostgreSQL, Flyway, entity classes, or repositories.

## Why no service discovery yet?

The first route uses a fixed URL:

```text
http://localhost:8081
```

This is the simplest way to learn routing. A service registry and `lb://` URIs solve
a different problem and can be learned after the basic route works.

