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

package org.apache.airavata.orchestrator.core.validator.impl;

import org.airavata.appcatalog.cpi.AppCatalog;
import org.airavata.appcatalog.cpi.AppCatalogException;
import org.apache.aiaravata.application.catalog.data.impl.AppCatalogFactory;
import org.apache.airavata.common.exception.ApplicationSettingsException;
import org.apache.airavata.common.utils.AiravataUtils;
import org.apache.airavata.common.utils.Constants;
import org.apache.airavata.common.utils.RequestData;
import org.apache.airavata.common.utils.ServerSettings;
import org.apache.airavata.credential.store.credential.AuditInfo;
import org.apache.airavata.credential.store.store.CredentialReader;
import org.apache.airavata.credential.store.store.CredentialReaderFactory;
import org.apache.airavata.gfac.monitor.util.CommonUtils;
import org.apache.airavata.model.appcatalog.appdeployment.ApplicationDeploymentDescription;
import org.apache.airavata.model.appcatalog.computeresource.BatchQueue;
import org.apache.airavata.model.appcatalog.computeresource.ComputeResourceDescription;
import org.apache.airavata.model.appcatalog.computeresource.JobSubmissionInterface;
import org.apache.airavata.model.appcatalog.computeresource.SSHJobSubmission;
import org.apache.airavata.model.error.ValidatorResult;
import org.apache.airavata.model.workspace.experiment.Experiment;
import org.apache.airavata.model.workspace.experiment.TaskDetails;
import org.apache.airavata.model.workspace.experiment.WorkflowNodeDetails;
import org.apache.airavata.orchestrator.core.context.OrchestratorContext;
import org.apache.airavata.orchestrator.core.validator.JobMetadataValidator;
import org.apache.airavata.persistance.registry.jpa.model.TaskDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Job count validator validate max job submission count for a given resource under given community user name.
 */
public class JobCountValidator implements JobMetadataValidator {
    private static final Logger logger = LoggerFactory.getLogger(JobCountValidator.class);

    @Override
    public ValidatorResult validate(Experiment experiment, WorkflowNodeDetails workflowNodeDetail, TaskDetails taskID,
                                    String credStoreToken) {
        ValidatorResult result;
        try {
            ComputeResourceDescription computeResourceDes = CommonUtils.getComputeResourceDescription(taskID);
            if (computeResourceDes.getBatchQueuesSize() > 0) {
                BatchQueue batchQueue = CommonUtils.getBatchQueueByName(computeResourceDes.getBatchQueues(),
                        taskID.getTaskScheduling().getQueueName());
                if (batchQueue == null) {
                    throw new IllegalArgumentException("Invalid queue name, There is no queue with name :" +
                            taskID.getTaskScheduling().getQueueName());
                }
                int resourceMaxJobCount = batchQueue.getMaxJobsInQueue();
                if (resourceMaxJobCount > 0) {
                    for (JobSubmissionInterface jobSubmissionInterface : computeResourceDes.getJobSubmissionInterfaces()) {
                        switch (jobSubmissionInterface.getJobSubmissionProtocol()) {
                            case LOCAL:
                                // nothing to do
                                return new ValidatorResult(true);
                            case SSH:
                                SSHJobSubmission sshJobSubmission =
                                        AppCatalogFactory.getAppCatalog().getComputeResource().getSSHJobSubmission(
                                                jobSubmissionInterface.getJobSubmissionInterfaceId());
                                switch (sshJobSubmission.getSecurityProtocol()) {
                                    case GSI:
                                        // gsi
                                        RequestData requestData = new RequestData(ServerSettings.getDefaultUserGateway());
                                        requestData.setTokenId(credStoreToken);
                                        return isJobSpaceAvailable(requestData.getMyProxyUserName(),
                                                computeResourceDes.getHostName(), batchQueue.getQueueName(), resourceMaxJobCount);
                                    case SSH_KEYS:
                                        CredentialReader credentialReader = CredentialReaderFactory.createCredentialStoreReader();
                                        AuditInfo auditInfo = credentialReader.getAuditInfo(experiment.getUserName(), credStoreToken);
                                        return isJobSpaceAvailable(auditInfo.getCommunityUser().getUserName(),
                                                computeResourceDes.getHostName(), batchQueue.getQueueName(), resourceMaxJobCount);
                                    // ssh
                                    default:
                                        result = new ValidatorResult(false);
                                        result.setErrorDetails("Doesn't support " + sshJobSubmission.getSecurityProtocol() +
                                                " protocol yet");
                                        return result;
                                }
                            default:
                                result = new ValidatorResult(false);
                                result.setErrorDetails("Doesn't support " +
                                        jobSubmissionInterface.getJobSubmissionProtocol() + " protocol yet");
                                return result;
                        }
                    }
                    result = new ValidatorResult(false);
                    result.setErrorDetails("No JobSubmission interface found");
                    return result;

                }// end of inner if
            }// end of outer if
            return new ValidatorResult(true);
        } catch (Exception e) {
            logger.error("Exception occur while running job count validation process ", e);
            result = new ValidatorResult(false);
            result.setErrorDetails("Exception occur while running job count validation process ");
            return result;
        }

    }

    private ValidatorResult isJobSpaceAvailable(String communityUserName, String computeHostName, String queueName, int resourceMaxJobCount)
            throws ApplicationSettingsException {
        if (communityUserName == null) {
            throw new IllegalArgumentException("Community user name should not be null");
        }
        if (computeHostName == null) {
            throw new IllegalArgumentException("Compute resource should not be null");
        }
        String keyPath = new StringBuilder("/" + Constants.STAT).append("/").append(communityUserName)
                .append("/").toString();
        String key = keyPath + computeHostName + "/" + Constants.JOB + "/" + queueName;
        Map<String, Integer> jobCountMap = AiravataUtils.getJobCountMap(OrchestratorContext.getZk());
        if (jobCountMap.containsKey(key)) {
            int count = jobCountMap.get(key);
            logger.info("Submitted job count = " + count + ", max job count = " + resourceMaxJobCount);
            if (count < resourceMaxJobCount) {
                return new ValidatorResult(true);
            }
        } else {
            logger.info("Job count map doesn't has key : " + key);
            return new ValidatorResult(true);
        }
        logger.info("Resource " + computeHostName + " doesn't has space to submit another job, " +
                "Configured resource max job count is " + resourceMaxJobCount + ".");
        ValidatorResult result = new ValidatorResult(false);
        result.setErrorDetails("Please honour to the gobal max job count " + resourceMaxJobCount);
        return result;
    }
}
