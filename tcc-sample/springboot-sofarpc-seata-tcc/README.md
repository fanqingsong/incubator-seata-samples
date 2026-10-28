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

# springboot-sofarpc-seata-tcc

中文说明见 [docs/tcc.md](../../docs/tcc.md)。

## What

A single-process TCC sample on SOFARPC. Two bolt services are the TCC participants. The same JVM also starts the global transaction. ZooKeeper is an embedded `TestingServer` on port `2181`, not a separate Compose service.

## Why

The Dubbo TCC sample splits the transaction manager and the participants. This sample shows the same try-confirm-cancel contract when the RPC framework is SOFARPC, and it shows a weaving detail that is easy to miss: the TCC interceptor is applied to a `ReferenceFactoryBean`, not to a `@SofaReference` field.

## How

`SofaRPCTccTransactionApplication.main` sleeps when `E2E_ENV=open`, starts the embedded ZooKeeper, then starts Spring Boot.

`transactionCommitDemo` prints the rows, calls `doTransactionCommit()`, checks the confirmed balances, waits three seconds, and checks that both branches recorded `T`.

`transactionRollbackDemo` calls `doTransactionRollback()`, expects the `transaction rollback` exception, checks that the second reservation was released, and waits three seconds. It does not re-check `ResultHolder`.

### Code points

- `TccActionOneImpl` is published with `@SofaService(interfaceType = TccActionOne.class, bindings = @SofaServiceBinding(bindingType = "bolt"))`. The try method has `@TwoPhaseBusinessAction(name = "SofaTccActionOne", commitMethod = "commit", rollbackMethod = "rollback")`. Action two uses `SofaTccActionTwo`.
- `TccTransactionService.SofaRpcReferenceConfig` builds two `ReferenceFactoryBean` instances. The comment in that class is the integration rule: only this factory bean is advised by the TCC interceptor. `afterPropertiesSet` assigns those proxies to `tccActionOne` and `tccActionTwo`.
- `doTransactionCommit` and `doTransactionRollback` only call `prepare`. Confirm and cancel are phase two. Try arguments (`a = 10`, `b = "C00321"`, `c = ["c1","2"]`) are stored with `@BusinessActionContextParameter` and read back from `BusinessActionContext`. Index 1 of `c` is the reserved stock count.
- Action one freezes 10 on account `U100001` in `tcc_account`. Action two reserves 2 of commodity `C00321` in `tcc_storage`. Confirm drops `frozen`. Cancel puts it back. `account_tx` and `stock_tx` keep the branch status so a repeated phase-two call does not apply twice.
- `seata.enable-auto-data-source-proxy=false` keeps these two data sources out of AT. The application also turns off the TCC fence auto-configuration, which expects a single `DataSource`. The sample records branch state in `account_tx` and `stock_tx` instead.
- `commit` stores `T` in `ResultHolder`. `rollback` stores `R`. Because provider and caller share the process, `checkBranchTransaction(xid, true)` can see the `T` values after the commit demo. The starter also reads both tables and checks the numbers.
- `application.properties` points SOFARPC at `zookeeper://127.0.0.1:2181`, which is the embedded server started in `mockZKServer()`. Compose does not add a ZooKeeper container. MySQL is a Compose service. The schema is `e2e-files/sqlsh/all.sql`.
- Success is `after confirm` with the reservation kept and `frozen` released, then `after cancel` with the second reservation undone, then `transaction commit demo finish.` and `transaction rollback demo finish.`

## Run

```bash
docker compose up --build
docker compose logs -f springboot-sofarpc-seata-tcc
docker compose down
```
