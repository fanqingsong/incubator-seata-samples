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
import org.apache.seata.action.TccActionStock;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Stock branch. This process only writes {@code tcc_stock.stock_tbl}.
 */
public class TccActionStockImpl implements TccActionStock {

    private final ResourceTccStore stockStore;

    public TccActionStockImpl(ResourceTccStore stockStore) {
        this.stockStore = stockStore;
    }

    /**
     * Try。Consumer 在全局事务里通过 Dubbo 调用，业务代码只走这里。
     *
     * <p>{@code @TwoPhaseBusinessAction} 把本方法和下面的 {@code commit}、{@code rollback} 登记成同一个分支
     * {@code DubboTccActionStock}。Seata 拦到这次调用后向 TC 注册分支；{@code commit} 和 {@code rollback}
     * 不由 Consumer 调用，二阶段由 TC 回调。
     *
     * <p>第一个参数调用方传的是 {@code null}。拦截器在进入方法前填好 {@link BusinessActionContext}，
     * 所以这里能拿到全局事务的 XID。{@code b} 是商品编码，例如 {@code C00321}，注解把它放进二阶段上下文。
     * {@code list} 形如 {@code ["c1", "2"]}：{@code index = 1} 只保存下标 1 的数量 {@code "2"}，
     * 下标 0 的 {@code "c1"} 不会进入二阶段。
     *
     * <p>{@code tryReserve} 在 {@code tcc_stock} 里提交一次本地事务，和全局事务的提交时机无关：
     * {@code stock_tbl.count} 减掉数量，同一数量加到 {@code frozen}；{@code stock_tx} 插入该 XID，状态为 {@code TRY}。
     * 可用库存不足时更新行数为 0，方法抛异常，这个 Try 失败，Seata 会去 Cancel 已经成功的 payment 分支。
     * 同一 XID 再次进入且状态已是 {@code TRY} 时直接返回，避免重复预占。若该 XID 已被记成 {@code CANCEL}
     * （Cancel 比 Try 先到），这里抛异常，防止悬挂。
     *
     * @return {@code true} 表示预占成功，分支可以进入二阶段
     */
    @Override
    @TwoPhaseBusinessAction(name = "DubboTccActionStock", commitMethod = "commit", rollbackMethod = "rollback")
    public boolean prepare(BusinessActionContext actionContext, @BusinessActionContextParameter(paramName = "b") String b,
                           @BusinessActionContextParameter(paramName = "c", index = 1) List list) {
        String xid = actionContext.getXid();
        // 下标 0 只是演示用的占位值，真正预占的件数在下标 1。
        int count = Integer.parseInt(String.valueOf(list.get(1)));
        stockStore.tryReserve(xid, b, count);
        System.out.println("stock TccActionStock prepare, xid:" + xid + ", b:" + b + ", c:" + list.get(1)
            + ", stock:[" + stockStore.describe(b) + "]");
        return true;
    }

    /**
     * Confirm。payment 和 stock 的 Try 都成功、全局事务正常返回后，TC 回调此方法。
     *
     * <p>二阶段只有 {@link BusinessActionContext}。Try 时的 {@code b}、{@code c} 不再作为方法参数传入，
     * 要从 {@code actionContext.getActionContext} 取回。两个断言用来确认上下文真的带上了商品编码和数量。
     *
     * <p>{@code confirm} 按 {@code stock_tx} 里该 XID 的记录扣减：{@code frozen} 减去预占数量，
     * {@code count} 保持 Try 之后的值，冻结因此变成真实出库。然后把 {@code stock_tx.status} 改成 {@code CONFIRM}。
     * 状态已经是 {@code CONFIRM} 时直接返回，重复回调不会再减一次冻结。没有 Try 记录，或状态不是 {@code TRY}，则抛异常。
     *
     * <p>{@code ResultHolder} 只在本进程的 Map 里记下 {@code T}。Consumer 在另一个进程，读不到这个值，
     * 库存是否提交以 {@code stock_tbl} 为准。
     */
    @Override
    public boolean commit(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("b") != null);
        Assert.isTrue(actionContext.getActionContext("c") != null);
        String commodityCode = String.valueOf(actionContext.getActionContext("b"));
        stockStore.confirm(xid);
        System.out.println("stock TccActionStock commit, xid:" + xid + ", b:" + commodityCode + ", c:"
            + actionContext.getActionContext("c") + ", stock:[" + stockStore.describe(commodityCode) + "]");
        ResultHolder.setActionStockResult(xid, "T");
        return true;
    }

    /**
     * Cancel。任一分支 Try 失败，或全局事务抛出异常时，TC 回调此方法，把本分支的预占退回。
     *
     * <p>和 Confirm 一样，商品编码、数量只存在上下文里。{@code cancel} 把 {@code frozen} 减掉、
     * {@code count} 加回，{@code stock_tx.status} 改成 {@code CANCEL}。状态已经是 {@code CANCEL} 时直接返回。
     *
     * <p>还有一种空回滚：Cancel 先到，{@code stock_tx} 里还没有这个 XID。这时插入一条数量为 0、
     * 状态为 {@code CANCEL} 的记录，不改 {@code stock_tbl}。后面的 Try 看到 {@code CANCEL} 会失败，
     * 避免预占发生在回滚之后。
     *
     * <p>本进程 Map 记下 {@code R}。回滚是否生效，看 {@code count} 和 {@code frozen} 是否回到 Try 之前。
     */
    @Override
    public boolean rollback(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        Assert.isTrue(actionContext.getActionContext("b") != null);
        Assert.isTrue(actionContext.getActionContext("c") != null);
        String commodityCode = String.valueOf(actionContext.getActionContext("b"));
        stockStore.cancel(xid);
        System.out.println("stock TccActionStock rollback, xid:" + xid + ", b:" + commodityCode + ", c:"
            + actionContext.getActionContext("c") + ", stock:[" + stockStore.describe(commodityCode) + "]");
        ResultHolder.setActionStockResult(xid, "R");
        return true;
    }
}
