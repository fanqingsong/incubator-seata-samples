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
 * 一个资源的 Try / Confirm / Cancel。payment 和 stock 各建一个实例，只是表名和列名不同。
 *
 * <p>以库存为例，起始 {@code stock_tbl} 中 {@code C00321} 的 {@code count=100}、{@code frozen=0}，
 * {@code stock_tx} 还没有这次事务的行。{@code tryReserve(xid, "C00321", 2)} 之后变成
 * {@code count=98}、{@code frozen=2}，事务行状态 {@code TRY}。接着 {@code confirm(xid)} 变成
 * {@code count=98}、{@code frozen=0}，状态 {@code CONFIRM}。如果走 {@code cancel(xid)}，则回到
 * {@code count=100}、{@code frozen=0}，状态 {@code CANCEL}。
 *
 * <p>支付是同一套移动：{@code payment_tbl} 的 {@code U100001} 从 {@code money=100}、{@code frozen=0}
 * 预留 10，Try 后是 {@code money=90}、{@code frozen=10}。
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

    /**
     * Try。把可用数量划到冻结列，并记下这个 XID。方法结束时本地事务已经提交。
     *
     * <p>库存：{@code tryReserve(xid, "C00321", 2)}。
     * {@code stock_tbl} 从 {@code count=100, frozen=0} 变成 {@code count=98, frozen=2}。
     * {@code stock_tx} 插入 {@code (xid, C00321, 2, TRY)}。
     * 支付同理：{@code tryReserve(xid, "U100001", 10)} 让 {@code money} 100 变 90，{@code frozen} 0 变 10。
     *
     * <p>可用数量不够时更新不到行，例如库存只剩 1 却预占 2，整段本地事务回滚，表保持原样。
     * 同一 XID 已经是 {@code TRY} 或 {@code CONFIRM} 时直接返回，不再扣一次。
     * 同一 XID 已经是 {@code CANCEL} 时抛异常，拒绝补做预占。
     */
    public void tryReserve(String xid, String key, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        inTransaction(conn -> {
            TxRow row = lockTx(conn, xid);
            // 空回滚已经插入了 CANCEL。这时再 Try 会把库存划走，而二阶段不会再来，所以直接失败。
            if (row != null && CANCEL.equals(row.status)) {
                throw new IllegalStateException("try suspended, xid already cancelled: " + xid);
            }
            // 重复 Try，或 Confirm 之后又收到 Try：沿用已有记录。
            if (row != null) {
                return;
            }
            // count >= 2 才更新。100 -> 98，frozen 0 -> 2。影响行数不是 1 就视为库存不足。
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

    /**
     * Confirm。只去掉冻结，可用数量保持 Try 之后的值，冻结因此变成真实扣减。
     *
     * <p>承接上面的库存 Try：{@code confirm(xid)} 读到 {@code stock_tx} 的 {@code (C00321, 2, TRY)}，
     * 把 {@code frozen} 从 2 减到 0，{@code count} 仍是 98。状态改成 {@code CONFIRM}。
     * 支付则是 {@code frozen} 从 10 减到 0，{@code money} 保持 90。
     *
     * <p>没有 Try 记录时抛异常。状态已经是 {@code CONFIRM} 时直接返回，重复回调不会把 {@code frozen} 减成负数。
     * 状态是 {@code CANCEL} 时也不能再确认。
     */
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
            // 只减 frozen。count 在 Try 里已经减过，这里再减就会扣两次。
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

    /**
     * Cancel。把 Try 划走的数量退回可用列。
     *
     * <p>承接库存 Try：{@code cancel(xid)} 读到 {@code (C00321, 2, TRY)}，
     * {@code count} 从 98 加回 100，{@code frozen} 从 2 减到 0，状态改成 {@code CANCEL}。
     * 支付则是 {@code money} 从 90 加回 100，{@code frozen} 从 10 减到 0。
     *
     * <p>还有一条空回滚。Cancel 先到，{@code stock_tx} 里没有这个 XID，{@code stock_tbl} 仍是
     * {@code count=100, frozen=0}。这里插入 {@code (xid, "", 0, CANCEL)}，不改库存。
     * 随后到达的 {@code tryReserve} 看到 {@code CANCEL} 会失败，预占不会落在回滚之后。
     * 状态已经是 {@code CANCEL} 时直接返回。
     */
    public void cancel(String xid) {
        inTransaction(conn -> {
            TxRow row = lockTx(conn, xid);
            // 空回滚：还没有 Try。记下 CANCEL，数量为 0，库存保持 100/0。
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
            // 98 + 2 = 100，frozen 2 - 2 = 0。
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
