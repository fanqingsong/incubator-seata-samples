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

import java.lang.reflect.Method;

import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.apache.seata.integration.tx.api.remoting.parser.AbstractedRemotingParser;
import org.apache.seata.saga.rm.api.CompensationBusinessAction;
import org.springframework.aop.framework.AopProxyUtils;

/**
 * Seata 2.3 only proxies a bean when some {@code RemotingParser} reports it as a service.
 * {@code @LocalTCC} has {@code LocalTCCRemotingParser}. {@code @CompensationBusinessAction} does not,
 * so a local Saga action called by a Dubbo entry is never intercepted and no branch is registered.
 * This parser marks classes that declare the annotation, which is what makes the compensation run.
 */
public class CompensationBusinessActionRemotingParser extends AbstractedRemotingParser {

    /**
     * Same protocol value {@code LocalTCCRemotingParser} uses for an in-process service.
     */
    private static final short IN_JVM = 5;

    @Override
    public boolean isReference(Object bean, String beanName) {
        return false;
    }

    @Override
    public boolean isService(Object bean, String beanName) {
        return bean != null && hasCompensation(AopProxyUtils.ultimateTargetClass(bean));
    }

    @Override
    public boolean isService(Class<?> beanClass) {
        return hasCompensation(beanClass);
    }

    @Override
    public RemotingDesc getServiceDesc(Object bean, String beanName) {
        if (!isService(bean, beanName)) {
            return null;
        }
        Class<?> serviceClass = AopProxyUtils.ultimateTargetClass(bean);
        RemotingDesc desc = new RemotingDesc();
        desc.setReference(false);
        desc.setService(true);
        desc.setProtocol(IN_JVM);
        desc.setServiceClass(serviceClass);
        desc.setServiceClassName(serviceClass.getName());
        desc.setTargetBean(bean);
        return desc;
    }

    @Override
    public short getProtocol() {
        return IN_JVM;
    }

    private static boolean hasCompensation(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(CompensationBusinessAction.class)) {
                    return true;
                }
            }
            for (Class<?> iface : current.getInterfaces()) {
                if (hasCompensation(iface)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
