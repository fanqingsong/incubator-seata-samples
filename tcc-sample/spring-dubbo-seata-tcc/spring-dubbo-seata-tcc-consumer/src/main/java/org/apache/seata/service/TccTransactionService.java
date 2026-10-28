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

import org.apache.seata.action.TccActionOne;
import org.apache.seata.action.TccActionTwo;
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

    private TccActionOne tccActionOne;

    private TccActionTwo tccActionTwo;

    private TccDataReader dataReader;

    /**
     * 发起分布式事务
     *
     * @return string string
     */
    @GlobalTransactional
    public String doTransactionCommit() {
        int[] accountBefore = dataReader.readAccount();
        int[] stockBefore = dataReader.readStock();
        //第一个TCC 事务参与者
        boolean result = tccActionOne.prepare(null, TccDataReader.AMOUNT);
        if (!result) {
            throw new RuntimeException("TccActionOne failed.");
        }
        List<String> list = new ArrayList<>();
        list.add("c1");
        list.add(String.valueOf(TccDataReader.COUNT));
        result = tccActionTwo.prepare(null, TccDataReader.COMMODITY_CODE, list);
        if (!result) {
            throw new RuntimeException("TccActionTwo failed.");
        }
        assertReserved(accountBefore, stockBefore);
        return RootContext.getXID();
    }

    public void checkBranchTransaction(String xid, boolean commit) {
        String actionOneResult = ResultHolder.getActionOneResult(xid);
        String actionTwoResult = ResultHolder.getActionTwoResult(xid);
        Assert.isTrue(commit ? "T".equals(actionOneResult) : "F".equals(actionOneResult), "分支事务" + (commit ? "提交" : "回滚") + "失败");
        Assert.isTrue(commit ? "T".equals(actionTwoResult) : "F".equals(actionTwoResult), "分支事务" + (commit ? "提交" : "回滚") + "失败");
    }

    /**
     * Do transaction rollback string.
     */
    @GlobalTransactional
    public void doTransactionRollback() {
        int[] accountBefore = dataReader.readAccount();
        int[] stockBefore = dataReader.readStock();
        //第一个TCC 事务参与者
        boolean result = tccActionOne.prepare(null, TccDataReader.AMOUNT);
        if (!result) {
            throw new RuntimeException("TccActionOne failed.");
        }
        List<String> list = new ArrayList<>();
        list.add("c1");
        list.add(String.valueOf(TccDataReader.COUNT));
        result = tccActionTwo.prepare(null, TccDataReader.COMMODITY_CODE, list);
        if (!result) {
            throw new RuntimeException("TccActionTwo failed.");
        }
        assertReserved(accountBefore, stockBefore);
        throw new RuntimeException("transaction rollback");
    }

    public String snapshot() {
        return dataReader.snapshot();
    }

    public int[] readAccount() {
        return dataReader.readAccount();
    }

    public int[] readStock() {
        return dataReader.readStock();
    }

    public void assertConfirmed(int[] accountBefore, int[] stockBefore) {
        int[] account = dataReader.readAccount();
        int[] stock = dataReader.readStock();
        Assert.isTrue(account[0] == accountBefore[0] - TccDataReader.AMOUNT,
            "Confirm 后可用余额不正确, actual=" + account[0]);
        Assert.isTrue(account[1] == accountBefore[1], "Confirm 后冻结金额应释放, actual=" + account[1]);
        Assert.isTrue(stock[0] == stockBefore[0] - TccDataReader.COUNT,
            "Confirm 后可用库存不正确, actual=" + stock[0]);
        Assert.isTrue(stock[1] == stockBefore[1], "Confirm 后冻结库存应释放, actual=" + stock[1]);
        System.out.println("after confirm: " + dataReader.snapshot());
    }

    public void assertCancelled(int[] accountBefore, int[] stockBefore) {
        int[] account = dataReader.readAccount();
        int[] stock = dataReader.readStock();
        Assert.isTrue(account[0] == accountBefore[0] && account[1] == accountBefore[1],
            "Cancel 后账户未恢复, actual money=" + account[0] + ", frozen=" + account[1]);
        Assert.isTrue(stock[0] == stockBefore[0] && stock[1] == stockBefore[1],
            "Cancel 后库存未恢复, actual count=" + stock[0] + ", frozen=" + stock[1]);
        System.out.println("after cancel: " + dataReader.snapshot());
    }

    private void assertReserved(int[] accountBefore, int[] stockBefore) {
        int[] account = dataReader.readAccount();
        int[] stock = dataReader.readStock();
        Assert.isTrue(account[0] == accountBefore[0] - TccDataReader.AMOUNT,
            "Try 后可用余额未扣减, actual=" + account[0]);
        Assert.isTrue(account[1] == accountBefore[1] + TccDataReader.AMOUNT,
            "Try 后冻结金额不正确, actual=" + account[1]);
        Assert.isTrue(stock[0] == stockBefore[0] - TccDataReader.COUNT,
            "Try 后可用库存未扣减, actual=" + stock[0]);
        Assert.isTrue(stock[1] == stockBefore[1] + TccDataReader.COUNT,
            "Try 后冻结库存不正确, actual=" + stock[1]);
        System.out.println("after try: " + dataReader.snapshot());
    }

    /**
     * Sets tcc action one.
     *
     * @param tccActionOne the tcc action one
     */
    public void setTccActionOne(TccActionOne tccActionOne) {
        this.tccActionOne = tccActionOne;
    }

    /**
     * Sets tcc action two.
     *
     * @param tccActionTwo the tcc action two
     */
    public void setTccActionTwo(TccActionTwo tccActionTwo) {
        this.tccActionTwo = tccActionTwo;
    }

    public void setDataReader(TccDataReader dataReader) {
        this.dataReader = dataReader;
    }
}
