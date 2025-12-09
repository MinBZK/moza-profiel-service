package LDV.java.nl.rijksoverheid.moz.config;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class ConfigurationLoader {
    
    private static final Config configuration = ConfigProvider.getConfig();
    
    private ConfigurationLoader() {
    }
    
    public static synchronized Config getConfiguration() throws ConfigurationException {
            return configuration;
    }

    public static <T> T getValueByKey(String key, Class<T> tClass) throws ConfigurationException {
        return configuration.getValue(key, tClass);
    }
}
