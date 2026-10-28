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

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.apache.seata.action.ResultHolder;
import org.apache.seata.action.TccActionTwo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

/**
 * The type Tcc action two.
 *
 * @author zhangsen
 */
@SofaService(interfaceType = TccActionTwo.class, bindings = { @SofaServiceBinding(bindingType = "bolt") })
@Service
public class TccActionTwoImpl implements TccActionTwo {

    private final ResourceTccStore stockStore;

    public TccActionTwoImpl(@Qualifier("stockTccStore") ResourceTccStore stockStore) {
        this.stockStore = stockStore;
    }

    @Override
    @TwoPhaseBusinessAction(name = "SofaTccActionTwo", commitMethod = "commit", rollbackMethod = "rollback")
    public boolean prepare(BusinessActionContext actionContext, @BusinessActionContextParameter(paramName = "b") String b,
                           @BusinessActionContextParameter(paramName = "c", index = 1) List list) {
        String xid = actionContext.getXid();
        int count = Integer.parseInt(String.valueOf(list.get(1)));
        stockStore.tryReserve(xid, b, count);
        System.out.println("TccActionTwo prepare, xid:" + xid + ", b:" + b + ", c:" + list.get(1)
            + ", stock:[" + stockStore.describe(b) + "]");
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("b") != null);
        Assert.isTrue(actionContext.getActionContext("c") != null);
        String commodityCode = String.valueOf(actionContext.getActionContext("b"));
        stockStore.confirm(xid);
        System.out.println("TccActionTwo commit, xid:" + xid + ", b:" + commodityCode + ", c:"
            + actionContext.getActionContext("c") + ", stock:[" + stockStore.describe(commodityCode) + "]");
        ResultHolder.setActionTwoResult(xid, "T");
        return true;
    }

    @Override
    public boolean rollback(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("b") != null);
        Assert.isTrue(actionContext.getActionContext("c") != null);
        String commodityCode = String.valueOf(actionContext.getActionContext("b"));
        stockStore.cancel(xid);
        System.out.println("TccActionTwo rollback, xid:" + xid + ", b:" + commodityCode + ", c:"
            + actionContext.getActionContext("c") + ", stock:[" + stockStore.describe(commodityCode) + "]");
        ResultHolder.setActionTwoResult(xid, "R");
        return true;
    }

}
