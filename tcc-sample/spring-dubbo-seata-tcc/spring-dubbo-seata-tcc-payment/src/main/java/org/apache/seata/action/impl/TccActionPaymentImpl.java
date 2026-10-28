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

import org.apache.seata.action.ResultHolder;
import org.apache.seata.action.TccActionPayment;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.util.Assert;

/**
 * Payment branch. This process only writes {@code tcc_payment.payment_tbl}.
 */
public class TccActionPaymentImpl implements TccActionPayment {

    public static final String USER_ID = "U100001";

    private final ResourceTccStore paymentStore;

    public TccActionPaymentImpl(ResourceTccStore paymentStore) {
        this.paymentStore = paymentStore;
    }

    @Override
    @TwoPhaseBusinessAction(name = "DubboTccActionPayment", commitMethod = "commit", rollbackMethod = "rollback")
    public boolean prepare(BusinessActionContext actionContext, @BusinessActionContextParameter(paramName = "a") int a) {
        String xid = actionContext.getXid();
        paymentStore.tryReserve(xid, USER_ID, a);
        System.out.println("payment TccActionPayment prepare, xid:" + xid + ", a:" + a
            + ", payment:[" + paymentStore.describe(USER_ID) + "]");
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("a") != null);
        paymentStore.confirm(xid);
        System.out.println("payment TccActionPayment commit, xid:" + xid + ", a:" + actionContext.getActionContext("a")
            + ", payment:[" + paymentStore.describe(USER_ID) + "]");
        ResultHolder.setActionPaymentResult(xid, "T");
        return true;
    }

    @Override
    public boolean rollback(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("a") != null);
        paymentStore.cancel(xid);
        System.out.println("payment TccActionPayment rollback, xid:" + xid + ", a:" + actionContext.getActionContext("a")
            + ", payment:[" + paymentStore.describe(USER_ID) + "]");
        ResultHolder.setActionPaymentResult(xid, "R");
        return true;
    }
}
