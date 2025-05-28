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
