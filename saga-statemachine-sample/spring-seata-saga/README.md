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

# spring-seata-saga

## What

A state-machine Saga. The process "reduce inventory, then reduce balance" is declared in JSON. The Java classes are plain Spring beans. Seata's state-machine engine drives the steps, persists execution state in an in-memory H2 database, and runs compensation when a later step fails.

## Why

Annotation Saga hard-codes the call order in Java. A state machine keeps the process definition outside the service methods, so choice, catch, and compensation can change without rewriting the participants. This sample is the local, single-process form of that design.

## How

`SagaTransactionStarter` loads `spring/seata-saga.xml` and obtains `StateMachineEngine`.

`transactionCommittedDemo` starts `reduceInventoryAndBalance` twice for the same business key style of input: once with `startWithBusinessKey` (synchronous) and once with `startWithBusinessKeyAsync`. Both expect `ExecutionStatus.SU`.

`transactionCompensatedDemo` starts the same machine asynchronously with `mockReduceBalanceFail=true`. Inventory reduction succeeds, balance reduction throws, and the engine compensates. The assertion checks `inst.getCompensationStatus()`, not the forward status.

### Code points

- `statelang/reduce_inventory_and_balance.json` is the process. `StartState` is `ReduceInventory`. Each `ServiceTask` names a Spring bean (`inventoryAction`, `balanceAction`), a method, and a `CompensateState`.
- `Input` uses JSONPath such as `$.[businessKey]`. `Output` writes the method result back into the state context, for example `reduceInventoryResult`.
- `ChoiceState` continues to `ReduceBalance` only when inventory reduction returned true. Otherwise it goes to `Fail`.
- `ReduceBalance` has a `Catch` for `java.lang.Throwable` that moves to `CompensationTrigger`. That state tells the engine to run the recorded compensation states in reverse: `CompensateReduceBalance`, then `CompensateReduceInventory`.
- `BalanceActionImpl.reduce` throws when the input map contains `throwException=true`. The state machine passes `mockReduceBalanceFail` into that map. `compensateReduce` is the reverse method and only logs success in the normal demo.
- `seata-saga.xml` builds `DbStateMachineConfig` on `jdbc:h2:mem:seata_saga`, loads `statelang/*.json`, and sets `enableAsync=true`. `h2_init.sql` creates the Saga state tables. The engine still reports the global transaction to Seata Server through `txServiceGroup`.

## Run

```bash
docker compose up --build
docker compose logs -f spring-seata-saga
docker compose down
```

Look for `saga transaction commit succeed` and `saga transaction compensate succeed`.
