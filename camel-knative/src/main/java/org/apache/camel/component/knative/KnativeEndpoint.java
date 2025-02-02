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
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.knative.ce.CloudEventsProcessors;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;


@UriEndpoint(
    firstVersion = "3.0.0",
    scheme = "knative",
    syntax = "knative:type/target",
    title = "Knative",
    label = "cloud,eventing")
public class KnativeEndpoint extends DefaultEndpoint implements DelegateEndpoint {
    @UriPath(description = "The Knative type")
    private final Knative.Type type;
    @UriPath(description = "The Knative name")
    private final String name;

    private final KnativeConfiguration configuration;
    private final KnativeEnvironment environment;
    private final KnativeEnvironment.KnativeServiceDefinition service;
    private final Endpoint endpoint;

    public KnativeEndpoint(String uri, KnativeComponent component, Knative.Type targetType, String remaining, KnativeConfiguration configuration) {
        super(uri, component);

        this.type = targetType;
        this.name = remaining.indexOf('/') != -1 ? StringHelper.before(remaining, "/") : remaining;
        this.configuration = configuration;
        this.environment =  this.configuration.getEnvironment();
        this.service = this.environment.lookupServiceOrDefault(targetType, remaining);

        switch (service.getProtocol()) {
        case http:
        case https:
            this.endpoint = http(component.getCamelContext(), service, configuration.getTransportOptions());
            break;
        default:
            throw new IllegalArgumentException("unsupported protocol: " + this.service.getProtocol());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(endpoint);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(endpoint);
        super.doStop();
    }

    @Override
    public KnativeComponent getComponent() {
        return (KnativeComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        final String version = configuration.getCloudEventsSpecVersion();
        final Processor ceProcessor = CloudEventsProcessors.forSpecversion(version).producerProcessor(this);
        final Processor ceConverter = new KnativeConversionProcessor(configuration.isJsonSerializationEnabled());

        return new KnativeProducer(this, ceProcessor, ceConverter, endpoint.createProducer());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final String version = configuration.getCloudEventsSpecVersion();
        final Processor ceProcessor = CloudEventsProcessors.forSpecversion(version).consumerProcessor(this);
        final Processor pipeline = Pipeline.newInstance(getCamelContext(), ceProcessor, processor);
        final Consumer consumer = endpoint.createConsumer(pipeline);

        configureConsumer(consumer);

        return consumer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Endpoint getEndpoint() {
        return this.endpoint;
    }

    public Knative.Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public KnativeConfiguration getConfiguration() {
        return configuration;
    }

    public KnativeEnvironment.KnativeServiceDefinition getService() {
        return service;
    }

    // *****************************
    //
    // Helpers
    //
    // *****************************

    private static Endpoint http(CamelContext context, ServiceDefinition definition, Map<String, Object> transportOptions) {
        try {
            String scheme = Knative.HTTP_COMPONENT;
            String host = definition.getHost();
            int port = definition.getPort();

            if (port == -1) {
                port = Knative.DEFAULT_HTTP_PORT;
            }
            if (ObjectHelper.isEmpty(host)) {
                String name = definition.getName();
                String zone = definition.getMetadata().get(Knative.SERVICE_META_ZONE);

                if (ObjectHelper.isNotEmpty(zone)) {
                    try {
                        zone = context.resolvePropertyPlaceholders(zone);
                    } catch (IllegalArgumentException e) {
                        zone = null;
                    }
                }
                if (ObjectHelper.isNotEmpty(zone)) {
                    name = name + "." + zone;
                }

                host = name;
            }

            ObjectHelper.notNull(host, Knative.SERVICE_META_HOST);

            String uri = String.format("%s:%s:%s", scheme, host, port);
            String path = definition.getMetadata().get(Knative.SERVICE_META_PATH);
            if (path != null) {
                if (!path.startsWith("/")) {
                    uri += "/";
                }

                uri += path;
            }

            final String filterKey = definition.getMetadata().get(Knative.FILTER_HEADER_NAME);
            final String filterVal = definition.getMetadata().get(Knative.FILTER_HEADER_VALUE);
            final Map<String, Object> parameters = new HashMap<>();

            parameters.putAll(transportOptions);

            if (ObjectHelper.isNotEmpty(filterKey) && ObjectHelper.isNotEmpty(filterVal)) {
                parameters.put("filter." + filterKey, filterVal);
            }

            uri = URISupport.appendParametersToURI(uri, parameters);

            return context.getEndpoint(uri);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
