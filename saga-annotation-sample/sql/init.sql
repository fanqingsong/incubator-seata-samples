-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

CREATE DATABASE IF NOT EXISTS seata_pay DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS seata_stock DEFAULT CHARACTER SET utf8mb4;

GRANT ALL PRIVILEGES ON seata_pay.* TO 'user'@'%';
GRANT ALL PRIVILEGES ON seata_stock.* TO 'user'@'%';
FLUSH PRIVILEGES;

USE seata_pay;

CREATE TABLE IF NOT EXISTS payment
(
    id      INT         NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    balance INT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO payment (user_id, balance)
VALUES ('U100001', 1000);

USE seata_stock;

CREATE TABLE IF NOT EXISTS stock
(
    id             INT         NOT NULL AUTO_INCREMENT,
    commodity_code VARCHAR(64) NOT NULL,
    count          INT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commodity_code (commodity_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO stock (commodity_code, count)
VALUES ('C00321', 100);
