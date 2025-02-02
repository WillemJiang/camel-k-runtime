/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.knative;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;

@Component("knative")
public class KnativeComponent extends DefaultComponent {
    public static final String CONFIGURATION_ENV_VARIABLE = "CAMEL_KNATIVE_CONFIGURATION";

    private KnativeConfiguration configuration;
    private String environmentPath;

    public KnativeComponent() {
        this(null);
    }

    public KnativeComponent(CamelContext context) {
        super(context);

        this.configuration = new KnativeConfiguration();
    }

    // ************************
    //
    // Properties
    //
    // ************************

    public KnativeConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Set the configuration.
     */
    public void setConfiguration(KnativeConfiguration configuration) {
        this.configuration = ObjectHelper.notNull(configuration, "configuration");
    }

    public String getEnvironmentPath() {
        return environmentPath;
    }

    /**
     * The path ot the environment definition
     */
    public void setEnvironmentPath(String environmentPath) {
        this.environmentPath = environmentPath;
    }

    public KnativeEnvironment getEnvironment() {
        return configuration.getEnvironment();
    }

    /**
     * The environment
     */
    public void setEnvironment(KnativeEnvironment environment) {
        configuration.setEnvironment(environment);
    }

    public boolean isJsonSerializationEnabled() {
        return configuration.isJsonSerializationEnabled();
    }

    public void setJsonSerializationEnabled(boolean jsonSerializationEnabled) {
        configuration.setJsonSerializationEnabled(jsonSerializationEnabled);
    }

    public String getCloudEventsSpecVersion() {
        return configuration.getCloudEventsSpecVersion();
    }

    public void setCloudEventsSpecVersion(String cloudEventSpecVersion) {
        configuration.setCloudEventsSpecVersion(cloudEventSpecVersion);
    }

    public Map<String, Object> getTransportOptions() {
        return configuration.getTransportOptions();
    }

    /**
     * Transport options.
     */
    public void setTransportOptions(Map<String, Object> transportOptions) {
        configuration.setTransportOptions(transportOptions);
    }

    // ************************
    //
    //
    //
    // ************************

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String type = StringHelper.before(remaining, "/");
        final String target = StringHelper.after(remaining, "/");
        final KnativeConfiguration conf = getKnativeConfiguration();

        conf.getTransportOptions().putAll(
            PropertiesHelper.extractProperties(parameters, "transport.", true)
        );

        // set properties from the endpoint uri
        PropertyBindingSupport.bindProperties(getCamelContext(), conf, parameters);

        return new KnativeEndpoint(uri, this, Knative.Type.valueOf(type), target, conf);
    }

    // ************************
    //
    // Helpers
    //
    // ************************

    private KnativeConfiguration getKnativeConfiguration() throws Exception {
        KnativeConfiguration conf = configuration.copy();

        if (conf.getTransportOptions() == null) {
            conf.setTransportOptions(new HashMap<>());
        }

        if (conf.getEnvironment() == null) {
            String envConfig = System.getenv(CONFIGURATION_ENV_VARIABLE);
            if (environmentPath != null) {
                conf.setEnvironment(
                    KnativeEnvironment.mandatoryLoadFromResource(getCamelContext(), this.environmentPath)
                );
            } else if (envConfig != null) {
                if (envConfig.startsWith("file:") || envConfig.startsWith("classpath:")) {
                    conf.setEnvironment(
                        KnativeEnvironment.mandatoryLoadFromResource(getCamelContext(), envConfig)
                    );
                } else {
                    conf.setEnvironment(
                        KnativeEnvironment.mandatoryLoadFromSerializedString(getCamelContext(), envConfig)
                    );
                }
            } else {
                throw new IllegalStateException("Cannot load Knative configuration from file or env variable");
            }
        }

        return conf;
    }
}
