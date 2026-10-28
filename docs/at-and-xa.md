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

# AT and XA

AT and XA in this repository share the global-transaction API. A purchase method under either mode logs `RootContext.getXID()`, calls stock and order, and throws to roll the global transaction back. That method does not select the mode.

The mode is the data-source proxy on each process that runs SQL. AT commits the local transaction when the branch method returns, and keeps a before image in `undo_log` so a later global rollback can reverse the SQL. XA enlists the JDBC connection in the database's two-phase commit. The branch stays prepared until Seata tells the database to commit or roll back, and no before image is written.

## The business method looks the same

AT samples put `@GlobalTransactional` on the purchase method. The XA sample does the same:

```47:55:xa-sample/springboot-feign-seata-xa/springboot-feign-seata-business/src/main/java/org/apache/seata/service/impl/BusinessServiceImpl.java
    @GlobalTransactional(timeoutMills = 300000, name = "springboot-feign-seata-xa")
    public void purchase(String userId, String commodityCode, int orderCount, boolean forceRollback) {
        LOGGER.info("purchase begin ... xid: " + RootContext.getXID());
        stockFeignClient.deduct(commodityCode, orderCount);
        orderFeignClient.create(userId, commodityCode, orderCount);
        if (forceRollback) {
            throw new RuntimeException("force rollback!");
        }
    }
```

`@GlobalTransactional` starts the global transaction and binds the XID. An exception leaving that method asks Seata to roll every branch back. A normal return asks Seata to commit them. Stock, order, and account join that XID. They do not start another global transaction.

How the XID crosses a process is also independent of the mode. Dubbo samples copy `RootContext` with Seata's Dubbo filter. The XA sample copies it with `FeignRequestInterceptor`, which sets the `RootContext.KEY_XID` header. Both mechanisms only make the downstream call join the same global transaction.

## Where the mode is chosen

Look at the application class, or at the Spring Boot starter log, before reading the service method.

| Mode | What this repository does |
| --- | --- |
| AT | `@EnableAutoDataSourceProxy` with no mode, or no annotation at all when `seata-spring-boot-starter` is on the classpath. The starter log line is `Auto proxy data source 'dataSource' by 'AT' mode`. |
| XA | `@EnableAutoDataSourceProxy(dataSourceProxyMode = "XA")` on every application that opens a JDBC connection. |

Plain Spring AT, in `at-sample/spring-seata`:

```32:35:at-sample/spring-seata/src/main/java/org/apache/seata/BusinessServiceTester.java
@ComponentScan
@Configuration
@EnableAutoDataSourceProxy
public class BusinessServiceTester {
```

Spring Boot AT, in `at-sample/springboot-seata`, has no `@EnableAutoDataSourceProxy`. The starter wraps the auto-configured `DataSource` in AT mode. `at-api` does the same wrap by hand with `new DataSourceProxy(dataSource)`.

XA, on the business application and on storage, order, and account:

```25:28:xa-sample/springboot-feign-seata-xa/springboot-feign-seata-business/src/main/java/org/apache/seata/SpringbootFeignSeataBusinessApplication.java
@SpringBootApplication
@EnableFeignClients
@EnableAutoDataSourceProxy(dataSourceProxyMode = "XA")
public class SpringbootFeignSeataBusinessApplication  {
```

### Legal values of `dataSourceProxyMode`

`@EnableAutoDataSourceProxy` only wraps a JDBC `DataSource`. That wrap exists for AT and XA. TCC and Saga register a branch through their own resource managers, so this annotation does not apply to them. The TCC and Saga samples in this repository do not declare it.

These samples depend on Seata 2.3.0. In that version the attribute is a `String` and defaults to `"AT"`. The annotation documents the two modes as "AT or XA". The Java compiler accepts any string. The proxy creator accepts only two values, compared without case:

| Value | Proxy |
| --- | --- |
| `AT` | `DataSourceProxy` |
| `XA` | `DataSourceProxyXA` |

Any other value, including `TCC` and `SAGA`, fails at startup with `IllegalArgumentException: Unknown dataSourceProxyMode`. Leaving the attribute off selects AT. XA has to be written as `dataSourceProxyMode = "XA"`.

## What a branch does

Both modes use the same purchase: deduct stock, debit the account, insert the order. The branch lifecycle differs.

### AT

1. While an XID is bound, the AT proxy runs the business SQL on a local transaction and writes the before image into `undo_log` in that same local transaction.
2. The local transaction commits when the branch method returns. The row is already visible to other transactions.
3. The branch reports phase-one success to Seata.
4. A global commit deletes the `undo_log` row.
5. A global rollback builds compensating SQL from the before image and runs it. The business method has already returned, so the undo is a later update.

`at-sample/spring-dubbo-seata` states that contract directly: the mock exception is thrown in the business process after the remote calls have returned. Storage, order, and account have already committed their local transactions. Seata then uses `undo_log` to reverse them.

Each proxied data source writes its own `undo_log` rows. The AT samples create that table in `all.sql` (or `script/db/undo_log.sql` for `dubbo-samples-seata`). A database that will run AT SQL needs this table.

### XA

1. The XA proxy enlists the JDBC connection as an XA resource.
2. Local work ends in `prepare`. The database holds the branch until phase two.
3. A global commit is the database XA commit.
4. A global rollback is the database XA rollback. Seata does not write a before image and does not run compensating SQL.

`OrderServiceImpl.create` in the XA sample is `@Transactional`. That local transaction is the order branch: the Feign call to account is a separate XA branch in the account process, and the insert into `order_tbl` is the order process's own branch. Both carry the XID that business started.

The XA sample's `all.sql` still contains `CREATE TABLE undo_log`. That script is shared with the AT samples. The XA proxy does not record before images in it. The presence of the DDL is not evidence that the sample runs in AT mode. The annotation `dataSourceProxyMode = "XA"` is.

## How to tell a sample apart

| What you see | AT | XA |
| --- | --- | --- |
| Data-source annotation | omitted, or `@EnableAutoDataSourceProxy` | `dataSourceProxyMode = "XA"` |
| After the branch method returns, before Seata decides | the business row is committed | the branch is prepared and waiting |
| Global rollback | compensating SQL from `undo_log` | the database aborts the prepared branch |
| Extra table the mode uses | `undo_log` is written | no before image |

`at-sample` is entirely AT: `at-api`, `spring-seata`, `springboot-seata`, `springboot-mybatis-seata`, `spring-dubbo-seata`, `springboot-dubbo-seata`, and `dubbo-samples-seata`. They differ by Spring, MyBatis, and Dubbo, and by whether the purchase is one process or several. The transaction mode stays AT.

`xa-sample/springboot-feign-seata-xa` is the XA purchase. It is four Spring Boot applications talking over OpenFeign. The Feign split is the service layout. The XA behavior comes from the data-source proxy mode.
