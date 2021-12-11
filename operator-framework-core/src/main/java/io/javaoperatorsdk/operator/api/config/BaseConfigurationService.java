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
  protected void logMissingReconcilerWarning(String reconcilerKey, String reconcilersNameMessage) {
    logger.warn("Configuration for reconciler '{}' was not found. {}", reconcilerKey,
        reconcilersNameMessage);
  }

  public String getLoggerName() {
    return LOGGER_NAME;
  }

  protected Logger getLogger() {
    return logger;
  }
}
