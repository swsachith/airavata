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
package org.apache.airavata.orchestrator.cpi.impl;

import org.apache.airavata.common.exception.AiravataException;
import org.apache.airavata.common.utils.Constants;
import org.apache.airavata.common.utils.ServerSettings;
import org.apache.airavata.gfac.monitor.util.CommonUtils;
import org.apache.airavata.model.appcatalog.computeresource.BatchQueue;
import org.apache.airavata.model.appcatalog.computeresource.ComputeResourceDescription;
import org.apache.airavata.model.error.LaunchValidationException;
import org.apache.airavata.model.error.ValidationResults;
import org.apache.airavata.model.error.ValidatorResult;
import org.apache.airavata.model.util.ExperimentModelUtil;
import org.apache.airavata.model.workspace.experiment.*;
import org.apache.airavata.orchestrator.core.context.OrchestratorContext;
import org.apache.airavata.orchestrator.core.exception.OrchestratorException;
import org.apache.airavata.orchestrator.core.job.JobSubmitter;
import org.apache.airavata.orchestrator.core.utils.OrchestratorUtils;
import org.apache.airavata.orchestrator.core.validator.JobMetadataValidator;
import org.apache.airavata.orchestrator.core.validator.impl.JobCountValidator;
import org.apache.airavata.registry.cpi.ChildDataType;
import org.apache.airavata.registry.cpi.Registry;
import org.apache.airavata.registry.cpi.RegistryModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class SimpleOrchestratorImpl extends AbstractOrchestrator{
    private final static Logger logger = LoggerFactory.getLogger(SimpleOrchestratorImpl.class);
    private ExecutorService executor;
    
    // this is going to be null unless the thread count is 0
    private JobSubmitter jobSubmitter = null;
    private List<JobMetadataValidator> validatorList = new ArrayList<JobMetadataValidator>();

    public SimpleOrchestratorImpl() throws OrchestratorException {
        init();
    }

    public void init() throws OrchestratorException {
        try {
            loadSubmitter();
            loadValidators();
        } catch (OrchestratorException e) {
            logger.error("Error Constructing the Orchestrator");
            throw e;
        }
    }

    private void loadValidators() throws OrchestratorException {
        List<String> validatorClzzez = this.orchestratorContext.getOrchestratorConfiguration().getValidatorClasses();
        Class<? extends JobMetadataValidator> vClass;
        boolean allValidatorsFailed = true;
        for (String validator : validatorClzzez) {
            try {
                vClass = Class.forName(validator.trim()).asSubclass(JobMetadataValidator.class);
                validatorList.add(vClass.newInstance());
                allValidatorsFailed = false;
            } catch (Exception e) {
                logger.warn("Error loading the validation class: " + validator, e);
            }
        }
        if (allValidatorsFailed && validatorClzzez.size() > 0) {
            throw new OrchestratorException("Error loading all JobMetadataValidator implementation classes");
        }
    }

    private void loadSubmitter() throws OrchestratorException {
        try {
            String submitterClass = this.orchestratorContext.getOrchestratorConfiguration().getNewJobSubmitterClass();
            Class<? extends JobSubmitter> aClass = Class.forName(submitterClass.trim()).asSubclass(JobSubmitter.class);
            jobSubmitter = aClass.newInstance();
            jobSubmitter.initialize(this.orchestratorContext);

        } catch (Exception e) {
            String error = "Error creating JobSubmitter in non threaded mode ";
            throw new OrchestratorException(error, e);
        }
    }

    public boolean launchExperiment(Experiment experiment, WorkflowNodeDetails workflowNode, TaskDetails task,
                                    String tokenId) throws OrchestratorException {
        // we give higher priority to userExperimentID
        String experimentId = experiment.getExperimentID();
        String taskId = task.getTaskID();
        // creating monitorID to register with monitoring queue
        // this is a special case because amqp has to be in place before submitting the job
        try {
            if (ServerSettings.getEnableJobRestrictionValidation().equals("true") &&
                    task.getTaskScheduling().getQueueName() != null) {
                ComputeResourceDescription computeResourceDes = CommonUtils.getComputeResourceDescription(task);
                String communityUserName = OrchestratorUtils.getCommunityUserName(experiment, computeResourceDes, task,
                        tokenId);
                BatchQueue batchQueue = CommonUtils.getBatchQueueByName(computeResourceDes.getBatchQueues(),
                        task.getTaskScheduling().getQueueName());

                synchronized (this) {
                    boolean spaceAvaialble = OrchestratorUtils.isJobSpaceAvailable(communityUserName,
                            computeResourceDes.getHostName(), batchQueue.getQueueName(), batchQueue.getMaxJobsInQueue());
                    if (spaceAvaialble) {
                        if (jobSubmitter.submit(experimentId, taskId, tokenId)) {
                            logger.info("Job submitted, experiment Id : " + experimentId + " , task Id : " + taskId);
                            Map<String, Integer> jobUpdateMap = new HashMap<String, Integer>();
                            StringBuilder sb = new StringBuilder("/").append(Constants.STAT).append("/")
                                    .append(communityUserName).append("/").append(computeResourceDes.getHostName())
                                    .append("/").append(Constants.JOB).append("/").append(batchQueue.getQueueName());
                            jobUpdateMap.put(sb.toString(), 1);
                            CommonUtils.updateZkWithJobCount(OrchestratorContext.getZk(), jobUpdateMap, true); // update change job count to zookeeper
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        throw new AiravataException("Please honour to the max job submission restriction policy," +
                                " max count is " + batchQueue.getMaxJobsInQueue());
                    }
                }// end of synchronized block
            } else {
                logger.info("Ignored job throttling");
                return jobSubmitter.submit(experimentId, taskId, tokenId);
            }
        } catch (Exception e) {
            throw new OrchestratorException("Error launching the job", e);
        }
    }

    /**
     * This method will parse the ExperimentConfiguration and based on the configuration
     * we create a single or multiple tasks for the experiment.
     *
     * @param experimentId
     * @return
     * @throws OrchestratorException
     */
    public List<TaskDetails> createTasks(String experimentId) throws OrchestratorException {
        Experiment experiment = null;
        List<TaskDetails> tasks = new ArrayList<TaskDetails>();
        try {
            Registry newRegistry = orchestratorContext.getNewRegistry();
            experiment = (Experiment) newRegistry.get(RegistryModelType.EXPERIMENT, experimentId);


            WorkflowNodeDetails iDontNeedaNode = ExperimentModelUtil.createWorkflowNode("IDontNeedaNode", null);
            String nodeID = (String) newRegistry.add(ChildDataType.WORKFLOW_NODE_DETAIL, iDontNeedaNode, experimentId);

            TaskDetails taskDetails = ExperimentModelUtil.cloneTaskFromExperiment(experiment);
            taskDetails.setTaskID((String) newRegistry.add(ChildDataType.TASK_DETAIL, taskDetails, nodeID));
            tasks.add(taskDetails);
        } catch (Exception e) {
            throw new OrchestratorException("Error during creating a task");
        }
        return tasks;
    }

    public ValidationResults validateExperiment(Experiment experiment, WorkflowNodeDetails workflowNodeDetail,
                                                TaskDetails taskID , String airavataCredStoreToken) throws OrchestratorException,LaunchValidationException {
        org.apache.airavata.model.error.ValidationResults validationResults = new org.apache.airavata.model.error.ValidationResults();
        validationResults.setValidationState(true); // initially making it to success, if atleast one failed them simply mark it failed.
        if (this.orchestratorConfiguration.isEnableValidation()) {
            ValidatorResult vResult = null;
            for (JobMetadataValidator jobMetadataValidator : validatorList) {
                    vResult = jobMetadataValidator.validate(experiment, workflowNodeDetail, taskID, airavataCredStoreToken);
                    if (vResult.isResult()) {
                        logger.info("Validation of " + jobMetadataValidator.getClass().getName() + " is SUCCESSFUL");
                    } else {
                        logger.error("Validation of " + jobMetadataValidator.getClass().getName() + " is FAILED:[error]" + vResult.getErrorDetails());
                        //todo we need to store this message to registry
                        validationResults.setValidationState(false);
                        // we do not return immediately after the first failure
                    }
                validationResults.addToValidationResultList(vResult);
            }
        }
        if(validationResults.isValidationState()){
            return validationResults;
        }else {
            //atleast one validation has failed, so we throw an exception
            LaunchValidationException launchValidationException = new LaunchValidationException();
            launchValidationException.setValidationResult(validationResults);
            launchValidationException.setErrorMessage("Validation failed refer the validationResults list for detail error");
            throw launchValidationException;
        }
    }

    public void cancelExperiment(Experiment experiment, WorkflowNodeDetails workflowNode, TaskDetails task, String tokenId)
            throws OrchestratorException {
        List<JobDetails> jobDetailsList = task.getJobDetailsList();
        for(JobDetails jobDetails:jobDetailsList) {
            JobState jobState = jobDetails.getJobStatus().getJobState();
            if (jobState.getValue() > 4){
                logger.error("Cannot cancel the job, because current job state is : " + jobState.toString() +
                "jobId: " + jobDetails.getJobID() + " Job Name: " + jobDetails.getJobName());
                return;
            }
        }
        jobSubmitter.terminate(experiment.getExperimentID(),task.getTaskID());
    }


    public ExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public JobSubmitter getJobSubmitter() {
        return jobSubmitter;
    }

    public void setJobSubmitter(JobSubmitter jobSubmitter) {
        this.jobSubmitter = jobSubmitter;
    }

    public void initialize() throws OrchestratorException {

    }

}
