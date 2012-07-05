/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.airavata.xbaya.model.registrybrowser;

import java.net.URI;

import org.apache.airavata.registry.api.AiravataRegistry;

public class InterpreterServiceURL {
    private AiravataRegistry registry;
    private URI interpreterServiceURL;

    public InterpreterServiceURL(AiravataRegistry registry, URI interpreterServiceURI) {
        setRegistry(registry);
        setInterpreterServiceURI(interpreterServiceURI);
    }

    public AiravataRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(AiravataRegistry registry) {
        this.registry = registry;
    }

    public URI getInterpreterServiceURL() {
        return interpreterServiceURL;
    }

    public void setInterpreterServiceURI(URI interpreterServiceURI) {
        this.interpreterServiceURL = interpreterServiceURI;
    }
}