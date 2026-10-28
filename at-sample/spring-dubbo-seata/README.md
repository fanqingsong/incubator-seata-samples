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

# spring-dubbo-seata

## What

An AT purchase split into four Spring processes. Business is the transaction manager and has no business tables. Storage, order, and account each own a data source and a Dubbo service. One global transaction covers the stock update, the account debit, and the order insert.

## Why

A local AT sample never leaves the JVM, so the XID never has to cross a network. This sample shows the piece that microservices add: Dubbo carries the XID from the caller to each resource manager, and each process registers its own AT branch.

## How

Interfaces live in `spring-dubbo-seata-common`. Each implementation module starts its own Spring context.

`DubboBusinessServiceTester` loads the business context and calls `purchase("U100001", "C00321", 2)` on a background thread. `purchase` is `@GlobalTransactional(name = "spring-dubbo-tx")`. It calls `storageService.deduct`, then `orderService.create`. `OrderServiceImpl` calls `accountService.debit` over Dubbo and inserts `order_tbl`. The order amount is `200 * orderCount`. A random `RuntimeException("random exception mock!")` rolls every branch back. A normal return commits them.

### Code points

- `@DubboReference` on the business side and `@DubboService` on the provider side are ordinary Dubbo annotations. Seata's Dubbo filter copies `RootContext` onto the RPC attachment, so `RootContext.getXID()` inside storage, order, and account is the same XID the business method logged.
- Each resource starter (`DubboAccountServiceStarter`, `DubboStorageServiceStarter`, `DubboOrderServiceStarter`) has `@EnableAutoDataSourceProxy`. That wraps the local `JdbcTemplate` data source. Business also carries the annotation, but the SQL runs in the three provider processes.
- `OrderServiceImpl.create` is the second hop: order is itself a Dubbo provider and a Dubbo consumer of account. The filter propagates the XID again, so the account update joins the original global transaction.
- `DubboConfiguration` selects the registry. With `E2E_ENV=open`, which Compose sets, the address is `zookeeper://zookeeper:2181`. A local run uses `zookeeper://localhost:2181`.
- AT undo is per database connection. Storage, order, and account can share the `seata` schema, but each proxied data source still writes its own `undo_log` rows for its branch.
- The random exception is thrown in the business process after the remote calls have returned. The providers have already committed their local transactions. Seata then uses `undo_log` to reverse them. That is the AT contract this sample is demonstrating.

## Run

```bash
docker compose up --build
docker compose logs -f spring-dubbo-seata-business
docker compose down
```

The business log prints `purchase begin` and then either a commit or `random exception mock!`.
