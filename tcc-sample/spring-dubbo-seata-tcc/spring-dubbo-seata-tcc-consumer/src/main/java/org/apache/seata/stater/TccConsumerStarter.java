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
package org.apache.seata.stater;

import org.apache.seata.common.util.StringUtils;
import org.apache.seata.service.TccTransactionService;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.Assert;

import static org.apache.seata.e2e.E2EUtil.isInE2ETest;
import static org.apache.seata.e2e.E2EUtil.writeE2EResFile;

public class TccConsumerStarter {
    private static TccTransactionService tccTransactionService = null;

    public static void main(String[] args) throws Exception {
        new TccConsumerStarter().start0(args);
    }

    private void start0(String[] args) throws Exception {
        if (isInE2ETest()) {
            // wait provider
            Thread.sleep(5000);
        }

        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("spring/seata-dubbo-reference.xml");
        tccTransactionService = (TccTransactionService)applicationContext.getBean("tccTransactionService");

        //分布式事务提交demo
        transactionCommitDemo();
        //分布式事务回滚demo
        transactionRollbackDemo();
    }

    private static void transactionCommitDemo() throws InterruptedException {
        System.out.println("before commit: " + tccTransactionService.snapshot());
        int[] paymentBefore = tccTransactionService.readPayment();
        int[] stockBefore = tccTransactionService.readStock();
        String txId = tccTransactionService.doTransactionCommit();
        System.out.println(txId);
        tccTransactionService.assertConfirmed(paymentBefore, stockBefore);

        Assert.isTrue(StringUtils.isNotEmpty(txId), "事务开启失败");

        if (isInE2ETest()) {
            String res =  "{\"res\": \"commit\"}";
            writeE2EResFile(res, "commit.yaml");
        }
        System.out.println("transaction commit demo finish.");
    }

    private static void transactionRollbackDemo() throws InterruptedException {
        int[] paymentBefore = tccTransactionService.readPayment();
        int[] stockBefore = tccTransactionService.readStock();
        try {
            tccTransactionService.doTransactionRollback();
            Assert.isTrue(false, "分布式事务未回滚");
        } catch (Throwable t) {
            if (!hasMessage(t, "transaction rollback")) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                if (t instanceof Error) {
                    throw (Error) t;
                }
                throw new RuntimeException(t);
            }
        }
        tccTransactionService.assertCancelled(paymentBefore, stockBefore);

//        //wait rollback
//        Thread.sleep(3000);

        if (isInE2ETest()) {
            String res =  "{\"res\": \"rollback\"}";
            writeE2EResFile(res, "rollback.yaml");
        }
        System.out.println("transaction rollback demo finish.");
    }

    private static boolean hasMessage(Throwable error, String expected) {
        for (Throwable cursor = error; cursor != null; cursor = cursor.getCause()) {
            if (cursor.getMessage() != null && cursor.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
