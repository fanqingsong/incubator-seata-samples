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
package org.apache.seata.business;

import org.apache.seata.action.DemoData;
import org.springframework.stereotype.Service;

@Service
public class PurchaseApplicationService {

    private final ParticipantClient participants;
    private final SagaPurchaseService sagaPurchaseService;

    public PurchaseApplicationService(ParticipantClient participants, SagaPurchaseService sagaPurchaseService) {
        this.participants = participants;
        this.sagaPurchaseService = sagaPurchaseService;
    }

    public String commit() {
        reset();
        int balanceBefore = balance();
        int stockBefore = stock();
        String xid = sagaPurchaseService.commitPurchase();
        return report("提交", xid, balanceBefore, balance(), stockBefore, stock(),
                "全局事务已提交。Saga 没有 Confirm，两个库保持扣减后的结果。");
    }

    public String rollback() {
        reset();
        int balanceBefore = balance();
        int stockBefore = stock();
        String xid;
        try {
            sagaPurchaseService.rollbackPurchase();
            throw new IllegalStateException("回滚演示没有抛出异常");
        } catch (RuntimeException exception) {
            xid = findXid(exception);
            if (xid == null) {
                throw exception;
            }
        }
        return report("回滚", xid, balanceBefore, balance(), stockBefore, stock(),
                "全局事务已回滚。支付库和库存库都通过补偿方法加回原值。");
    }

    private void reset() {
        participants.payment().reset();
        participants.stock().reset();
    }

    private int balance() {
        return participants.payment().getBalance(DemoData.USER_ID);
    }

    private int stock() {
        return participants.stock().getCount(DemoData.COMMODITY_CODE);
    }

    private static String findXid(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SagaRollbackSignal) {
                return ((SagaRollbackSignal) current).getXid();
            }
            current = current.getCause();
        }
        return null;
    }

    private static String report(String action, String xid, int balanceBefore, int balanceAfter,
                                 int stockBefore, int stockAfter, String summary) {
        return action + " xid=" + xid + "\n"
                + "seata_pay.payment userId=" + DemoData.USER_ID
                + " balance " + balanceBefore + " -> " + balanceAfter + "\n"
                + "seata_stock.stock commodityCode=" + DemoData.COMMODITY_CODE
                + " count " + stockBefore + " -> " + stockAfter + "\n"
                + "本次购买 " + DemoData.COUNT + " 件, 单价 " + DemoData.PRICE
                + ", 扣款 " + DemoData.AMOUNT + "\n"
                + summary + "\n";
    }
}
