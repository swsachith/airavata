package org.apache.airavata.orchestrator.core.validator.impl;

import org.airavata.appcatalog.cpi.AppCatalog;
import org.airavata.appcatalog.cpi.AppCatalogException;
import org.airavata.appcatalog.cpi.ComputeResource;
import org.apache.aiaravata.application.catalog.data.impl.AppCatalogFactory;
import org.apache.aiaravata.application.catalog.data.resources.AbstractResource;
import org.apache.airavata.common.exception.ApplicationSettingsException;
import org.apache.airavata.common.utils.AiravataUtils;
import org.apache.airavata.common.utils.Constants;
import org.apache.airavata.common.utils.RequestData;
import org.apache.airavata.common.utils.ServerSettings;
import org.apache.airavata.credential.store.credential.AuditInfo;
import org.apache.airavata.credential.store.store.CredentialReader;
import org.apache.airavata.credential.store.store.CredentialReaderFactory;
import org.apache.airavata.credential.store.store.CredentialStoreException;
import org.apache.airavata.credential.store.util.TokenizedMyProxyAuthInfo;
import org.apache.airavata.gfac.core.scheduler.HostScheduler;
import org.apache.airavata.model.appcatalog.appdeployment.ApplicationDeploymentDescription;
import org.apache.airavata.model.appcatalog.appinterface.ApplicationInterfaceDescription;
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
import org.apache.airavata.registry.cpi.RegistryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
            AppCatalog appCatalog = AppCatalogFactory.getAppCatalog();
            ApplicationInterfaceDescription applicationInterface = appCatalog.getApplicationInterface().
                    getApplicationInterface(taskID.getApplicationId());

            List<String> applicationModules = applicationInterface.getApplicationModules();
            String selectedModuleId = applicationModules.get(0);
            Map<String, String> moduleIdFilter = new HashMap<String, String>();
            moduleIdFilter.put(AbstractResource.ApplicationDeploymentConstants.APP_MODULE_ID, selectedModuleId);
            if (taskID.getTaskScheduling()!=null && taskID.getTaskScheduling().getResourceHostId() != null) {
                moduleIdFilter.put(AbstractResource.ApplicationDeploymentConstants.COMPUTE_HOST_ID,
                        taskID.getTaskScheduling().getResourceHostId());
            }
            List<ApplicationDeploymentDescription> applicationDeployements = appCatalog.getApplicationDeployment()
                    .getApplicationDeployements(moduleIdFilter);
            Map<ComputeResourceDescription, ApplicationDeploymentDescription> deploymentMap =
                    new HashMap<ComputeResourceDescription, ApplicationDeploymentDescription>();
            ComputeResource computeResource = appCatalog.getComputeResource();
            for (ApplicationDeploymentDescription deploymentDescription : applicationDeployements) {
                deploymentMap.put(computeResource.getComputeResource(deploymentDescription.getComputeHostId()),
                        deploymentDescription);
            }
            List<ComputeResourceDescription> computeHostList = new ArrayList<ComputeResourceDescription>();
            computeHostList.addAll(deploymentMap.keySet());

            Class<? extends HostScheduler> aClass = Class.forName(
                    ServerSettings.getHostScheduler()).asSubclass(
                    HostScheduler.class);
            HostScheduler hostScheduler = aClass.newInstance();
            ComputeResourceDescription ComputeResourceDescription = hostScheduler.schedule(computeHostList);
            ApplicationDeploymentDescription applicationDeploymentDescription = deploymentMap.get(ComputeResourceDescription);

            ComputeResourceDescription computeResourceDescription = appCatalog.getComputeResource().
                    getComputeResource(applicationDeploymentDescription.getComputeHostId());
            for (JobSubmissionInterface jobSubmissionInterface : computeResourceDescription.getJobSubmissionInterfaces()) {
                switch (jobSubmissionInterface.getJobSubmissionProtocol()) {
                    case LOCAL:
                        // nothing to do
                        return new ValidatorResult(true);
                    case SSH:
                        SSHJobSubmission sshJobSubmission =
                                appCatalog.getComputeResource().getSSHJobSubmission(jobSubmissionInterface.getJobSubmissionInterfaceId());
                        switch (sshJobSubmission.getSecurityProtocol()) {
                            case GSI:
                                // gsi
                                RequestData requestData = new RequestData(ServerSettings.getDefaultUserGateway());
                                requestData.setTokenId(credStoreToken);
                                if (isJobSpaceAvailable(requestData.getMyProxyUserName(), computeHostList)) {
                                    return new ValidatorResult(true);
                                } else {
                                    result = new ValidatorResult(false);
                                    result.setErrorDetails("Please honour to the gobal max job count " + ServerSettings.getGlobalMaxJobCount());
                                    return result;
                                }
//                                TokenizedMyProxyAuthInfo tokenizedMyProxyAuthInfo = new TokenizedMyProxyAuthInfo(requestData);
                            case SSH_KEYS:
                                result = new ValidatorResult(false);
                                result.setErrorDetails("SSH_KEY base job count validation is not yet implemented");
                                return result;
                                // ssh
                            default:
                                result = new ValidatorResult(false);
                                result.setErrorDetails("Doesn't support " + sshJobSubmission.getSecurityProtocol() + " protocol yet");
                                return result;
                        }
                    default:
                        result = new ValidatorResult(false);
                        result.setErrorDetails("Doesn't support " + jobSubmissionInterface.getJobSubmissionProtocol() + " protocol yet");
                        return result;
                }
            }
            result = new ValidatorResult(false);
            result.setErrorDetails("No JobSubmission interface found");
            return result;
        } catch (Exception e) {
            result = new ValidatorResult(false);
            result.setErrorDetails("Exception occur while running validation process ");
            return result;
        }

    }

    private boolean isJobSpaceAvailable(String communityUserName, List<ComputeResourceDescription> computeHostList) throws ApplicationSettingsException {
        String keyPath = new StringBuilder("/" + Constants.STAT).append("/").append(communityUserName).append("/").toString();
        for (ComputeResourceDescription computeResDesc : computeHostList) {
            String key = keyPath + computeResDesc.getHostName() + "/" + Constants.JOB;
            Map<String, Integer> jobCountMap = AiravataUtils.getJobCountMap(OrchestratorContext.getZk());
            if (jobCountMap.containsKey(key)) {
                int count = jobCountMap.get(key);
                if (count < Integer.parseInt(ServerSettings.getGlobalMaxJobCount())) {
                    return true;
                }
            }else {
                return true;
            }
        }
        return false;
    }

    private void getAppDeployment(String applicationId, TaskDetail taskData) throws AppCatalogException {
        return;

    }

    private ApplicationDeploymentDescription getAppDeployment(AppCatalog appCatalog, TaskDetail taskData, String selectedModuleId) {
        return null;
    }
}
