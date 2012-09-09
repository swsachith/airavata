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
package org.apache.airavata.persistance.registry.jpa.resources;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import org.apache.airavata.persistance.registry.jpa.Resource;
import org.apache.airavata.persistance.registry.jpa.ResourceType;
import org.apache.airavata.persistance.registry.jpa.model.Experiment;
import org.apache.airavata.persistance.registry.jpa.model.Gateway;
import org.apache.airavata.persistance.registry.jpa.model.Project;
import org.apache.airavata.persistance.registry.jpa.model.Users;
import org.apache.airavata.persistance.registry.jpa.utils.QueryGenerator;

public class ProjectResource extends AbstractResource {

    private String name;
    private int id = -1;
    private GatewayResource gateway;
    private WorkerResource worker;

    public ProjectResource() {
    }

    public ProjectResource(int id) {
        this.id = id;
    }

    public ProjectResource(WorkerResource worker, GatewayResource gateway, int id) {
        this.setWorker(worker);
        this.setGateway(gateway);
        this.id = id;
    }

    public Resource create(ResourceType type) {
        if (type == ResourceType.EXPERIMENT) {
            ExperimentResource experimentResource = new ExperimentResource();
            experimentResource.setGateway(getGateway());
            experimentResource.setProject(this);
            experimentResource.setWorker(getWorker());
            return experimentResource;
        } else {
            return null;
        }
    }

    public void remove(ResourceType type, Object name) {
        begin();
        if (type == ResourceType.EXPERIMENT) {
        	QueryGenerator generator = new QueryGenerator(EXPERIMENT);
        	generator.setParameter(ExperimentConstants.PROJECT_ID, id);
        	generator.setParameter(ExperimentConstants.USERNAME, getWorker().getUser());
        	generator.setParameter(ExperimentConstants.EXPERIMENT_ID, name);
        	Query q = generator.deleteQuery(em);
        	q.executeUpdate();
        }
        end();
    }

    public Resource get(ResourceType type, Object name) {
        begin();
        if (type == ResourceType.EXPERIMENT) {
        	QueryGenerator generator = new QueryGenerator(EXPERIMENT);
        	generator.setParameter(ExperimentConstants.PROJECT_ID, id);
        	generator.setParameter(ExperimentConstants.USERNAME, getWorker().getUser());
        	generator.setParameter(ExperimentConstants.EXPERIMENT_ID, name);
        	Query q = generator.selectQuery(em);
            Experiment experiment = (Experiment) q.getSingleResult();
            ExperimentResource experimentResource = (ExperimentResource)Utils.getResource(ResourceType.EXPERIMENT, experiment);
            end();
            return experimentResource;
        }
        return null;
    }

    public List<Resource> populate(Object[] keys) {
        List<Resource> list = new ArrayList<Resource>();
        begin();
        QueryGenerator queryGenerator = new QueryGenerator(PROJECT);
        queryGenerator.setParameter(ProjectConstants.PROJECT_NAME, keys[0]);
        Query q = queryGenerator.selectQuery(em);
        List<?> resultList = q.getResultList();
        if (resultList.size() != 0) {
            for (Object result : resultList) {
                Project project = (Project) result;
                ProjectResource projectResource = (ProjectResource)Utils.getResource(ResourceType.PROJECT, project);
                list.add(projectResource);
            }
        }
        end();
        return list;
    }

    public List<Resource> get(ResourceType type) {
        List<Resource> resourceList = new ArrayList<Resource>();
        begin();
        if (type == ResourceType.EXPERIMENT) {
        	QueryGenerator generator = new QueryGenerator(EXPERIMENT);
        	generator.setParameter(ExperimentConstants.PROJECT_ID, id);
        	Query q = generator.selectQuery(em);
            List<?> results = q.getResultList();
            if (results.size() != 0) {
                for (Object result : results) {
                    Experiment experiment = (Experiment) result;
                    ExperimentResource experimentResource = (ExperimentResource)Utils.getResource(ResourceType.USER, experiment);
                    resourceList.add(experimentResource);
                }
            }
        }
        end();
        return resourceList;
    }

    public void save() {
        begin();
        Project project = new Project();
        project.setProject_name(name);
        Gateway gatewayO = new Gateway();
        gatewayO.setGateway_name(gateway.getGatewayName());
        project.setGateway(gatewayO);
        if (id != -1) {
            project.setProject_ID(id);
        }
        Users user = new Users();
        user.setUser_name(worker.getUser());
        project.setUsers(user);
        em.persist(project);
        end();

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

	public WorkerResource getWorker() {
		return worker;
	}

	public void setWorker(WorkerResource worker) {
		this.worker = worker;
	}

	public GatewayResource getGateway() {
		return gateway;
	}

	public void setGateway(GatewayResource gateway) {
		this.gateway = gateway;
	}

	public boolean isExperimentExists(String experimentId){
		return isExists(ResourceType.EXPERIMENT, experimentId);
	}
	
	public ExperimentResource createExperiment(String experimentId){
		ExperimentResource experimentResource = (ExperimentResource)create(ResourceType.EXPERIMENT);
		experimentResource.setExpID(experimentId);
		return experimentResource;
	}
	
	public ExperimentResource getExperiment(String experimentId){
		return (ExperimentResource)get(ResourceType.EXPERIMENT,experimentId);
	}
	
	public List<ExperimentResource> getExperiments(){
		List<Resource> list = get(ResourceType.EXPERIMENT);
		List<ExperimentResource> result=new ArrayList<ExperimentResource>();
		for (Resource resource : list) {
			result.add((ExperimentResource) resource);
		}
		return result;
	}
	
	public void removeExperiment(String experimentId){
		remove(ResourceType.EXPERIMENT, experimentId);
	}

}