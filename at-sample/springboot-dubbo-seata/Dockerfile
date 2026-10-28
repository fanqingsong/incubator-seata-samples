# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/maven:3.9.9-eclipse-temurin-8 AS build

ARG MODULE
ARG JAR_NAME

WORKDIR /build

RUN mkdir -p /root/.m2 && printf '%s\n' \
    '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>*</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' \
    > /root/.m2/settings.xml

COPY . .

RUN set -eux; \
    for name in file.conf application.properties jdbc.properties; do \
      if [ -f "e2e-replace/${name}" ]; then \
        find . -type d -path '*/src/main/resources' -exec cp "e2e-replace/${name}" "{}/${name}" \;; \
      fi; \
    done; \
    mvn -B -pl "${MODULE}" -am package -DskipTests; \
    jar="$(find "${MODULE}/target" -maxdepth 1 -type f -name "${JAR_NAME}-*.jar" ! -name 'original-*' ! -name '*.original' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)"; \
    test -n "$jar"; \
    cp "$jar" /tmp/app.jar

FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/eclipse-temurin:8-jre

WORKDIR /app

COPY --from=build /tmp/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
