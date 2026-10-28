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

# springboot-mybatis-seata

## What

The same single-process AT purchase as `springboot-seata`, with MyBatis mappers for stock, order, and account. `JdbcTemplate` is used only to reset the tables before the purchase. All three tables share one proxied data source.

## Why

Seata AT sits under JDBC, so the ORM is incidental. This sample shows ordinary MyBatis statements joining one global transaction, without a MyBatis plugin and without changing the mapper XML for Seata.

## How

On startup, `BusinessServiceImpl.initData` deletes and reinserts account `U100001` (money `10000`) and stock `C00321` (count `100`). `SpringbootSeataApplication` then calls `purchase` on a background thread.

`purchase` calls `storageService.deduct` and `orderService.create`. A random `RuntimeException` rolls the global transaction back. Otherwise it commits.

### Code points

- `StorageServiceImpl.deduct` loads the row with `storageMapper.findByCommodityCode`, sets `count` to the stored count minus the requested count, and calls `storageMapper.updateById`. The SQL is in `mapper/StorageMapper.xml`. MyBatis borrows the connection from the Seata-proxied data source, so the update is an AT branch.
- `OrderServiceImpl.create` builds an `Order`, sets `money` to `200 * count`, inserts it through `OrderMapper`, then calls `accountService.debit`.
- `AccountServiceImpl.debit` loads the account, assigns the debit argument to `Account.money`, and updates by id. `AccountMapper.updateById` writes `set money = #{money}`. That statement is also intercepted by AT.
- `BusinessServiceImpl.purchase` is the only `@GlobalTransactional` method. The three mapper calls share `RootContext.getXID()` because they run on the same thread inside that interceptor.
- `initData` uses `JdbcTemplate` in `@PostConstruct`, before the purchase thread starts. That SQL is outside `purchase`, so it commits on its own and is not undone by a later rollback.
- The random exception is the only rollback trigger. Stock, account, and order either all stay at the post-purchase values or return to the values written by `initData`.
- `application.properties` sets `mybatis.mapper-locations=classpath:mapper/*.xml`. Seata configuration stays the file registry, same as the other AT samples.

## Run

```bash
docker compose up --build
docker compose logs -f springboot-mybatis-seata
docker compose down
```

Port `8088` is open. Read the purchase result from the application log.
