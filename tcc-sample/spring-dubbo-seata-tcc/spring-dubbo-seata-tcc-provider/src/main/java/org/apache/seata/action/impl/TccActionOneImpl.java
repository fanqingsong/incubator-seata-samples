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
import org.apache.seata.action.TccActionOne;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.util.Assert;

public class TccActionOneImpl implements TccActionOne {

    public static final String USER_ID = "U100001";

    private final ResourceTccStore accountStore;

    public TccActionOneImpl(ResourceTccStore accountStore) {
        this.accountStore = accountStore;
    }

    @Override
    @TwoPhaseBusinessAction(name = "DubboTccActionOne", commitMethod = "commit", rollbackMethod = "rollback")
    public boolean prepare(BusinessActionContext actionContext, @BusinessActionContextParameter(paramName = "a") int a) {
        String xid = actionContext.getXid();
        accountStore.tryReserve(xid, USER_ID, a);
        System.out.println("TccActionOne prepare, xid:" + xid + ", a:" + a + ", account:[" + accountStore.describe(USER_ID) + "]");
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("a") != null);
        accountStore.confirm(xid);
        System.out.println("TccActionOne commit, xid:" + xid + ", a:" + actionContext.getActionContext("a")
            + ", account:[" + accountStore.describe(USER_ID) + "]");
        ResultHolder.setActionOneResult(xid, "T");
        return true;
    }

    @Override
    public boolean rollback(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("a") != null);
        accountStore.cancel(xid);
        System.out.println("TccActionOne rollback, xid:" + xid + ", a:" + actionContext.getActionContext("a")
            + ", account:[" + accountStore.describe(USER_ID) + "]");
        ResultHolder.setActionOneResult(xid, "R");
        return true;
    }
}
