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

package org.apache.airavata.persistance.registry.jpa;

import org.apache.airavata.persistance.registry.jpa.resources.GatewayResource;
import org.apache.airavata.persistance.registry.jpa.resources.ProjectResource;
import org.apache.airavata.persistance.registry.jpa.resources.UserResource;
import org.apache.airavata.persistance.registry.jpa.resources.WorkerResource;
import org.apache.airavata.persistance.registry.jpa.util.Initialize;
import org.apache.airavata.registry.cpi.utils.RegistrySettings;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.sql.Timestamp;
import java.util.Calendar;

public abstract class AbstractResourceTest {

    private GatewayResource gatewayResource;
    private WorkerResource workerResource;
    private UserResource userResource;
    private ProjectResource projectResource;

    private static Initialize initialize;
   
    @BeforeClass
	public static void setUpBeforeClass() throws Exception {
    	  initialize = new Initialize("registry-derby.sql");
          initialize.initializeDB();
    }
    @Before
    public void setUp() throws Exception {
        gatewayResource = (GatewayResource)ResourceUtils.getGateway("default");
        workerResource = (WorkerResource)ResourceUtils.getWorker(gatewayResource.getGatewayName(), "admin");
        userResource = (UserResource)ResourceUtils.getUser(RegistrySettings.getSetting("default.registry.user"));
        projectResource = workerResource.getProject("default");
    }

    public Timestamp getCurrentTimestamp() {
        Calendar calender = Calendar.getInstance();
        java.util.Date d = calender.getTime();
        return new Timestamp(d.getTime());
    }
    @AfterClass
	public static void tearDownAfterClass() throws Exception {
        initialize.stopDerbyServer();
	}
   

    public GatewayResource getGatewayResource() {
        return gatewayResource;
    }

    public WorkerResource getWorkerResource() {
        return workerResource;
    }

    public UserResource getUserResource() {
        return userResource;
    }

	public ProjectResource getProjectResource() {
		return projectResource;
	}

	public void setProjectResource(ProjectResource projectResource) {
		this.projectResource = projectResource;
	}


}
