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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import hudson.Launcher;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.util.Secret;

import org.kohsuke.stapler.DataBoundConstructor;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class QueryOverOps extends hudson.tasks.Recorder implements SimpleBuildStep {

	private final String deployName;
	private final String OOappName;
	private final int maxEventCount;
	private final int maxNewEventCount;
	private final int queryLookback;
	private final int RetryCount;
	private final int RetryInt;
	private final boolean markUnstable;
	private final boolean showResults;

	@DataBoundConstructor
	public QueryOverOps(String OOappName, String deployName, int queryLookback, int RetryCount, int RetryInt,
			int maxEventCount, int maxNewEventCount, boolean markUnstable, boolean showResults) {

		this.OOappName = OOappName;
		this.deployName = deployName;
		this.queryLookback = queryLookback;
		this.maxEventCount = maxEventCount;
		this.maxNewEventCount = maxNewEventCount;
		this.RetryInt = RetryInt;
		this.RetryCount = RetryCount;
		this.markUnstable = markUnstable;
		this.showResults = showResults;

	}

	public String getOOappName() {
		return OOappName;
	}

	public String getdeployName() {
		return deployName;
	}

	public int getqueryLookback() {
		return queryLookback;
	}

	public int getmaxEventCount() {
		return maxEventCount;
	}

	public int getmaxNewEventCount() {
		return maxNewEventCount;
	}

	public int getRetryInt() {
		return RetryInt;
	}

	public int getRetryCount() {
		return RetryCount;
	}

	public boolean getmarkUnstable() {
		return markUnstable;
	}

	public boolean getshowResults() {
		return showResults;
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
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
	 
	
			throws InterruptedException, IOException {

		// EnvVars gets Jenkins Build Vars
		final EnvVars env = run.getEnvironment(listener);

		// Get Global Plugin Config settings from Descriptor

		String deployNameEnv = env.expand(deployName);
		String OverOpsURL = getDescriptor().getOverOpsURL();
		String OverOpsSID = getDescriptor().getOverOpsSID();
		String OverOpsUser = getDescriptor().getOverOpsUser();
		String OverOpsPWD = Secret.toString(getDescriptor().getOverOpsPWD());
		String OOAPIKey = Secret.toString(getDescriptor().getOOAPIKey());

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

		Client OverOpsApiClient = ClientBuilder.newClient();

		ArrayList<OOReportEvent> EventList = new ArrayList<OOReportEvent>();
		ArrayList<OOReportEvent> NewEventList = new ArrayList<OOReportEvent>();
		String usernameAndPassword = OverOpsUser + ":" + OverOpsPWD;
		String authorizationHeaderValue;
		String keyName;
		String ViewID = null;
		int x = 1;

		if (OOAPIKey == null || OOAPIKey.isEmpty()) {
			keyName = "Authorization";
			authorizationHeaderValue = "Basic "
					+ java.util.Base64.getEncoder().encodeToString(usernameAndPassword.getBytes("utf-8"));
		} else {
			keyName = "X-API-Key";
			authorizationHeaderValue = OOAPIKey;
		}
		
		// Get View ID for All Events
		
		try {

			Response getViewResponse = OverOpsApiClient.target(OverOpsURL)
					.path("api/v1/services/" + OverOpsSID + "/views").request(MediaType.APPLICATION_JSON)
					.header(keyName, authorizationHeaderValue).get();

			if (getViewResponse.getStatus() == 200) {
				JSONObject getViewResult = getViewResponse.readEntity(JSONObject.class);
				JSONArray ViewsArray = getViewResult.getJSONArray("views");
				for (int i = 0; i < ViewsArray.size(); i++) {
					// listener.getLogger().println(ViewsArray.getJSONObject(i).get("name"));
					if (ViewsArray.getJSONObject(i).get("name").equals("All Events")) {
						ViewID = ViewsArray.getJSONObject(i).getString("id");
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	
		listener.getLogger().println("Checking OverOps for errors in deployment " + deployNameEnv);

		// Query OverOps for events matching app and deployment
		while (x <= RetryCount) {
			EventList.clear();
			NewEventList.clear();
			listener.getLogger().println("\nOverOps Query  #" + x + " from OverOps Jenkins Plugin");
			listener.getLogger().println("Waiting " + RetryInt + " seconds.");
			TimeUnit.SECONDS.sleep(RetryInt);

			try {

				Response response = OverOpsApiClient.target(OverOpsURL)
						.path("api/v1/services/" + OverOpsSID + "/views/" + ViewID + "/events")
						.queryParam("from", fromStamp).queryParam("to", toStamp)
						.queryParam("deployment", deployNameEnv).queryParam("app", OOappName)
						.request(MediaType.APPLICATION_JSON).header(keyName, authorizationHeaderValue).get();
				if (showResults == true) {
					listener.getLogger().println("API Call: " + response.toString() + "\n");
				}
				if (response.getStatus() == 200) {
					
					JSONObject result = response.readEntity(JSONObject.class);
					JSONArray events = result.getJSONArray("events");
					if (showResults == true) {

						listener.getLogger().println(events.size() + " Events");
						listener.getLogger().println(result);
						
					}

					for (int i = 0; i < events.size(); i++) {
												
								String eventsID = events.getJSONObject(i).getString("id");
								String tpkString = (OverOpsSID + "#" + eventsID + "#1");
								String tpkLink = java.util.Base64.getEncoder().encodeToString(tpkString.getBytes("utf-8"));
								String eventOOURL = OverOpsURL.replaceAll("api.overops.com", "app.overops.com");
								EventList.add(new OOReportEvent(events.getJSONObject(i).get("summary").toString(),events.getJSONObject(i).get("introduced_by").toString(),eventOOURL + "/tale.html?event="
										+ tpkLink));
								if (deployName.equals(events.getJSONObject(i).get("introduced_by"))) {
									NewEventList.add(new OOReportEvent(events.getJSONObject(i).get("summary").toString(),events.getJSONObject(i).get("introduced_by").toString(),eventOOURL + "/tale.html?event="
											+ tpkLink));
								}

							}
						}
					
				}
			 catch (Exception e) {
				e.printStackTrace();
			}
			// Validate Query results
			listener.getLogger().println("\n");
			listener.getLogger().println(
					"OverOps found " + EventList.size() + " events in " + OOappName + " deployment " + deployNameEnv);
			if (EventList.size() > maxEventCount && maxEventCount != -1) {
				
				listener.getLogger().println("Event threshold " + maxEventCount + " Exceeded");
				if (markUnstable == true) {
					listener.getLogger().println("OverOps Query results in Unstable build");
					run.setResult(hudson.model.Result.UNSTABLE);
				}

//				for (int i = 0; i < EventList.size(); i++) {
//					listener.getLogger().println(EventList.get(i).geteventSummary());
//				}
				x = RetryCount;
			}
			listener.getLogger().println("\n");
			listener.getLogger().println(
					"OverOps found " + NewEventList.size() + " new events in " + OOappName + " deployment " + deployNameEnv);
			if (NewEventList.size() > maxNewEventCount && maxNewEventCount != -1) {
				
				listener.getLogger().println("New Event threshold " + maxNewEventCount + " Exceeded");
				if (markUnstable == true) {
					
					listener.getLogger().println("OverOps Query results in Unstable build");
					listener.getLogger().println("\n");
					run.setResult(hudson.model.Result.UNSTABLE);
					
				}
				
				for (int i = 0; i < NewEventList.size(); i++) {
					listener.getLogger().println(NewEventList.get(i).geteventSummary() + " Intooduced by:  " + NewEventList.get(i).getintroducedBy() + "   " + NewEventList.get(i).getARCLink());
				}
				x = RetryCount;
			}
			listener.getLogger().println("New Events Introduced by " + deployNameEnv + ": " + NewEventList.size());
			x++;

		}
		
		
		OverOpsBuildAction buildAction = new OverOpsBuildAction(EventList, NewEventList, run );
        run.addAction(buildAction);
        
		listener.getLogger().println();
		listener.getLogger().println("Total Events found in OverOps for build " + deployNameEnv + ": " + EventList.size());
		listener.getLogger().println("New Events Introduced by " + deployNameEnv + ": " + NewEventList.size());
	}

}
