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

package org.apache.aiaravata.application.catalog.data.model;

import java.io.Serializable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "POST_JOBCOMMAND")
@IdClass(PostJobCommandPK.class)
public class PostJobCommand implements Serializable {
    @Id
    @Column(name = "APPDEPLOYMENT_ID")
    private String deploymentId;
    @Id
    @Column(name = "COMMAND")
    private String command;

    @ManyToOne(cascade= CascadeType.MERGE)
    @JoinColumn(name = "APPDEPLOYMENT_ID")
    private ApplicationDeployment deployment;

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public ApplicationDeployment getDeployment() {
        return deployment;
    }

    public void setDeployment(ApplicationDeployment deployment) {
        this.deployment = deployment;
    }
}
