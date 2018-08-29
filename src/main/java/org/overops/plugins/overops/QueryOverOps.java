/*
 * The MIT License
 *
 * Copyright (c) 2018, OverOps, Inc., Joe Offenberg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.overops.plugins.overops;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.util.Base64.getEncoder;

public class QueryOverOps extends Recorder implements SimpleBuildStep {

    private static final Pattern API_PATTERN = Pattern.compile("api.overops.com");
    private final String deploymentName;
    private final String applicationName;
    private final int maxEventCount;
    private final int maxNewEventCount;
    private final int queryLookback;
    private final int retryCount;
    private final int retryInt;
    private final boolean markUnstable;
    private final boolean showResults;

    @DataBoundConstructor
    public QueryOverOps(String applicationName, String deploymentName, int queryLookback, int RetryCount, int RetryInt,
                        int maxEventCount, int maxNewEventCount, boolean markUnstable, boolean showResults) {

        this.applicationName = applicationName;
        this.deploymentName = deploymentName;
        this.queryLookback = queryLookback;
        this.maxEventCount = maxEventCount;
        this.maxNewEventCount = maxNewEventCount;
        this.retryInt = RetryInt;
        this.retryCount = RetryCount;
        this.markUnstable = markUnstable;
        this.showResults = showResults;

    }

    public BuildStepMonitor getRequiredMonitorService() {
        // TODO Auto-generated method stub
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    // Job Plugin execution code


    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        // EnvVars gets Jenkins Build Vars
        final EnvVars env = run.getEnvironment(listener);

        // Get Global Plugin Config settings from Descriptor

        String deployNameEnv = env.expand(deploymentName);
        String overOpsURL = getDescriptor().getOverOpsURL();
        String overOpsSID = getDescriptor().getOverOpsSID();
        String overOpsUser = getDescriptor().getOverOpsUser();
        String overOpsPWD = Secret.toString(getDescriptor().getOverOpsPWD());
        String overOpsAPIKey = Secret.toString(getDescriptor().getOverOpsAPIKey());

        // if url is api.overops.com then replace with app.overops.com; otherwise leave alone
        String eventURL = API_PATTERN.matcher(overOpsURL).replaceAll("app.overops.com");

        // Time Formatter for OverOps REST API
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneId.of("UTC"));

        // Get Dates for OverOps REST API Calls

        Calendar now = Calendar.getInstance();
        now.setTime(new Date());
        now.add(Calendar.MINUTE, 10); // adds 10 minutes just in case time settings are off a little
        String toStamp = formatter.format(now.getTime().toInstant());

        Calendar before = Calendar.getInstance();
        before.setTime(new Date());
        before.add(Calendar.HOUR_OF_DAY, -queryLookback);
        String fromStamp = formatter.format(before.getTime().toInstant());

        Client apiClient = ClientBuilder.newClient();


        String usernameAndPassword = overOpsUser + ':' + overOpsPWD;
        String authorizationHeaderValue;
        String keyName;
        String viewId = null;
        int x = 1;

        if (overOpsAPIKey == null || overOpsAPIKey.isEmpty()) {
            keyName = "Authorization";
            authorizationHeaderValue = "Basic " + getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.UTF_8));
        } else {
            keyName = "X-API-Key";
            authorizationHeaderValue = overOpsAPIKey;
        }

        // Get View ID for All Events

        try {

            Response getViewResponse = apiClient
                    .target(overOpsURL)
                    .path("api/v1/services/" + overOpsSID + "/views")
                    .request(MediaType.APPLICATION_JSON)
                    .header(keyName, authorizationHeaderValue).get();

            if (getViewResponse.getStatus() == 200) {
                JSONObject getViewResult = getViewResponse.readEntity(JSONObject.class);
                JSONArray views = getViewResult.getJSONArray("views");

                for (int i = 0; i < views.size(); i++) {
                    if (views.getJSONObject(i).get("name").equals("All Events")) {
                        viewId = views.getJSONObject(i).getString("id");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        listener.getLogger().println("Checking OverOps for errors in deployment " + deployNameEnv);

        // Query OverOps for events matching app and deployment

        List<OOReportEvent> eventsList = new ArrayList<>();
        List<OOReportEvent> newEventsList = new ArrayList<>();

        while (x <= retryCount) {
            eventsList.clear();
            newEventsList.clear();
            listener.getLogger().println("\nOverOps Query  #" + x + " from OverOps Jenkins Plugin");
            listener.getLogger().println("Waiting " + retryInt + " seconds.");
            TimeUnit.SECONDS.sleep(retryInt);

            try {

                Response response = apiClient.target(overOpsURL)
                        .path("api/v1/services/" + overOpsSID + "/views/" + viewId + "/events")
                        .queryParam("from", fromStamp)
                        .queryParam("to", toStamp)
                        .queryParam("deployment", deployNameEnv)
                        .queryParam("app", applicationName)
                        .request(MediaType.APPLICATION_JSON)
                        .header(keyName, authorizationHeaderValue).get();

                if (showResults) {
                    listener.getLogger().println("API Call: " + response.toString() + '\n');
                }

                if (response.getStatus() == 200) {

                    JSONObject result = response.readEntity(JSONObject.class);
                    JSONArray events = result.getJSONArray("events");

                    if (showResults) {
                        listener.getLogger().println(events.size() + " Events");
                        listener.getLogger().println(result);
                    }

                    for (int i = 0; i < events.size(); i++) {

                        String eventId = events.getJSONObject(i).getString("id");
                        String tpkString = (overOpsSID + '#' + eventId + "#1");
                        String tpkLink = getEncoder().encodeToString(tpkString.getBytes(StandardCharsets.UTF_8));


                        eventsList.add(new OOReportEvent(events.getJSONObject(i).get("summary").toString(),
                                events.getJSONObject(i).get("introduced_by").toString(),
                                eventURL + "/tale.html?event=" + tpkLink));

                        if (deploymentName.equals(events.getJSONObject(i).get("introduced_by"))) {
                            newEventsList.add(new OOReportEvent(events.getJSONObject(i).get("summary").toString(),
                                    events.getJSONObject(i).get("introduced_by").toString(),
                                    eventURL + "/tale.html?event=" + tpkLink));
                        }

                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            // Validate Query results
            listener.getLogger().println("\n");
            listener.getLogger().println("OverOps found " + eventsList.size() + " events in " + applicationName + " deployment " + deployNameEnv);

            if (eventsList.size() > maxEventCount && maxEventCount != -1) {

                listener.getLogger().println("Event threshold " + maxEventCount + " Exceeded");

                if (markUnstable) {
                    listener.getLogger().println("OverOps Query results in Unstable build");
                    run.setResult(Result.UNSTABLE);
                }

                x = retryCount;
            }

            listener.getLogger().println("\n");
            listener.getLogger().println("OverOps found " + newEventsList.size() + " new events in " + applicationName + " deployment " + deployNameEnv);

            if (newEventsList.size() > maxNewEventCount && maxNewEventCount != -1) {

                listener.getLogger().println("New Event threshold " + maxNewEventCount + " Exceeded");

                if (markUnstable) {
                    listener.getLogger().println("OverOps Query results in Unstable build");
                    listener.getLogger().println("\n");
                    run.setResult(Result.UNSTABLE);
                }

                for (OOReportEvent event : newEventsList) {
                    listener.getLogger().println(event.getEventSummary() + " Introduced by:  " + event.getIntroducedBy() + "   " + event.getARCLink());
                }

                x = retryCount;
            }

            listener.getLogger().println("New Events Introduced by " + deployNameEnv + ": " + newEventsList.size());

            x++;

        }


        OverOpsBuildAction buildAction = new OverOpsBuildAction(eventsList, newEventsList, run);
        run.addAction(buildAction);

        listener.getLogger().println();
        listener.getLogger().println("Total Events found in OverOps for build " + deployNameEnv + ": " + eventsList.size());
        listener.getLogger().println("New Events Introduced by " + deployNameEnv + ": " + newEventsList.size());
    }

}
