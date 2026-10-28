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
package org.apache.seata.action.impl;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * One resource's Try / Confirm / Cancel.
 * Try moves quantity from available to frozen and records the branch.
 * Confirm drops the frozen quantity. Cancel returns it to available.
 */
public class ResourceTccStore {

    private static final String TRY = "TRY";

    private static final String CONFIRM = "CONFIRM";

    private static final String CANCEL = "CANCEL";

    private final JdbcTemplate jdbc;

    private final String resourceTable;

    private final String txTable;

    private final String keyColumn;

    private final String availableColumn;

    private final String frozenColumn = quote("frozen");

    public ResourceTccStore(JdbcTemplate jdbc, String resourceTable, String txTable, String keyColumn,
                            String availableColumn) {
        this.jdbc = jdbc;
        this.resourceTable = quote(resourceTable);
        this.txTable = quote(txTable);
        this.keyColumn = quote(keyColumn);
        this.availableColumn = quote(availableColumn);
    }

    public void checkConnection() {
        jdbc.queryForObject("SELECT 1", Integer.class);
    }

    public void tryReserve(String xid, String key, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        inTransaction(conn -> {
            TxRow row = lockTx(conn, xid);
            if (row != null && CANCEL.equals(row.status)) {
                throw new IllegalStateException("try suspended, xid already cancelled: " + xid);
            }
            if (row != null) {
                return;
            }
            int updated = executeUpdate(conn,
                "UPDATE " + resourceTable + " SET " + availableColumn + " = " + availableColumn + " - ?, "
                    + frozenColumn + " = " + frozenColumn + " + ? WHERE " + keyColumn + " = ? AND "
                    + availableColumn + " >= ?",
                amount, amount, key, amount);
            if (updated != 1) {
                throw new IllegalStateException(
                    "insufficient " + availableColumn + ", key=" + key + ", amount=" + amount);
            }
            executeUpdate(conn,
                "INSERT INTO " + txTable + "(xid, " + keyColumn + ", amount, status) VALUES (?, ?, ?, ?)",
                xid, key, amount, TRY);
        });
    }

    public void confirm(String xid) {
        inTransaction(conn -> {
            TxRow row = lockTx(conn, xid);
            if (row == null) {
                throw new IllegalStateException("confirm without try, xid=" + xid);
            }
            if (CONFIRM.equals(row.status)) {
                return;
            }
            if (!TRY.equals(row.status)) {
                throw new IllegalStateException("cannot confirm status " + row.status + ", xid=" + xid);
            }
            int updated = executeUpdate(conn,
                "UPDATE " + resourceTable + " SET " + frozenColumn + " = " + frozenColumn
                    + " - ? WHERE " + keyColumn + " = ? AND " + frozenColumn + " >= ?",
                row.amount, row.key, row.amount);
            if (updated != 1) {
                throw new IllegalStateException("confirm frozen resource failed, xid=" + xid);
            }
            executeUpdate(conn, "UPDATE " + txTable + " SET status = ? WHERE xid = ?", CONFIRM, xid);
        });
    }

    public void cancel(String xid) {
        inTransaction(conn -> {
            TxRow row = lockTx(conn, xid);
            if (row == null) {
                executeUpdate(conn,
                    "INSERT INTO " + txTable + "(xid, " + keyColumn + ", amount, status) VALUES (?, ?, ?, ?)",
                    xid, "", 0, CANCEL);
                return;
            }
            if (CANCEL.equals(row.status)) {
                return;
            }
            if (!TRY.equals(row.status)) {
                throw new IllegalStateException("cannot cancel status " + row.status + ", xid=" + xid);
            }
            int updated = executeUpdate(conn,
                "UPDATE " + resourceTable + " SET " + availableColumn + " = " + availableColumn + " + ?, "
                    + frozenColumn + " = " + frozenColumn + " - ? WHERE " + keyColumn + " = ? AND "
                    + frozenColumn + " >= ?",
                row.amount, row.amount, row.key, row.amount);
            if (updated != 1) {
                throw new IllegalStateException("cancel reserved resource failed, xid=" + xid);
            }
            executeUpdate(conn, "UPDATE " + txTable + " SET status = ? WHERE xid = ?", CANCEL, xid);
        });
    }

    public int[] read(String key) {
        return jdbc.queryForObject(
            "SELECT " + availableColumn + ", " + frozenColumn + " FROM " + resourceTable + " WHERE " + keyColumn
                + " = ?",
            (rs, rowNum) -> new int[] {rs.getInt(1), rs.getInt(2)}, key);
    }

    public String describe(String key) {
        int[] row = read(key);
        return availableColumn.replace("`", "") + "=" + row[0] + ", frozen=" + row[1];
    }

    private void inTransaction(SqlWork work) {
        jdbc.execute((ConnectionCallback<Void>) conn -> {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
                return null;
            } catch (Throwable ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                if (ex instanceof Error) {
                    throw (Error) ex;
                }
                if (ex instanceof SQLException) {
                    throw (SQLException) ex;
                }
                throw new IllegalStateException(ex);
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        });
    }

    private TxRow lockTx(Connection conn, String xid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT status, " + keyColumn + ", amount FROM " + txTable + " WHERE xid = ? FOR UPDATE")) {
            ps.setString(1, xid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new TxRow(rs.getString(1), rs.getString(2), rs.getInt(3));
            }
        }
    }

    private static int executeUpdate(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    private static String quote(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("illegal identifier: " + name);
        }
        return "`" + name + "`";
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection conn) throws SQLException;
    }

    private static final class TxRow {
        private final String status;

        private final String key;

        private final int amount;

        private TxRow(String status, String key, int amount) {
            this.status = status;
            this.key = key;
            this.amount = amount;
        }
    }
}
