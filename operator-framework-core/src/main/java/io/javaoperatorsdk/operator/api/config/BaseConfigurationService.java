package io.javaoperatorsdk.operator.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseConfigurationService extends AbstractConfigurationService {

  private static final String LOGGER_NAME = "Default ConfigurationService implementation";
  private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);

  public BaseConfigurationService(Version version) {
    super(version);
  }

  @Override
  protected void logMissingControllerWarning(String controllerKey, String controllersNameMessage) {
    logger.warn("Configuration for controller '{}' was not found. {}", controllerKey,
        controllersNameMessage);
  }

  public String getLoggerName() {
    return LOGGER_NAME;
  }

  protected Logger getLogger() {
    return logger;
  }
}
