/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Two schemas stand in for two resource managers. TCC does not use undo_log.
CREATE DATABASE IF NOT EXISTS tcc_account DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS tcc_storage DEFAULT CHARACTER SET utf8mb4;

GRANT ALL PRIVILEGES ON tcc_account.* TO 'user'@'%';
GRANT ALL PRIVILEGES ON tcc_storage.* TO 'user'@'%';
FLUSH PRIVILEGES;

USE tcc_account;

CREATE TABLE IF NOT EXISTS account_tbl
(
    id      INT          NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    money   INT          NOT NULL DEFAULT 0,
    frozen  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS account_tx
(
    xid     VARCHAR(128) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    amount  INT          NOT NULL,
    status  VARCHAR(16)  NOT NULL,
    PRIMARY KEY (xid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO account_tbl (user_id, money, frozen)
SELECT 'U100001', 100, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM account_tbl WHERE user_id = 'U100001');

USE tcc_storage;

CREATE TABLE IF NOT EXISTS stock_tbl
(
    id             INT          NOT NULL AUTO_INCREMENT,
    commodity_code VARCHAR(255) NOT NULL,
    `count`        INT          NOT NULL DEFAULT 0,
    frozen         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commodity_code (commodity_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS stock_tx
(
    xid            VARCHAR(128) NOT NULL,
    commodity_code VARCHAR(255) NOT NULL,
    amount         INT          NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    PRIMARY KEY (xid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO stock_tbl (commodity_code, `count`, frozen)
SELECT 'C00321', 100, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM stock_tbl WHERE commodity_code = 'C00321');
