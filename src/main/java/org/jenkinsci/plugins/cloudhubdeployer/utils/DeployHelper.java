package org.jenkinsci.plugins.cloudhubdeployer.utils;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jenkinsci.plugins.cloudhubdeployer.CloudHubDeployer;
import org.jenkinsci.plugins.cloudhubdeployer.CloudHubRequest;
import org.jenkinsci.plugins.cloudhubdeployer.common.ApiStatus;
import org.jenkinsci.plugins.cloudhubdeployer.common.RequestMode;
import org.jenkinsci.plugins.cloudhubdeployer.exception.CloudHubRequestException;
import hudson.FilePath;
import org.jenkinsci.plugins.cloudhubdeployer.data.*;
import org.jenkinsci.plugins.cloudhubdeployer.exception.ValidationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DeployHelper {

    public static AppInfoJson buildAppInfoJson(CloudHubDeployer cloudhubDeployer) throws ValidationException {

        validate(cloudhubDeployer);

        AppInfoJson appInfoJson = new AppInfoJson();
        Map<String, String> properties = new HashMap<>();
        List<LogLevel> logLevelList = new ArrayList<>();
        Workers workers = new Workers();
        Type type = new Type();


        for (EnvVars envVars: cloudhubDeployer.getEnvVars()) {
            properties.put(envVars.getKey(),envVars.getValue());
        }

        for (LogLevels logLevels: cloudhubDeployer.getLogLevels()) {
            LogLevel logLevel = new LogLevel();
            logLevel.setLevel(logLevels.getLevelCategory().toString());
            logLevel.setPackageName(logLevels.getPackageName());
            logLevelList.add(logLevel);
        }


        type.setCpu(formatWorkerCpuCapacity(cloudhubDeployer.getWorkerCpu()));
        type.setMemory(formatWorkerMemory(cloudhubDeployer.getWorkerMemory()));
        type.setName(cloudhubDeployer.getWorkerType());
        type.setWeight(Double.parseDouble(cloudhubDeployer.getWorkerWeight()));
        workers.setType(type);
        workers.setAmount(cloudhubDeployer.getWorkerAmount());

        appInfoJson.setDomain(cloudhubDeployer.getAppName());
        appInfoJson.setMuleVersion(new MuleVersion(cloudhubDeployer.getMuleVersion()));
        appInfoJson.setRegion(cloudhubDeployer.getRegion());
        appInfoJson.setMonitoringEnabled(cloudhubDeployer.isMonitoringEnabled());
        appInfoJson.setMonitoringAutoRestart(cloudhubDeployer.isMonitoringAutoRestart());
        appInfoJson.setLoggingNgEnabled(cloudhubDeployer.isLoggingNgEnabled());
        appInfoJson.setPersistentQueues(cloudhubDeployer.isPersistentQueues());
        appInfoJson.setObjectStoreV1(cloudhubDeployer.isObjectStoreV1());
        appInfoJson.setPersistentQueuesEncrypted(cloudhubDeployer.isPersistentQueuesEncrypted());
        appInfoJson.setWorkers(workers);
        appInfoJson.setProperties(properties);
        appInfoJson.setLogLevels(logLevelList);

        return appInfoJson;
    }

    private static void validate(CloudHubDeployer cloudhubDeployer) throws ValidationException {

            if(Strings.isNullOrEmpty(cloudhubDeployer.getWorkerCpu())){
                throw new ValidationException("Please enter Worker Cpu");
            }

            if(Strings.isNullOrEmpty(cloudhubDeployer.getWorkerMemory())){
                throw new ValidationException("Please enter Worker Memory");
            }

            if(Strings.isNullOrEmpty(cloudhubDeployer.getWorkerType())){
                throw new ValidationException("Please enter Worker Type");
            }

            if(Strings.isNullOrEmpty(cloudhubDeployer.getWorkerWeight())){
                throw new ValidationException("Please enter Worker Weight");
            }

            if(cloudhubDeployer.getWorkerAmount() == 0){
                throw new ValidationException("Worker Amount can not be zero");
            }

            if(Strings.isNullOrEmpty(cloudhubDeployer.getAppName())){
                throw new ValidationException("Please enter Application Name");
            }

            if(Strings.isNullOrEmpty(cloudhubDeployer.getMuleVersion())){
                throw new ValidationException("Please enter Mule Version");
            }

            if(Strings.isNullOrEmpty(cloudhubDeployer.getRegion())){
                throw new ValidationException("Please enter Region");
            }

    }


    private static String formatWorkerCpuCapacity(String cpuCapacity){
        String finalCpuCapacity = cpuCapacity;
        if (!cpuCapacity.contains(Constants.SUFFIX_WORKER_CPU)) {
            finalCpuCapacity += Constants.SUFFIX_WORKER_CPU;
        }
        return finalCpuCapacity;
    }

    private static String formatWorkerMemory(String memoryCapacity) throws ValidationException {
        String finalMemoryCapacity;
        if (!(memoryCapacity.contains(" MB") ||
                memoryCapacity.contains(" GB"))) {
            throw new ValidationException("Invalid Worker Memory format");
        }

        finalMemoryCapacity = memoryCapacity.concat(Constants.SUFFIX_WORKER_MEMORY);

        return finalMemoryCapacity;
    }

    public static String getArtifactPath(FilePath workspace, String filePath) throws CloudHubRequestException {

        FilePath[] filePaths;
        try {

            if(null == filePath)
                return null;

            filePaths = workspace.list(filePath);

            if(filePaths.length < 1){
                throw new CloudHubRequestException("No artifact file found to deploy.");
            }

            if(filePaths.length > 1){
                throw new CloudHubRequestException("Multiple artifact file were found with provided filter.");
            }

            return filePaths[0].getRemote();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean checkPattern(String test, String regExPattern) {
        Pattern pattern = Pattern.compile(regExPattern);
        Matcher matcher = pattern.matcher(test);
        return matcher.matches();
    }

    public static void logOutputStandard(PrintStream logger, String output){
        logger.println();
        logger.println(output);
        logger.println();
    }

    public static void logOutputStandard(PrintStream logger,String headline ,String output){
        logger.println();
        logger.println(headline);
        logger.println(output);
        logger.println();
    }

    public static RequestMode getFinalRequestMode(CloudHubRequest cloudHubRequest) throws CloudHubRequestException {
        if(cloudHubRequest.getRequestMode().compareTo(RequestMode.CREATE_OR_UPDATE) == 0){
            String cloudhubResponseBody = CloudHubRequestUtils.apiList(cloudHubRequest);

            boolean isApiPresent = JsonHelper.checkIfApiExists(cloudhubResponseBody,cloudHubRequest.getApiDomainName());

            if(isApiPresent){
                return  RequestMode.UPDATE;
            }else {
                return RequestMode.CREATE;
            }
        }

        return cloudHubRequest.getRequestMode();
    }

    public static boolean checkIfApiStarted(CloudHubRequest cloudHubRequest, PrintStream logger, int verifyIntervalInSeconds)
            throws CloudHubRequestException, InterruptedException {

        boolean isDeploymentInProgress = true;
        boolean isApiStarted = false;

        Thread.sleep(Constants.DEFAULT_VERIFY_INITIAL_DELAY);
        if(cloudHubRequest.getRequestMode().equals(RequestMode.CREATE)){
            while (isDeploymentInProgress) {
                String apiStatus = CloudHubRequestUtils.apiStatus(cloudHubRequest);
                String status = new Gson().fromJson(apiStatus, JsonObject.class)
                        .get(Constants.LABEL_API_STATUS).getAsString();
                if(status.equals(ApiStatus.STARTED.toString())){
                    isDeploymentInProgress = false;
                    isApiStarted = true;
                    logger.println("API deployment is completed");
                }else if(status.equals(ApiStatus.DEPLOYING.toString())){
                    logger.println("API deployment is in progress");
                    Thread.sleep(verifyIntervalInSeconds);
                }else {
                    logger.println("Deployment failed. Please review the logs");
                }

            }
        }else {
            while (isDeploymentInProgress) {
                String apiStatus = CloudHubRequestUtils.apiStatus(cloudHubRequest);
                if (JsonHelper.checkIfKeyExists(apiStatus, Constants.LABEL_DEPLOYMENT_UPDATE_STATUS)) {
                    String deploymentUpdateStatus = new Gson().fromJson(apiStatus, JsonObject.class)
                            .get(Constants.LABEL_DEPLOYMENT_UPDATE_STATUS).getAsString();
                    logger.println("API update is in progress");
                    Thread.sleep(verifyIntervalInSeconds);
                }else {
                    String status = new Gson().fromJson(apiStatus, JsonObject.class)
                            .get(Constants.LABEL_API_STATUS).getAsString();
                    if(status.equals(ApiStatus.STARTED.toString())){
                        isApiStarted = true;
                        logger.println("API update is completed");
                    }else {
                        logger.println("Update failed. Please review the logs");
                    }
                    isDeploymentInProgress = false;
                }

            }
        }

        return isApiStarted;
    }

    public static void validateAutoScalePolicy(List<AutoScalePolicy> policy) throws ValidationException {

        AutoScalePolicy autoScalePolicy = policy.get(0);

        if(Strings.isNullOrEmpty(autoScalePolicy.getAutoScalePolicyName())){
            throw new ValidationException("Please enter AutoScale Policy Name");
        }
    }

    public static boolean checkIfAutoScalePolicyExists(CloudHubRequest cloudHubRequest, PrintStream logger) throws CloudHubRequestException {
        String cloudhubResponseBody = CloudHubRequestUtils.getAutoScalePolicy(cloudHubRequest);

        logger.println(cloudhubResponseBody);
        return false;
    }
}
