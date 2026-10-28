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
package org.apache.seata.config;

import org.apache.seata.action.impl.ResourceTccStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class TccDataSourceConfig {

    @Bean
    public DataSource accountDataSource(@Value("${mysql.address:127.0.0.1}") String address,
                                         @Value("${mysql.user:user}") String user,
                                         @Value("${mysql.password:123456}") String password) {
        return dataSource(address, user, password, "tcc_account");
    }

    @Bean
    public DataSource stockDataSource(@Value("${mysql.address:127.0.0.1}") String address,
                                       @Value("${mysql.user:user}") String user,
                                       @Value("${mysql.password:123456}") String password) {
        return dataSource(address, user, password, "tcc_storage");
    }

    @Bean
    public JdbcTemplate accountJdbcTemplate(@Qualifier("accountDataSource") DataSource accountDataSource) {
        return new JdbcTemplate(accountDataSource);
    }

    @Bean
    public JdbcTemplate stockJdbcTemplate(@Qualifier("stockDataSource") DataSource stockDataSource) {
        return new JdbcTemplate(stockDataSource);
    }

    @Bean
    public ResourceTccStore accountTccStore(@Qualifier("accountJdbcTemplate") JdbcTemplate accountJdbcTemplate) {
        ResourceTccStore store = new ResourceTccStore(accountJdbcTemplate, "account_tbl", "account_tx", "user_id", "money");
        store.checkConnection();
        return store;
    }

    @Bean
    public ResourceTccStore stockTccStore(@Qualifier("stockJdbcTemplate") JdbcTemplate stockJdbcTemplate) {
        ResourceTccStore store = new ResourceTccStore(stockJdbcTemplate, "stock_tbl", "stock_tx", "commodity_code", "count");
        store.checkConnection();
        return store;
    }

    private static DataSource dataSource(String address, String user, String password, String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + address + ":3306/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        return dataSource;
    }
}
