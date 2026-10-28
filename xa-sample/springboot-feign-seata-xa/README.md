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

# springboot-feign-seata-xa

## What

An XA purchase across four Spring Boot applications. Business calls storage and order with OpenFeign. Order calls account with OpenFeign. Each application that touches MySQL enlists an XA branch. Seata is the transaction coordinator. There is no undo log image as in AT mode.

## Why

AT rewrites committed SQL from a before image. XA uses the database's own two-phase commit: the resource manager prepares, and Seata later tells it to commit or roll back. This sample is the Feign version of that model, including the header that carries the XID between HTTP services.

## How

On startup, `BusinessServiceImpl.initData` deletes `account_tbl`, `order_tbl`, and `stock_tbl`, then inserts account `U100001` with money `10000` and stock `C00321` with count `100`. The four applications share that schema.

`TestController` on business port `8084` exposes the two outcomes.

- `GET /test/commit` calls `purchaseCommit`. Stock is deducted, the order is inserted, and the account is debited. The method returns, and Seata commits every XA branch.
- `GET /test/rollback` calls `purchaseRollback`, which does the same remote work and then throws `force rollback!`. Seata rolls the XA branches back. The controller still returns `res: rollback`.

`purchase(userId, commodityCode, orderCount, forceRollback)` is a third `@GlobalTransactional` method with the same body and a boolean instead of a dedicated method. The HTTP demo uses the two dedicated methods.

### Code points

- Each application class has `@EnableAutoDataSourceProxy(dataSourceProxyMode = "XA")`. The proxy enlists the JDBC connection as an XA resource. Storage updates `stock_tbl`. Order inserts `order_tbl` inside `@Transactional`. Account updates `account_tbl`.
- `FeignRequestInterceptor.apply` reads `RootContext.getXID()` and, when it is present, sets the request header `RootContext.KEY_XID`. Business has this interceptor for calls to stock and order. Order has it for the call to account. The downstream Seata filter restores the XID before the service method runs, which is why those methods can log the same XID.
- `OrderServiceImpl.create` computes `200 * orderCount`, calls `accountFeignClient.debit`, then inserts the order. The Feign call and the local insert are two XA branches of one global transaction.
- `@GlobalTransactional` is only on the business methods. The downstream services do not start a new global transaction. They join the XID from the header.
- `initData` runs in `@PostConstruct`, outside those methods, so the seed rows are committed before any purchase. A later rollback returns the tables to that seed state.
- Default listen ports are storage `8081`, order `8082`, account `8083`, and business `8084`. Feign URLs in this Compose stack use the service names `springboot-feign-seata-storage`, `springboot-feign-seata-order`, and `springboot-feign-seata-account`.

## Run

```bash
docker compose up --build
curl http://127.0.0.1:8084/test/commit
curl http://127.0.0.1:8084/test/rollback
docker compose down
```
