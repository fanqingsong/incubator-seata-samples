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
import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SagaPurchaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SagaPurchaseService.class);

    private final ParticipantClient participants;

    public SagaPurchaseService(ParticipantClient participants) {
        this.participants = participants;
    }

    @GlobalTransactional(name = "saga-purchase-commit", timeoutMills = 60000)
    public String commitPurchase() {
        String xid = RootContext.getXID();
        LOGGER.info("[business] 开启全局事务并调用两个 Dubbo 参与者, xid={}", xid);
        participants.payment().debit(null, DemoData.USER_ID, DemoData.AMOUNT);
        participants.stock().deduct(null, DemoData.COMMODITY_CODE, DemoData.COUNT);
        LOGGER.info("[business] 两个正向动作都成功, 全局事务将提交且不会补偿, xid={}", xid);
        return xid;
    }

    @GlobalTransactional(name = "saga-purchase-rollback", timeoutMills = 60000)
    public String rollbackPurchase() {
        String xid = RootContext.getXID();
        LOGGER.info("[business] 开启全局事务并调用两个 Dubbo 参与者, xid={}", xid);
        participants.payment().debit(null, DemoData.USER_ID, DemoData.AMOUNT);
        participants.stock().deduct(null, DemoData.COMMODITY_CODE, DemoData.COUNT);
        LOGGER.info("[business] 正向动作已完成, 主动抛异常触发补偿, xid={}", xid);
        throw new SagaRollbackSignal(xid);
    }
}
