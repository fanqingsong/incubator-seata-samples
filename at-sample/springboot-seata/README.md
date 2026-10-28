<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# springboot-seata

## What

The Spring Boot form of `spring-seata`. One application owns stock, order, and account. AT mode protects a purchase that uses `JdbcTemplate`. The process starts an embedded web server on port `8088` and runs the purchase once from `main`.

## Why

Spring Boot's Seata starter replaces the explicit `@EnableAutoDataSourceProxy` and the manual Seata client setup used by the plain Spring sample. Use this sample when you want to see the starter's auto configuration: data-source proxy, transaction scanner, and `application.properties`.

## How

`SpringbootSeataApplication.main` calls `SpringApplication.run`, then starts a thread that calls `businessService.purchase("U100001", "C00321", 2)`. The main thread joins and the process stays up.

`purchase` deducts stock, creates the order (which debits the account), and throws `random exception mock!` on a random boolean. That exception rolls the global transaction back. A normal return commits it.

### Code points

- There is no `@EnableAutoDataSourceProxy`. `seata-spring-boot-starter` proxies the auto-configured `DataSource`. The log line `Auto proxy data source 'dataSource' by 'AT' mode` is that step.
- `@GlobalTransactional(name = "spring-seata-tx")` on `BusinessServiceImpl.purchase` is the transaction boundary. `RootContext.getXID()` is empty before the interceptor and set inside the method.
- `OrderServiceImpl.create` still does the business composition: compute `200 * orderCount`, call `accountService.debit`, then insert `order_tbl`. AT does not care that several services share the method. It cares that their JDBC statements run on a proxied connection while an XID is bound.
- `StorageServiceImpl` and `AccountServiceImpl` are ordinary `@Service` beans with `JdbcTemplate`. No Seata API appears in the SQL.
- The purchase thread is started after the Spring context is ready, so the transaction interceptor and the data source exist. Doing the same call before `SpringApplication.run` returns would miss the proxies.
- Compose copies `e2e-replace/application.properties`, which points JDBC at `mysqlAddress` with user `user`.

## Run

```bash
docker compose up --build
docker compose logs -f springboot-seata
docker compose down
```

The application listens on `8088`. The purchase result is in the log (`Committed` or rollback), not on an HTTP endpoint.
