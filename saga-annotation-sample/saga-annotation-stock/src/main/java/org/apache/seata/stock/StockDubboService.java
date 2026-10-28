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
package org.apache.seata.stock;

import org.apache.dubbo.config.annotation.DubboService;
import org.apache.seata.action.StockAction;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Dubbo entry. The call is forwarded to {@link StockSagaAction} so Seata's interceptor can register the branch.
 */
@DubboService
public class StockDubboService implements StockAction {

    private final StockAction stockSagaAction;

    public StockDubboService(@Qualifier("stockSagaAction") StockAction stockSagaAction) {
        this.stockSagaAction = stockSagaAction;
    }

    @Override
    public boolean deduct(BusinessActionContext actionContext, String commodityCode, int count) {
        return stockSagaAction.deduct(actionContext, commodityCode, count);
    }

    @Override
    public int getCount(String commodityCode) {
        return stockSagaAction.getCount(commodityCode);
    }

    @Override
    public void reset() {
        stockSagaAction.reset();
    }
}
