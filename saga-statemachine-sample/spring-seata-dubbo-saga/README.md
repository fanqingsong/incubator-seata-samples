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

# spring-seata-dubbo-saga

## What

The same inventory-and-balance state machine as `spring-seata-saga`, split across two processes. The consumer owns the state-machine engine. The provider exposes `InventoryAction` and `BalanceAction` through Dubbo. Compensation is still defined by the JSON state language, but the service methods run in another JVM.

## Why

A real Saga process usually calls other services. This sample shows that the state machine does not have to embed the business methods. Dubbo is only the transport. Saga still decides the forward order and which compensation method to call.

## How

Start the provider first, then the consumer.

`DubboProviderStarter` keeps the provider process alive and publishes the action beans. `SagaTransactionStarter` on the consumer loads the engine and runs a successful execution, then an execution whose balance step fails and is compensated.

### Code points

- The state language still lives on the consumer (`statelang/reduce_inventory_and_balance.json`). `ServiceName` matches the Dubbo reference, not a local class.
- `DubboProviderConfiguration` registers the provider with ZooKeeper. Outside Compose it uses `localhost:2181`. When `E2E_ENV=open`, which Compose sets, it uses `zookeeper://zookeeperAddress:2181`.
- `BalanceActionImpl.reduce` on the provider throws `reduce balance failed` when the Saga input contains `throwException=true`. The consumer demo sets `mockReduceBalanceFail=true`, the state machine catches the throwable, and `CompensationTrigger` calls `compensateReduce` on both actions through Dubbo.
- Saga state is stored in the consumer's H2 database (`sql/h2_init.sql`), the same way as the local sample. The provider does not own the state log.
- The provider used to start an embedded ZooKeeper in `mockZKServer()`. That call is commented out, so an external ZooKeeper is required.

## Run

```bash
docker compose up --build
docker compose logs -f spring-seata-dubbo-saga-consumer
docker compose down
```

The provider stays up. The consumer log contains the commit result and the compensation result.
