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
package org.apache.airavata.orchestrator.core.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.apache.aiaravata.application.catalog.data.impl.AppCatalogFactory;
import org.apache.airavata.common.exception.AiravataException;
import org.apache.airavata.common.exception.ApplicationSettingsException;
import org.apache.airavata.common.utils.AiravataUtils;
import org.apache.airavata.common.utils.Constants;
import org.apache.airavata.common.utils.RequestData;
import org.apache.airavata.common.utils.ServerSettings;
import org.apache.airavata.commons.gfac.type.HostDescription;
import org.apache.airavata.credential.store.credential.AuditInfo;
import org.apache.airavata.credential.store.store.CredentialReader;
import org.apache.airavata.credential.store.store.CredentialReaderFactory;
import org.apache.airavata.gfac.monitor.util.CommonUtils;
import org.apache.airavata.model.appcatalog.computeresource.BatchQueue;
import org.apache.airavata.model.appcatalog.computeresource.ComputeResourceDescription;
import org.apache.airavata.model.appcatalog.computeresource.JobSubmissionInterface;
import org.apache.airavata.model.appcatalog.computeresource.SSHJobSubmission;
import org.apache.airavata.model.error.ValidatorResult;
import org.apache.airavata.model.workspace.experiment.ComputationalResourceScheduling;
import org.apache.airavata.model.workspace.experiment.Experiment;
import org.apache.airavata.model.workspace.experiment.TaskDetails;
import org.apache.airavata.model.workspace.experiment.WorkflowNodeDetails;
import org.apache.airavata.orchestrator.core.OrchestratorConfiguration;
import org.apache.airavata.orchestrator.core.context.OrchestratorContext;
import org.apache.airavata.orchestrator.core.exception.OrchestratorException;
import org.apache.airavata.orchestrator.core.impl.GFACEmbeddedJobSubmitter;
import org.apache.airavata.orchestrator.core.job.JobSubmitter;
import org.apache.airavata.orchestrator.cpi.Orchestrator;
import org.apache.airavata.orchestrator.cpi.impl.SimpleOrchestratorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This contains orchestrator specific utilities
 */
public class OrchestratorUtils {
    private final static Logger logger = LoggerFactory.getLogger(OrchestratorUtils.class);

    public static OrchestratorConfiguration loadOrchestratorConfiguration() throws OrchestratorException, IOException, NumberFormatException, ApplicationSettingsException {
        OrchestratorConfiguration orchestratorConfiguration = new OrchestratorConfiguration();
        orchestratorConfiguration.setNewJobSubmitterClass((String) ServerSettings.getSetting(OrchestratorConstants.JOB_SUBMITTER));
        orchestratorConfiguration.setSubmitterInterval(Integer.parseInt((String) ServerSettings.getSetting(OrchestratorConstants.SUBMIT_INTERVAL)));
        orchestratorConfiguration.setThreadPoolSize(Integer.parseInt((String) ServerSettings.getSetting(OrchestratorConstants.THREAD_POOL_SIZE)));
        orchestratorConfiguration.setStartSubmitter(Boolean.valueOf(ServerSettings.getSetting(OrchestratorConstants.START_SUBMITTER)));
        orchestratorConfiguration.setEmbeddedMode(Boolean.valueOf(ServerSettings.getSetting(OrchestratorConstants.EMBEDDED_MODE)));
        orchestratorConfiguration.setEnableValidation(Boolean.valueOf(ServerSettings.getSetting(OrchestratorConstants.ENABLE_VALIDATION)));
        if (orchestratorConfiguration.isEnableValidation()) {
            orchestratorConfiguration.setValidatorClasses(Arrays.asList(ServerSettings.getSetting(OrchestratorConstants.JOB_VALIDATOR).split(",")));
        }
        return orchestratorConfiguration;
    }

//    public static HostDescription getHostDescription(Orchestrator orchestrator, TaskDetails taskDetails)throws OrchestratorException {
//        JobSubmitter jobSubmitter = ((SimpleOrchestratorImpl) orchestrator).getJobSubmitter();
//        AiravataRegistry2 registry = ((GFACEmbeddedJobSubmitter) jobSubmitter).getOrchestratorContext().getRegistry();
//        ComputationalResourceScheduling taskScheduling = taskDetails.getTaskScheduling();
//        String resourceHostId = taskScheduling.getResourceHostId();
//        try {
//            return registry.getHostDescriptor(resourceHostId);
//        } catch (RegException e) {
//            throw new OrchestratorException(e);
//        }
//    }

    public  static String getCommunityUserName(Experiment experiment,
                                               ComputeResourceDescription computeResourceDes,
                                               TaskDetails taskID,
                                               String credStoreToken) throws AiravataException {
        ValidatorResult result;
        try {
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
                                return null;
                            case SSH:
                                SSHJobSubmission sshJobSubmission =
                                        AppCatalogFactory.getAppCatalog().getComputeResource().getSSHJobSubmission(
                                                jobSubmissionInterface.getJobSubmissionInterfaceId());
                                switch (sshJobSubmission.getSecurityProtocol()) {
                                    case GSI:
                                        // gsi
                                        RequestData requestData = new RequestData(ServerSettings.getDefaultUserGateway());
                                        requestData.setTokenId(credStoreToken);
                                        return requestData.getMyProxyUserName();
                                    case SSH_KEYS:
                                        CredentialReader credentialReader = CredentialReaderFactory.createCredentialStoreReader();
                                        AuditInfo auditInfo = credentialReader.getAuditInfo(experiment.getUserName(), credStoreToken);
                                        return auditInfo.getCommunityUser().getUserName();
                                    // ssh
                                    default:
                                        //nothing to do
                                }
                            default:
                                //nothing to do
                        }
                    }
                    return null;

                }// end of inner if
            }// end of outer if
            return null;
        } catch (Exception e) {
            throw new AiravataException("Exception while getting community user name ", e);
        }

    }

    public static boolean isJobSpaceAvailable(String communityUserName, String computeHostName, String queueName, int resourceMaxJobCount)
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
                return true;
            }
        } else {
            logger.info("Job count map doesn't has key : " + key);
            return true;
        }
        logger.info("Resource " + computeHostName + " doesn't has space to submit another job, " +
                "Configured resource max job count is " + resourceMaxJobCount + ".");
        return false;
    }


}
