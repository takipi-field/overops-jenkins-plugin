package org.overops.plugins.overops;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;

import static java.util.Base64.getEncoder;

//DescriptorImpl governs the global config settings

@Extension
@Symbol("OverOpsQuery")
public final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    private String overOpsURL;
    private String overOpsUser;
    private Secret overOpsPWD;
    private String overOpsSID;
    private Secret overOpsAPIKey;

    public DescriptorImpl() {
        super(QueryOverOps.class);
        load();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Query OverOps";
    }

    // Allows for persisting global config settings in JSONObject
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) {
        formData = formData.getJSONObject("QueryOverOps");
        overOpsURL = formData.getString("overOpsURL");
        overOpsUser = formData.getString("overOpsUser");
        overOpsPWD = Secret.fromString(formData.getString("overOpsPWD"));
        overOpsSID = formData.getString("overOpsSID");
        overOpsAPIKey = Secret.fromString(formData.getString("overOpsAPIKey"));
        save();
        return false;
    }

    public String getOverOpsURL() {
        return overOpsURL;
    }

    public String getOverOpsUser() {
        return overOpsUser;
    }

    public Secret getOverOpsPWD() {
        return overOpsPWD;
    }

    public String getOverOpsSID() {
        return overOpsSID;
    }

    public Secret getOverOpsAPIKey() {
        return overOpsAPIKey;
    }

    @POST
    public FormValidation doTestConnection(@QueryParameter("overOpsURL") final String overOpsURL,
                                           @QueryParameter("overOpsUser") final String overOpsUser,
                                           @QueryParameter("overOpsPWD") final Secret overOpsPWD,
                                           @QueryParameter("overOpsAPIKey") final Secret overOpsAPIKey) {

        if (overOpsURL == null || overOpsURL.isEmpty()) {
            return FormValidation.error("OverOps URL is empty");
        }

        if (overOpsUser == null || overOpsUser.isEmpty()) {
            return FormValidation.error("OverOps User is empty");
        }

        if (overOpsPWD == null || overOpsPWD.getPlainText().isEmpty()) {
            return FormValidation.error("OverOps Password is empty");
        }

        if (overOpsAPIKey == null || overOpsAPIKey.getPlainText().isEmpty()) {
            return FormValidation.error("OverOps API Key is empty");
        }


        //Admin permission check
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        try {
            Client apiClient = ClientBuilder.newClient();

            String usernameAndPassword = overOpsUser + ':' + Secret.toString(overOpsPWD);
            String authorizationHeaderValue;
            String keyName;

            if (Secret.toString(overOpsAPIKey) == null || Secret.toString(overOpsAPIKey).isEmpty()) {
                keyName = "Authorization";
                authorizationHeaderValue = "Basic " + getEncoder().encodeToString(usernameAndPassword.getBytes(StandardCharsets.UTF_8));
            } else {
                keyName = "X-API-Key";
                authorizationHeaderValue = Secret.toString(overOpsAPIKey);

            }

            Response response = apiClient
                    .target(overOpsURL)
                    .path("api/v1/services/" + overOpsSID + "/views")
                    .request(MediaType.APPLICATION_JSON)
                    .header(keyName, authorizationHeaderValue).get();

            if (response.getStatus() == 200) {
                JSONObject result = response.readEntity(JSONObject.class);
                JSONArray Views = result.getJSONArray("views");
                System.out.println("Found " + Views.size() + " views in OverOps service " + overOpsSID);

                return FormValidation.ok("Connection Successful.  \n Found " + Views.size() + " views in OverOps service: " + overOpsSID);
            } else {
                return FormValidation.error("REST API error : " + response.getStatus() + ' ' + response.getStatusInfo());
            }

        } catch (Exception e) {

            e.printStackTrace();

            return FormValidation.error(e, "REST API error : " + e.getMessage());

        }

    }

}