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
package org.apache.seata.service;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads payment and stock from the transaction initiator.
 * The initiator does not join the TCC branches; it only observes committed rows.
 */
public class TccDataReader {

    public static final String USER_ID = "U100001";

    public static final String COMMODITY_CODE = "C00321";

    public static final int AMOUNT = 10;

    public static final int COUNT = 2;

    private final JdbcTemplate paymentJdbc;

    private final JdbcTemplate stockJdbc;

    public TccDataReader(JdbcTemplate paymentJdbc, JdbcTemplate stockJdbc) {
        this.paymentJdbc = paymentJdbc;
        this.stockJdbc = stockJdbc;
    }

    public void checkConnection() {
        paymentJdbc.queryForObject("SELECT 1", Integer.class);
        stockJdbc.queryForObject("SELECT 1", Integer.class);
    }

    public int[] readPayment() {
        return paymentJdbc.queryForObject(
            "SELECT money, frozen FROM payment_tbl WHERE user_id = ?",
            (rs, rowNum) -> new int[] {rs.getInt(1), rs.getInt(2)}, USER_ID);
    }

    public int[] readStock() {
        return stockJdbc.queryForObject(
            "SELECT `count`, frozen FROM stock_tbl WHERE commodity_code = ?",
            (rs, rowNum) -> new int[] {rs.getInt(1), rs.getInt(2)}, COMMODITY_CODE);
    }

    public String snapshot() {
        int[] payment = readPayment();
        int[] stock = readStock();
        return "payment " + USER_ID + " money=" + payment[0] + " frozen=" + payment[1]
            + "; stock " + COMMODITY_CODE + " count=" + stock[0] + " frozen=" + stock[1];
    }
}
