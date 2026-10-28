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

import org.apache.seata.action.TccActionPayment;
import org.apache.seata.action.TccActionStock;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.action.ResultHolder;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Tcc transaction service.
 *
 * @author zhangsen
 */
public class TccTransactionService {

    private TccActionPayment tccActionPayment;

    private TccActionStock tccActionStock;

    private TccDataReader dataReader;

    /**
     * 发起分布式事务
     *
     * @return string string
     */
    @GlobalTransactional
    public String doTransactionCommit() {
        int[] paymentBefore = dataReader.readPayment();
        int[] stockBefore = dataReader.readStock();
        // payment 服务：冻结余额
        boolean result = tccActionPayment.prepare(null, TccDataReader.AMOUNT);
        if (!result) {
            throw new RuntimeException("TccActionPayment failed.");
        }
        List<String> list = new ArrayList<>();
        list.add("c1");
        list.add(String.valueOf(TccDataReader.COUNT));
        // stock 服务：预占库存
        result = tccActionStock.prepare(null, TccDataReader.COMMODITY_CODE, list);
        if (!result) {
            throw new RuntimeException("TccActionStock failed.");
        }
        assertReserved(paymentBefore, stockBefore);
        return RootContext.getXID();
    }

    public void checkBranchTransaction(String xid, boolean commit) {
        String actionPaymentResult = ResultHolder.getActionPaymentResult(xid);
        String actionStockResult = ResultHolder.getActionStockResult(xid);
        Assert.isTrue(commit ? "T".equals(actionPaymentResult) : "F".equals(actionPaymentResult), "分支事务" + (commit ? "提交" : "回滚") + "失败");
        Assert.isTrue(commit ? "T".equals(actionStockResult) : "F".equals(actionStockResult), "分支事务" + (commit ? "提交" : "回滚") + "失败");
    }

    /**
     * Do transaction rollback string.
     */
    @GlobalTransactional
    public void doTransactionRollback() {
        int[] paymentBefore = dataReader.readPayment();
        int[] stockBefore = dataReader.readStock();
        boolean result = tccActionPayment.prepare(null, TccDataReader.AMOUNT);
        if (!result) {
            throw new RuntimeException("TccActionPayment failed.");
        }
        List<String> list = new ArrayList<>();
        list.add("c1");
        list.add(String.valueOf(TccDataReader.COUNT));
        result = tccActionStock.prepare(null, TccDataReader.COMMODITY_CODE, list);
        if (!result) {
            throw new RuntimeException("TccActionStock failed.");
        }
        assertReserved(paymentBefore, stockBefore);
        throw new RuntimeException("transaction rollback");
    }

    public String snapshot() {
        return dataReader.snapshot();
    }

    public int[] readPayment() {
        return dataReader.readPayment();
    }

    public int[] readStock() {
        return dataReader.readStock();
    }

    public void assertConfirmed(int[] paymentBefore, int[] stockBefore) {
        int[] payment = dataReader.readPayment();
        int[] stock = dataReader.readStock();
        Assert.isTrue(payment[0] == paymentBefore[0] - TccDataReader.AMOUNT,
            "Confirm 后可用余额不正确, actual=" + payment[0]);
        Assert.isTrue(payment[1] == paymentBefore[1], "Confirm 后冻结金额应释放, actual=" + payment[1]);
        Assert.isTrue(stock[0] == stockBefore[0] - TccDataReader.COUNT,
            "Confirm 后可用库存不正确, actual=" + stock[0]);
        Assert.isTrue(stock[1] == stockBefore[1], "Confirm 后冻结库存应释放, actual=" + stock[1]);
        System.out.println("after confirm: " + dataReader.snapshot());
    }

    public void assertCancelled(int[] paymentBefore, int[] stockBefore) {
        int[] payment = dataReader.readPayment();
        int[] stock = dataReader.readStock();
        Assert.isTrue(payment[0] == paymentBefore[0] && payment[1] == paymentBefore[1],
            "Cancel 后支付未恢复, actual money=" + payment[0] + ", frozen=" + payment[1]);
        Assert.isTrue(stock[0] == stockBefore[0] && stock[1] == stockBefore[1],
            "Cancel 后库存未恢复, actual count=" + stock[0] + ", frozen=" + stock[1]);
        System.out.println("after cancel: " + dataReader.snapshot());
    }

    private void assertReserved(int[] paymentBefore, int[] stockBefore) {
        int[] payment = dataReader.readPayment();
        int[] stock = dataReader.readStock();
        Assert.isTrue(payment[0] == paymentBefore[0] - TccDataReader.AMOUNT,
            "Try 后可用余额未扣减, actual=" + payment[0]);
        Assert.isTrue(payment[1] == paymentBefore[1] + TccDataReader.AMOUNT,
            "Try 后冻结金额不正确, actual=" + payment[1]);
        Assert.isTrue(stock[0] == stockBefore[0] - TccDataReader.COUNT,
            "Try 后可用库存未扣减, actual=" + stock[0]);
        Assert.isTrue(stock[1] == stockBefore[1] + TccDataReader.COUNT,
            "Try 后冻结库存不正确, actual=" + stock[1]);
        System.out.println("after try: " + dataReader.snapshot());
    }

    /**
     * Sets the payment action.
     *
     * @param tccActionPayment the payment action
     */
    public void setTccActionPayment(TccActionPayment tccActionPayment) {
        this.tccActionPayment = tccActionPayment;
    }

    /**
     * Sets the stock action.
     *
     * @param tccActionStock the stock action
     */
    public void setTccActionStock(TccActionStock tccActionStock) {
        this.tccActionStock = tccActionStock;
    }

    public void setDataReader(TccDataReader dataReader) {
        this.dataReader = dataReader;
    }
}
