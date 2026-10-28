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

# spring-seata

## What

A single-process AT purchase. Spring wires stock, order, and account as local beans. They share one JDBC data source. `@GlobalTransactional` starts the global transaction, and Seata AT undoes the SQL if the method throws.

## Why

This is the usual first AT integration: an existing Spring application, `JdbcTemplate`, and no extra RPC. It shows that AT does not require the business code to write undo logic. The data-source proxy and `undo_log` do that.

## How

`BusinessServiceTester` enables `@EnableAutoDataSourceProxy` and calls `BusinessService.purchase("U100001", "C00321", 2)`.

`purchase` deducts stock, then creates the order. Order creation debits the account and inserts `order_tbl`. Afterwards `random.nextBoolean()` throws `random exception mock!` about half the time. An exception leaving a `@GlobalTransactional` method rolls the global transaction back. A clean return commits it.

### Code points

- `@EnableAutoDataSourceProxy` on the tester wraps the `DataSource` bean from `DataSourceConfiguration`. `JdbcTemplate` then borrows AT-aware connections. There is no manual `DataSourceProxy`.
- `BusinessServiceImpl.purchase` only orchestrates. It logs `RootContext.getXID()` so you can see that the XID is bound on this thread before the first SQL.
- `StockServiceImpl.deduct` runs `update stock_tbl set count = count - ?`. Seata parses that statement, stores the before image, and registers a branch.
- `OrderServiceImpl.create` calls `accountService.debit` first (`orderMoney = 100 * orderCount`), then inserts the order with a `KeyHolder`. Both statements join the same local transaction and the same global XID because they use the proxied data source on the same thread.
- The random exception is the rollback switch. It is not a database error. On rollback Seata replays the undo images. On commit it deletes the undo rows.
- The schema and seed data come from `e2e-files/sqlsh` when you start Compose. The application itself does not reset the tables on startup.

## Run

```bash
docker compose up --build
docker compose logs -f spring-seata
docker compose down
```

`purchase begin` is always logged. `commit status: Committed` or a rollback follows the random throw. Run again to see the other branch.
