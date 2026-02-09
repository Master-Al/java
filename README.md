# Quarkus Asynchronous Design Pattern (Apache Camel + BDD)

This project demonstrates a simple asynchronous processing flow in Quarkus using Apache Camel's `seda` endpoint and Cucumber BDD tests.

## Architecture Diagram

```mermaid
sequenceDiagram
    participant C as "Cucumber Step"
    participant API as "OrderResource"
    participant S as "OrderService"
    participant Q as "Camel SEDA Queue"
    participant R as "OrderRoute"

    C->>API: POST /orders (OrderRequest)
    API->>S: submit(request)
    S->>Q: send to "seda:orders" (jobId header)
    API-->>C: 202 Accepted + jobId

    C->>API: GET /orders/{id}/status (poll)
    API->>S: getStatus(jobId)
    API-->>C: QUEUED/PROCESSING/COMPLETED

    Q->>R: async consume message
    R->>S: markProcessing(jobId)
    R->>S: buildResult(jobId, request)
    R->>S: complete(jobId, result)

    C->>API: GET /orders/{id}
    API->>S: getResult(jobId)
    API-->>C: 200 OK + OrderResult
```

## Run Tests

```bash
mvn test
```
