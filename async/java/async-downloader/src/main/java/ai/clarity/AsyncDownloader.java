package ai.clarity;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncDownloader {

    private static final short DEFAULT_WAITING_MILISECS = 30 * 1000;
    private static final Logger logger = Logger.getLogger(AsyncDownloader.class.getName());

    private String token;
    private final String domain;
    private final String key;
    private final String secret;

    private final int waitingTime;

    public AsyncDownloader(String domain, String key, String secret, int waitingTime) {
        this.domain = domain;
        this.key = key;
        this.secret = secret;
        this.waitingTime = waitingTime;
    }

    public AsyncDownloader(String domain, String key, String secret) {
        this(domain, key, secret, DEFAULT_WAITING_MILISECS);
    }

    public String requestNewToken() {
        Map<String, String> headers = Map.of("Content-type", "application/json");

        Map<String, Object> bodyParams = Map.of("key", this.key, "secret", this.secret);
        var jsonBody = HttpRequestHelper.mapToJson(bodyParams).get();

        var url = domain + "/clarity/v1/oauth/token";
        logger.log(Level.INFO, "Requesting new token to " + url);
        Optional<String> response = HttpRequestHelper.postRequest(url, headers, jsonBody);
        if(response.isEmpty()){
            throw new RuntimeException("Couldn't request the access token");
        }
        Map<String, Object> responseMap = HttpRequestHelper.jsonToMap(response.get()).get();
        return (String) responseMap.get("token");
    }

    public String requestAsync(String apiPath, Map<String, Object> data) {
        String url = getUrl(apiPath);
        Map<String, String> headers = getHeaders();
        String jsonData = HttpRequestHelper.mapToJson(data).get();

        logger.log(Level.INFO, "Requesting Job to " + url + " with data " + jsonData);

        var response = HttpRequestHelper.postRequest(url, headers, jsonData);

        String jobId = null;
        if(response.isPresent()) {
            Map<String, Object> responseMap = HttpRequestHelper.jsonToMap(response.get()).get();

            if(responseMap.containsKey("uuid")) {
                jobId = (String) responseMap.get("uuid");
            }
        }

        if(jobId == null) {
            throw new RuntimeException("The request for an async job failed");
        }
        else {
            logger.info("Requested Job with UUID:" + jobId);
            return jobId;
        }

    }

    public void waitForJob(String jobId) {
        logger.log(Level.INFO, "Waiting for job with id " + jobId + " to finish...");
        String status = "RUNNING";
        while(status.equals("RUNNING")) {
            try {
                Thread.sleep(this.waitingTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Map<String, String> headers = getHeaders();
            Optional<String> response = HttpRequestHelper.getRequest(getUrl("/job/" + jobId + "/status"), headers);
            if(response.isPresent()) {
                Map<String, Object> responseMap = HttpRequestHelper.jsonToMap(response.get()).get();
                status = (String) responseMap.get("statusMessage");
            }
            else {
                throw new RuntimeException("Error requesting the status of the job " + jobId);
            }
        }

        if(!status.equals("RUNNING") && !status.equals("SUCCESS")) {
            throw new RuntimeException("The job " + jobId + " didn't finish correctly. Status: " + status);
        }

        logger.log(Level.INFO, "The job with id " + jobId + " finished correctly");
    }

    private String getToken() {
        if(this.token == null) {
            this.token = requestNewToken();
        }
        return this.token;
    }

    private Map<String, String> getHeaders() {
        return Map.of("Content-Type", "application/json",
                      "Authorization", "Bearer " + getToken());
    }

    private String getUrl(String apiPath) {
        return domain + "/clarity/v1/public" + apiPath;
    }
}
