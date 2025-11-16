package com.intact_id.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Properties;

public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());

        Properties properties = factory.getObject();

        if (properties == null) {
            throw new IllegalStateException("Failed to load YAML properties from: " + resource.getResource().getFilename());
        }

        String sourceName = name != null ? name : resource.getResource().getFilename();
        return new PropertiesPropertySource(sourceName, properties);
    }
}