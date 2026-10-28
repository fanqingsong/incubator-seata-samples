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

A TCC sample with two Dubbo services and two databases. The consumer starts the global transaction. `TccActionPayment` runs in the payment service and freezes `tcc_payment.payment_tbl`. `TccActionStock` runs in the stock service and reserves `tcc_stock.stock_tbl`. Dubbo is the RPC layer. ZooKeeper is the registry.

## Why

AT can undo SQL by itself. TCC is for a reservation the business has to undo itself. Try moves money or stock from the available column into `frozen`. Confirm drops `frozen`. Cancel adds it back. Each service records the XID in its own transaction table, `payment_tx` or `stock_tx`, so a repeated confirm or cancel does not apply twice. The two writes are not in one process: payment cannot see the stock table, and stock cannot see the payment table.

## How

Start payment and stock, then the consumer.

`TccConsumerStarter` loads `spring/seata-dubbo-reference.xml` and runs two demos.

1. `doTransactionCommit()` calls `tccActionPayment.prepare(null, 10)` on the payment service and `tccActionStock.prepare(null, "C00321", ["c1","2"])` on the stock service. After both tries it checks that user `U100001` has money 10 lower and frozen 10 higher in `payment_tbl`, and commodity `C00321` has count 2 lower and frozen 2 higher in `stock_tbl`. The method then returns the XID. Seata confirms both branches, and the starter checks that `frozen` is back to its previous value while the available quantity stays reduced.
2. `doTransactionRollback()` runs the same two prepare calls, checks the new reservation, and throws `transaction rollback`. The starter catches that exception. Seata cancels both branches, and the starter checks that money and stock match the values from before this second transaction.

### Code points

- The transaction manager is `TccTransactionService` on the consumer. Both entry methods are `@GlobalTransactional`. The service does not call `commit` or `rollback` itself. Seata calls those methods in phase two on the service that registered the branch.
- `TccActionPaymentImpl` lives only in `spring-dubbo-seata-tcc-payment`. `prepare` is marked `@TwoPhaseBusinessAction(name = "DubboTccActionPayment", commitMethod = "commit", rollbackMethod = "rollback")`. `TccActionStockImpl` lives only in `spring-dubbo-seata-tcc-stock`, with name `DubboTccActionStock`.
- `@BusinessActionContextParameter` stores the try arguments. Phase two receives only `BusinessActionContext`. `commit` and `rollback` read `actionContext.getActionContext("a")` (and the matching keys on the stock action) and assert the value is present. The stored `c` is the list element at index 1, the stock count `"2"`.
- `ResourceTccStore` in the common module runs the three SQL steps on a plain `DataSource`. It is not wrapped as an AT data source. Payment SQL uses `tcc_payment`. Stock SQL uses `tcc_stock`.
- The first argument of `prepare` is a null `BusinessActionContext`. Seata fills the context when it intercepts the try method. The demo passes null because the caller does not build that context.
- Each service XML exports one interface. The consumer XML only declares two `dubbo:reference` entries and a `GlobalTransactionScanner` named `tcc-sample-reference`.
- Confirm writes `ResultHolder` value `T` for the XID inside that service. Cancel writes `R`. Those maps do not cross the process boundary. The consumer starter does not call `checkBranchTransaction`. It treats a returned XID as commit and the thrown exception as rollback, then reads both tables.
- The registry address is `zookeeper://${zookeeper.address:127.0.0.1}:2181`. Compose sets `zookeeper.address` to the ZooKeeper service.

## Run

```bash
docker compose up --build
docker compose logs -f spring-dubbo-seata-tcc-consumer
docker compose down
```

The consumer prints `before commit`, `after try`, `after confirm`, then the same shape for rollback, and finally `transaction commit demo finish.` and `transaction rollback demo finish.` Payment logs show `payment_tbl`. Stock logs show `stock_tbl`.

Schema and seed rows are in `e2e-files/sqlsh/all.sql`. Compose mounts that directory into MySQL. A second `docker compose up` reuses the MySQL volume, so the numbers continue from the previous run instead of resetting to 100. `docker compose down -v` drops that volume.
