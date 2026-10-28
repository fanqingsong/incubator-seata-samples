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

# samples code specification

## Run with Docker Compose

Use Docker Compose v2 (`docker compose`). Each sample has its own `docker-compose.yml` and does not share a stack with the others. Run one sample at a time, because each sample publishes Seata on `7091` and `8091`.

```bash
cd saga-annotation-sample
docker compose up --build
docker compose logs -f
docker compose down
```

Seata Server is `2.3.0`. The console is `7091`, the server port is `8091`, and the store mode is `file`. Samples that need MySQL use database `seata`, user `user`, password `123456`. Samples that need Dubbo use ZooKeeper. `dubbo-samples-seata` uses ZooKeeper in Compose because multicast does not cross the Compose network. A local run still uses the sample's own defaults.

Images are pulled with the prefix `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/`. The image build resolves Maven dependencies from the Aliyun public repository, and copies `e2e-replace` onto the classpath so containers call `seata` and `mysqlAddress`.

| Directory | What it runs | Call after it is up |
| --- | --- | --- |
| `saga-annotation-sample` | Dubbo Saga annotation. Payment and stock are separate services and databases. | `curl localhost:8088/purchase/commit` and `curl localhost:8088/purchase/rollback` |
| `saga-statemachine-sample/spring-seata-saga` | Saga state machine. | Logs contain the state-machine result. |
| `saga-statemachine-sample/spring-seata-dubbo-saga` | Saga state machine over Dubbo. | Provider stays up. Consumer runs the demo. |
| `at-sample/at-api` | AT API, MySQL. | Process runs the purchase flow. |
| `at-sample/spring-seata` | Spring AT, MySQL. | Process runs the purchase flow. |
| `at-sample/springboot-seata` | Spring Boot AT, MySQL. | App listens on `8088` and runs the purchase flow. |
| `at-sample/springboot-mybatis-seata` | Spring Boot MyBatis AT, MySQL. | App listens on `8088` and runs the purchase flow. |
| `at-sample/spring-dubbo-seata` | Dubbo AT, MySQL, ZooKeeper. | Business service runs the purchase flow. |
| `at-sample/springboot-dubbo-seata` | Spring Boot Dubbo AT, MySQL, ZooKeeper. | `curl localhost:9991/testCommit` and `curl localhost:9991/testRollback` |
| `at-sample/dubbo-samples-seata` | Dubbo samples AT, MySQL, ZooKeeper. | `curl localhost:9999/test/commit` and `curl localhost:9999/test/rollback` |
| `tcc-sample/spring-dubbo-seata-tcc` | Dubbo TCC. Payment and stock are separate services and databases. | Consumer logs contain `transaction commit demo finish` and `transaction rollback demo finish`. |
| `tcc-sample/springboot-sofarpc-seata-tcc` | SOFARPC TCC. ZooKeeper is embedded in the app. | Logs contain `transaction commit demo finish` and `transaction rollback demo finish`. |
| `xa-sample/springboot-feign-seata-xa` | Spring Cloud Feign XA, MySQL. | `curl localhost:8084/test/commit` and `curl localhost:8084/test/rollback` |

##  Directory Structure

The first and second levels are more of a directory

Top level: seata-samples

Second layer: at-sample, tcc-sample, saga-statemachine-sample, saga-annotation-sample, xa-sample

Third floor, The third layer is the specific sample and the naming convention is as follows:

## naming

naming with framework: spring-nacos-seata, springboot-naocs-zk-seata ...

## dependency

pom: The dependencies of each sample should be independent and should not depend on the dependencies of the parent pom of seata samples.



# samples transaction model

`@GlobalTransactional` is the same entry for AT and XA. The mode is the data-source proxy, and the branch commit timing differs. See [docs/at-and-xa.md](docs/at-and-xa.md).

TCC try, confirm, and cancel are explained in [docs/tcc.md](docs/tcc.md) (中文).

https://seata.apache.org/docs/user/quickstart/

## start sequence

1、account

2、storage

3、order

4、business