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

# springboot-dubbo-seata

## What

The Spring Boot form of `spring-dubbo-seata`. Four applications share one AT purchase: business, storage, order, and account. Commit and rollback are two explicit HTTP calls instead of a random exception.

## Why

The plain Spring sample starts the purchase from `main` and then exits the interesting path. This sample keeps the providers running and lets you trigger both outcomes on demand, which is the shape you use when the transaction boundary is a web request.

## How

`seata-spring-boot-starter` proxies each provider data source. Business does not update tables. `E2EController` on port `9991` is the entry.

- `GET /testCommit` calls `purchaseCommit`. Stock is deducted, the order is created, and the method returns. Seata commits.
- `GET /testRollback` calls `purchaseRollback`. The same remote calls run, then the method throws `random exception mock!`. The controller catches it and responds with `res: rollback`. Seata rolls the AT branches back.

Both methods use `@GlobalTransactional(name = "spring-dubbo-tx")` and the arguments `U100001`, `C00321`, `2`.

### Code points

- `BusinessServiceImpl` injects `StorageService` and `OrderService` with `@DubboReference`. The Seata Dubbo filter puts the XID on the invocation, so each provider thread sees it in `RootContext`.
- `OrderServiceImpl.create` calculates `200 * orderCount`, calls `accountService.debit` through another `@DubboReference`, then inserts `order_tbl` with `JdbcTemplate`. Account is a third AT branch of the same global transaction.
- Providers do not declare `@EnableAutoDataSourceProxy`. The starter wraps the auto-configured data source when Seata is on the classpath. The business module has the starter too, but its only Seata role is the global transaction interceptor.
- `application.properties` reads `seata.service.grouplist.default=${seata.address:127.0.0.1}:8091` and `dubbo.registry.address=zookeeper://${zookeeper.address:127.0.0.1}:2181`. Compose passes `seata` and `zookeeper` for those placeholders.
- The controller writes a small YAML body. `testRollback` maps the expected exception to `res: rollback`. `testCommit` writes `res: commit` after `purchaseCommit` returns.

## Run

```bash
docker compose up --build
curl http://127.0.0.1:9991/testCommit
curl http://127.0.0.1:9991/testRollback
docker compose down
```
