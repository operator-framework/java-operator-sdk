/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample;

import org.apache.commons.lang3.ObjectUtils;

public class MySQLDbConfig {

  private final String host;
  private final String port;
  private final String user;
  private final String password;

  public MySQLDbConfig(String host, String port, String user, String password) {
    this.host = host;
    this.port = port != null ? port : "3306";
    this.user = user;
    this.password = password;
  }

  public static MySQLDbConfig loadFromEnvironmentVars() {
    if (ObjectUtils.anyNull(
        System.getenv("MYSQL_HOST"),
        System.getenv("MYSQL_USER"),
        System.getenv("MYSQL_PASSWORD"))) {
      throw new IllegalStateException("Mysql server parameters not defined");
    }
    return new MySQLDbConfig(
        System.getenv("MYSQL_HOST"),
        System.getenv("MYSQL_PORT"),
        System.getenv("MYSQL_USER"),
        System.getenv("MYSQL_PASSWORD"));
  }

  public String getHost() {
    return host;
  }

  public String getPort() {
    return port;
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }
}
