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
package org.apache.seata;

import org.apache.curator.test.TestingServer;
import org.apache.seata.common.util.StringUtils;
import org.apache.seata.service.TccTransactionService;
import org.apache.seata.spring.boot.autoconfigure.SeataSpringFenceAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import java.io.IOException;

import static org.apache.seata.e2e.E2EUtil.isInE2ETest;
import static org.apache.seata.e2e.E2EUtil.writeE2EResFile;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    SeataSpringFenceAutoConfiguration.class
})
public class SofaRPCTccTransactionApplication {

    private static TestingServer server;

    private static TccTransactionService tccTransactionService = null;

    public static void main(String[] args) throws Exception {
        if (isInE2ETest()) {
            // wait seata-server
            Thread.sleep(10000);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }));

        //mock zk server
        mockZKServer();

        ApplicationContext applicationContext = SpringApplication.run(SofaRPCTccTransactionApplication.class, args);

        tccTransactionService = applicationContext.getBean(TccTransactionService.class);

        //分布式事务提交demo
        transactionCommitDemo();

        //分布式事务回滚demo
        transactionRollbackDemo();
    }

    private static void mockZKServer() throws Exception {
        //Mock zk server，作为 transfer 配置中心
        server = new TestingServer(2181, true);
        server.start();
    }

    private static void transactionCommitDemo() throws InterruptedException {
        System.out.println("before commit: " + tccTransactionService.snapshot());
        int[] accountBefore = tccTransactionService.readAccount();
        int[] stockBefore = tccTransactionService.readStock();
        String txId = tccTransactionService.doTransactionCommit();
        System.out.println(txId);
        tccTransactionService.assertConfirmed(accountBefore, stockBefore);
        Assert.isTrue(StringUtils.isNotBlank(txId), "事务开启失败");

        //wait commit
        Thread.sleep(3000);
        tccTransactionService.checkBranchTransaction(txId,true);

        if (isInE2ETest()) {
            String res =  "{\"res\": \"commit\"}";
            writeE2EResFile(res, "commit.yaml");
        }
        System.out.println("transaction commit demo finish.");
    }

    private static void transactionRollbackDemo() throws InterruptedException {
        int[] accountBefore = tccTransactionService.readAccount();
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
        tccTransactionService.assertCancelled(accountBefore, stockBefore);

        //wait rollback
        Thread.sleep(3000);

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
