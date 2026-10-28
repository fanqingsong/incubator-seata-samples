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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProviderReady implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderReady.class);

    private final ParticipantClient participants;

    public ProviderReady(ParticipantClient participants) {
        this.participants = participants;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        for (int attempt = 1; attempt <= 60; attempt++) {
            try {
                participants.payment().reset();
                participants.stock().reset();
                LOGGER.info("payment 和 stock 已注册到 ZooKeeper");
                return;
            } catch (RuntimeException exception) {
                LOGGER.info("等待 payment 和 stock 注册, attempt={}", attempt);
                Thread.sleep(2000L);
            }
        }
        throw new IllegalStateException("payment 或 stock 在 120 秒内没有注册成功");
    }
}
