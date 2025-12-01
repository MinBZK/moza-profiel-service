package LDV.java.nl.rijksoverheid.moz.config;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigurationLoader {
    
    private static Configuration configuration;
    
    private ConfigurationLoader() {
    }
    
    public static synchronized Configuration getConfiguration() throws ConfigurationException {
        if (configuration != null) {
            return configuration;
        }


        InputStream ymlStream = ConfigurationLoader.class.getClassLoader().getResourceAsStream("application.yml");
        if (ymlStream != null) {
            YAMLConfiguration yamlConfig = new YAMLConfiguration();
            try (InputStreamReader reader = new InputStreamReader(ymlStream, StandardCharsets.UTF_8)) {
                yamlConfig.read(reader);
                configuration = yamlConfig;
                return configuration;
            } catch (Exception e) {
                throw new ConfigurationException("Failed to load application.yml", e);
            }
        }
        
        // Fall back to application.properties from classpath
        InputStream propertiesStream = ConfigurationLoader.class.getClassLoader().getResourceAsStream("application.properties");
        if (propertiesStream != null) {
            PropertiesConfiguration propertiesConfig = new PropertiesConfiguration();
            try (InputStreamReader reader = new InputStreamReader(propertiesStream, StandardCharsets.UTF_8)) {
                propertiesConfig.read(reader);
                configuration = propertiesConfig;
                return configuration;
            } catch (Exception e) {
                throw new ConfigurationException("Failed to load application.properties", e);
            }
        }
        
        throw new ConfigurationException("No configuration file found. Please provide either application.properties or application.yml in src/main/resources");
    }
    
    public static String getString(String key) throws ConfigurationException {
        return getConfiguration().getString(key);
    }
}
