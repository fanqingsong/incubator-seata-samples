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

# at-api

## What

An AT-mode sample with no Spring container. One process updates three logical databases (account, stock, and order) through Seata's low-level API. The global transaction is begun, committed, and rolled back by hand.

## Why

`@GlobalTransactional` hides the transaction manager. This sample shows the same AT behavior with `TMClient`, `RMClient`, and `GlobalTransaction`, which is what you need outside Spring or when the transaction boundary is not a method annotation.

## How

`BusinessService.main` is the whole demo.

1. `TMClient.init` and `RMClient.init` register this process as both transaction manager and resource manager, group `my_test_tx_group`.
2. It resets account money to `999`, stock count to `100`, and clears orders.
3. `GlobalTransactionContext.getCurrentOrCreate()` plus `tx.begin(60000, "testBiz")` opens the global transaction and binds the XID.
4. `stockService.deduct` and `orderService.create` run SQL. `create` also calls `accountService.reduce`. The order price is `count * 200`.
5. `opCount` is `5`, so the account debit is `1000`. The balance becomes negative. `validNegativeCheck` fails, and the code calls `tx.rollback()` instead of `tx.commit()`.

### Code points

- `DataSourceUtil.getDataSource` wraps each Druid pool with `new DataSourceProxy(dataSource)`. That proxy is the AT interceptor: it records the before image in `undo_log` and undoes the SQL if the branch rolls back. JDBC code itself does not mention Seata.
- Three keys (`account`, `stock`, `order`) in `jdbc.properties` become three resources. In this demo they can point at the same MySQL schema, but Seata still treats each proxied data source as a branch.
- When `E2E_ENV=open`, `DataSourceUtil` reads the `e2e.jdbc.*` properties, which point at `mysqlAddress` and user `user`.
- Rollback here is explicit. It is not caused by an exception. Changing `opCount` so that `opCount * 200` stays within `999` makes `needCommit` true and the transaction commits.
- After the decision, the main thread sleeps so the process can be inspected. The SQL changes to stock, account, and order are either all committed or all undone.

## Run

```bash
docker compose up --build
docker compose logs -f at-api
docker compose down
```

The default `opCount` prints `rollback trx, cause: data negative`.
