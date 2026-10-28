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

import com.alipay.sofa.runtime.spring.factory.ReferenceFactoryBean;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.apache.seata.action.ResultHolder;
import org.apache.seata.action.TccActionOne;
import org.apache.seata.action.TccActionTwo;
import org.apache.seata.action.impl.ResourceTccStore;
import org.apache.seata.action.impl.TccActionOneImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Service
public class TccTransactionService implements InitializingBean, BeanFactoryAware {

    public static final String COMMODITY_CODE = "C00321";

    public static final int AMOUNT = 10;

    public static final int COUNT = 2;

    private BeanFactory beanFactory;

    private TccActionOne tccActionOne;

    private TccActionTwo tccActionTwo;

    private ResourceTccStore accountStore;

    private ResourceTccStore stockStore;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /**
     * use ReferenceFactoryBean not sofaReference annotation. only ReferenceFactoryBean can weave tcc advice
     */
    @Configuration
    public static class SofaRpcReferenceConfig {

        @Bean
        public ReferenceFactoryBean tccActionOneService() {
            ReferenceFactoryBean referenceFactoryBean = new ReferenceFactoryBean();
            referenceFactoryBean.setInterfaceClass(TccActionOne.class);
            referenceFactoryBean.setBeanId("tccActionOneService");
            return referenceFactoryBean;
        }

        @Bean
        public ReferenceFactoryBean tccActionTwoService() {
            ReferenceFactoryBean referenceFactoryBean = new ReferenceFactoryBean();
            referenceFactoryBean.setInterfaceClass(TccActionTwo.class);
            referenceFactoryBean.setBeanId("tccActionTwoService");
            return referenceFactoryBean;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setTccActionOne((TccActionOne) beanFactory.getBean("tccActionOneService"));
        this.setTccActionTwo((TccActionTwo) beanFactory.getBean("tccActionTwoService"));
    }

    /**
     * 发起分布式事务
     *
     * @return string string
     */
    @GlobalTransactional
    public String doTransactionCommit() {
        int[] accountBefore = readAccount();
        int[] stockBefore = readStock();
        //第一个TCC 事务参与者
        boolean result = tccActionOne.prepare(null, AMOUNT);
        if (!result) {
            throw new RuntimeException("TccActionOne failed.");
        }
        List<String> list = new ArrayList<>();
        list.add("c1");
        list.add(String.valueOf(COUNT));
        result = tccActionTwo.prepare(null, COMMODITY_CODE, list);
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
     *
     * @return the string
     */
    @GlobalTransactional
    public void doTransactionRollback() {
        int[] accountBefore = readAccount();
        int[] stockBefore = readStock();
        //第一个TCC 事务参与者
        boolean result = tccActionOne.prepare(null, AMOUNT);
        if (!result) {
            throw new RuntimeException("TccActionOne failed.");
        }
        List<String> list = new ArrayList<>();
        list.add("c1");
        list.add(String.valueOf(COUNT));
        result = tccActionTwo.prepare(null, COMMODITY_CODE, list);
        if (!result) {
            throw new RuntimeException("TccActionTwo failed.");
        }
        assertReserved(accountBefore, stockBefore);
        throw new RuntimeException("transaction rollback");
    }

    public String snapshot() {
        return "account " + TccActionOneImpl.USER_ID + " " + accountStore.describe(TccActionOneImpl.USER_ID)
            + "; stock " + COMMODITY_CODE + " " + stockStore.describe(COMMODITY_CODE);
    }

    public int[] readAccount() {
        return accountStore.read(TccActionOneImpl.USER_ID);
    }

    public int[] readStock() {
        return stockStore.read(COMMODITY_CODE);
    }

    public void assertConfirmed(int[] accountBefore, int[] stockBefore) {
        int[] account = readAccount();
        int[] stock = readStock();
        Assert.isTrue(account[0] == accountBefore[0] - AMOUNT, "Confirm 后可用余额不正确, actual=" + account[0]);
        Assert.isTrue(account[1] == accountBefore[1], "Confirm 后冻结金额应释放, actual=" + account[1]);
        Assert.isTrue(stock[0] == stockBefore[0] - COUNT, "Confirm 后可用库存不正确, actual=" + stock[0]);
        Assert.isTrue(stock[1] == stockBefore[1], "Confirm 后冻结库存应释放, actual=" + stock[1]);
        System.out.println("after confirm: " + snapshot());
    }

    public void assertCancelled(int[] accountBefore, int[] stockBefore) {
        int[] account = readAccount();
        int[] stock = readStock();
        Assert.isTrue(account[0] == accountBefore[0] && account[1] == accountBefore[1],
            "Cancel 后账户未恢复, actual money=" + account[0] + ", frozen=" + account[1]);
        Assert.isTrue(stock[0] == stockBefore[0] && stock[1] == stockBefore[1],
            "Cancel 后库存未恢复, actual count=" + stock[0] + ", frozen=" + stock[1]);
        System.out.println("after cancel: " + snapshot());
    }

    private void assertReserved(int[] accountBefore, int[] stockBefore) {
        int[] account = readAccount();
        int[] stock = readStock();
        Assert.isTrue(account[0] == accountBefore[0] - AMOUNT, "Try 后可用余额未扣减, actual=" + account[0]);
        Assert.isTrue(account[1] == accountBefore[1] + AMOUNT, "Try 后冻结金额不正确, actual=" + account[1]);
        Assert.isTrue(stock[0] == stockBefore[0] - COUNT, "Try 后可用库存未扣减, actual=" + stock[0]);
        Assert.isTrue(stock[1] == stockBefore[1] + COUNT, "Try 后冻结库存不正确, actual=" + stock[1]);
        System.out.println("after try: " + snapshot());
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

    @Autowired
    public void setAccountStore(@Qualifier("accountTccStore") ResourceTccStore accountStore) {
        this.accountStore = accountStore;
    }

    @Autowired
    public void setStockStore(@Qualifier("stockTccStore") ResourceTccStore stockStore) {
        this.stockStore = stockStore;
    }
}
