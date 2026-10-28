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
package org.apache.seata.payment;

import org.apache.seata.action.DemoData;
import org.apache.seata.action.PaymentAction;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.saga.rm.api.CompensationBusinessAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentSagaAction implements PaymentAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentSagaAction.class);

    private final JdbcTemplate jdbcTemplate;

    public PaymentSagaAction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @CompensationBusinessAction(name = "paymentSaga", compensationMethod = "compensate")
    public boolean debit(BusinessActionContext actionContext,
                          @BusinessActionContextParameter(paramName = "userId") String userId,
                          @BusinessActionContextParameter(paramName = "amount") int amount) {
        int before = getBalance(userId);
        int updated = jdbcTemplate.update(
                "update payment set balance = balance - ? where user_id = ? and balance >= ?",
                amount, userId, amount);
        if (updated != 1) {
            throw new IllegalStateException("扣款失败, userId=" + userId + ", amount=" + amount + ", balance=" + before);
        }
        LOGGER.info("[payment] 正向扣款已在 seata_pay.payment 本地提交, xid={}, userId={}, amount={}, balance {} -> {}",
                RootContext.getXID(), userId, amount, before, getBalance(userId));
        return true;
    }

    public boolean compensate(BusinessActionContext actionContext) {
        String userId = String.valueOf(actionContext.getActionContext("userId"));
        int amount = asInt(actionContext.getActionContext("amount"));
        int before = getBalance(userId);
        int updated = jdbcTemplate.update(
                "update payment set balance = balance + ? where user_id = ?",
                amount, userId);
        if (updated != 1) {
            throw new IllegalStateException("退款失败, userId=" + userId);
        }
        LOGGER.info("[payment] 补偿退款已在 seata_pay.payment 本地提交, xid={}, userId={}, amount={}, balance {} -> {}",
                actionContext.getXid(), userId, amount, before, getBalance(userId));
        return true;
    }

    @Override
    public int getBalance(String userId) {
        Integer balance = jdbcTemplate.queryForObject(
                "select balance from payment where user_id = ?", Integer.class, userId);
        if (balance == null) {
            throw new IllegalStateException("账户不存在, userId=" + userId);
        }
        return balance;
    }

    @Override
    public void reset() {
        jdbcTemplate.update("update payment set balance = ? where user_id = ?", DemoData.INIT_BALANCE, DemoData.USER_ID);
        LOGGER.info("[payment] 重置 seata_pay.payment, userId={}, balance={}", DemoData.USER_ID, DemoData.INIT_BALANCE);
    }

    private static int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
