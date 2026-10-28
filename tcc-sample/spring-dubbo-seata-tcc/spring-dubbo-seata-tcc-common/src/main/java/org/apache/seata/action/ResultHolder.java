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
package org.apache.seata.action;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Result holder.
 *
 * @author zhangsen
 */
public class ResultHolder {

    private static Map<String, String> actionPaymentResults = new ConcurrentHashMap<String, String>();

    private static Map<String, String> actionStockResults = new ConcurrentHashMap<String, String>();

    /**
     * Set payment branch result.
     *
     * @param txId   the tx id
     * @param result the result
     */
    public static void setActionPaymentResult(String txId, String result) {
        actionPaymentResults.put(txId, result);
    }

    /**
     * Get payment branch result.
     *
     * @param txId the tx id
     * @return the string
     */
    public static String getActionPaymentResult(String txId) {
        return actionPaymentResults.get(txId);
    }

    /**
     * Set stock branch result.
     *
     * @param txId   the tx id
     * @param result the result
     */
    public static void setActionStockResult(String txId, String result) {
        actionStockResults.put(txId, result);
    }

    /**
     * Get stock branch result.
     *
     * @param txId the tx id
     * @return the string
     */
    public static String getActionStockResult(String txId) {
        return actionStockResults.get(txId);
    }

}
