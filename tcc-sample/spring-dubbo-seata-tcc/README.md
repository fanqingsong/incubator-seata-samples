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

# spring-dubbo-seata-tcc

中文说明见 [docs/tcc.md](../../docs/tcc.md)。

## What

A TCC sample with two business schemas. The consumer starts the global transaction and calls two remote participants. Action one freezes account balance. Action two reserves stock. Dubbo is the RPC layer. ZooKeeper is the registry. MySQL holds `tcc_account` and `tcc_storage`.

## Why

AT can undo SQL by itself. TCC is for a reservation the business has to undo itself. Try moves money or stock from the available column into `frozen`. Confirm drops `frozen`. Cancel adds it back. Each branch records the XID in `account_tx` or `stock_tx` so a repeated confirm or cancel does not apply twice.

## How

Start the provider, then the consumer.

`TccConsumerStarter` loads `spring/seata-dubbo-reference.xml` and runs two demos.

1. `doTransactionCommit()` calls `tccActionOne.prepare(null, 10)` and `tccActionTwo.prepare(null, "C00321", ["c1","2"])`. After both tries it checks that user `U100001` has money 10 lower and frozen 10 higher, and commodity `C00321` has count 2 lower and frozen 2 higher. The method then returns the XID. Seata confirms both branches, and the starter checks that `frozen` is back to its previous value while the available quantity stays reduced.
2. `doTransactionRollback()` runs the same two prepare calls, checks the new reservation, and throws `transaction rollback`. The starter catches that exception. Seata cancels both branches, and the starter checks that money and stock match the values from before this second transaction.

### Code points

- The transaction manager is `TccTransactionService` on the consumer. Both entry methods are `@GlobalTransactional`. The service does not call `commit` or `rollback` itself. Seata calls those methods in phase two.
- `TccActionOneImpl.prepare` on the provider is marked `@TwoPhaseBusinessAction(name = "DubboTccActionOne", commitMethod = "commit", rollbackMethod = "rollback")`. `TccActionTwoImpl` is the same shape with name `DubboTccActionTwo`.
- `@BusinessActionContextParameter` stores the try arguments. Phase two receives only `BusinessActionContext`. `commit` and `rollback` read `actionContext.getActionContext("a")` (and the matching keys on action two) and assert the value is present. The stored `c` is the list element at index 1, the stock count `"2"`.
- `ResourceTccStore` runs the three SQL steps on a plain `DataSource`. It is not wrapped as an AT data source. Account SQL uses `tcc_account`. Stock SQL uses `tcc_storage`.
- The first argument of `prepare` is a null `BusinessActionContext`. Seata fills the context when it intercepts the try method. The demo passes null because the caller does not build that context.
- `spring/seata-dubbo-provider.xml` exports the two action interfaces. The consumer XML only declares `dubbo:reference` and a `GlobalTransactionScanner` named `tcc-sample-reference`. Copies of the action classes in the consumer module are not the beans this XML calls.
- Confirm writes `ResultHolder` value `T` for the XID. Cancel writes `R`. The consumer starter does not call `checkBranchTransaction`. It treats a returned XID as commit and the thrown exception as rollback.
- The registry address is `zookeeper://${zookeeper.address:127.0.0.1}:2181`. Compose sets `zookeeper.address` to the ZooKeeper service.

## Run

```bash
docker compose up --build
docker compose logs -f spring-dubbo-seata-tcc-consumer
docker compose down
```

The consumer prints `before commit`, `after try`, `after confirm`, then the same shape for rollback, and finally `transaction commit demo finish.` and `transaction rollback demo finish.` Provider logs show the account and stock row after prepare, commit, and rollback.

Schema and seed rows are in `e2e-files/sqlsh/all.sql`. Compose mounts that directory into MySQL. A second `docker compose up` reuses the MySQL volume, so the numbers continue from the previous run instead of resetting to 100. `docker compose down -v` drops that volume.
