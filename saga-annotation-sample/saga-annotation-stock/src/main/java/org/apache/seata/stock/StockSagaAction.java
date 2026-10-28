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

import org.apache.seata.action.DemoData;
import org.apache.seata.action.StockAction;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.saga.rm.api.CompensationBusinessAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StockSagaAction implements StockAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockSagaAction.class);

    private final JdbcTemplate jdbcTemplate;

    public StockSagaAction(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @CompensationBusinessAction(name = "stockSaga", compensationMethod = "compensate")
    public boolean deduct(BusinessActionContext actionContext,
                           @BusinessActionContextParameter(paramName = "commodityCode") String commodityCode,
                           @BusinessActionContextParameter(paramName = "count") int count) {
        int before = getCount(commodityCode);
        int updated = jdbcTemplate.update(
                "update stock set `count` = `count` - ? where commodity_code = ? and `count` >= ?",
                count, commodityCode, count);
        if (updated != 1) {
            throw new IllegalStateException("扣库存失败, commodityCode=" + commodityCode + ", count=" + count + ", stock=" + before);
        }
        LOGGER.info("[stock] 正向扣库存已在 seata_stock.stock 本地提交, xid={}, commodityCode={}, count={}, stock {} -> {}",
                RootContext.getXID(), commodityCode, count, before, getCount(commodityCode));
        return true;
    }

    public boolean compensate(BusinessActionContext actionContext) {
        String commodityCode = String.valueOf(actionContext.getActionContext("commodityCode"));
        int count = asInt(actionContext.getActionContext("count"));
        int before = getCount(commodityCode);
        int updated = jdbcTemplate.update(
                "update stock set `count` = `count` + ? where commodity_code = ?",
                count, commodityCode);
        if (updated != 1) {
            throw new IllegalStateException("回补库存失败, commodityCode=" + commodityCode);
        }
        LOGGER.info("[stock] 补偿回补已在 seata_stock.stock 本地提交, xid={}, commodityCode={}, count={}, stock {} -> {}",
                actionContext.getXid(), commodityCode, count, before, getCount(commodityCode));
        return true;
    }

    @Override
    public int getCount(String commodityCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select `count` from stock where commodity_code = ?", Integer.class, commodityCode);
        if (count == null) {
            throw new IllegalStateException("商品不存在, commodityCode=" + commodityCode);
        }
        return count;
    }

    @Override
    public void reset() {
        jdbcTemplate.update("update stock set `count` = ? where commodity_code = ?",
                DemoData.INIT_STOCK, DemoData.COMMODITY_CODE);
        LOGGER.info("[stock] 重置 seata_stock.stock, commodityCode={}, count={}",
                DemoData.COMMODITY_CODE, DemoData.INIT_STOCK);
    }

    private static int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
