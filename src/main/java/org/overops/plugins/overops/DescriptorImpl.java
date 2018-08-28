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

import javax.servlet.ServletException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

//DescriptorImpl governs the global config settings

@Extension
@Symbol("OverOpsQuery")
public final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    private String OverOpsURL;
    private String OverOpsUser;
    private Secret OverOpsPWD;
    private String OverOpsSID;
    private Secret OOAPIKey;

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
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        formData = formData.getJSONObject("QueryOverOps");
        OverOpsURL = formData.getString("OverOpsURL");
        OverOpsUser = formData.getString("OverOpsUser");
        OverOpsPWD = Secret.fromString(formData.getString("OverOpsPWD"));
        OverOpsSID = formData.getString("OverOpsSID");
        OOAPIKey = Secret.fromString(formData.getString("OOAPIKey"));
        save();
        return false;
    }

    public String getOverOpsURL() {
        return OverOpsURL;
    }

    public String getOverOpsUser() {
        return OverOpsUser;
    }

    public Secret getOverOpsPWD() {
        return OverOpsPWD;
    }

    public String getOverOpsSID() {
        return OverOpsSID;
    }

    public Secret getOOAPIKey() {
        return OOAPIKey;
    }

    public void setOverOpsURL(String OverOpsURL) {
        this.OverOpsURL = OverOpsURL;
    }

    public void setOverOpsUser(String OverOpsUser) {
        this.OverOpsUser = OverOpsUser;
    }

    public void setOverOpsPWD(Secret OverOpsPWD) {
        this.OverOpsPWD = OverOpsPWD;
    }

    public void setOverOpsSID(String OverOpsSID) {
        this.OverOpsSID = OverOpsSID;
    }

    public void setOOAPIKey(Secret OOAPIKey) {
        this.OOAPIKey = OOAPIKey;
    }

    @POST
    public FormValidation doTestConnection(@QueryParameter("OverOpsURL") final String OverOpsURL,
                                           @QueryParameter("OverOpsUser") final String OverOpsUser,
                                           @QueryParameter("OverOpsPWD") final Secret OverOpsPWD,
                                           @QueryParameter("OOAPIKey") final Secret OOAPIKey)
            throws ServletException, IOException, InterruptedException {
        //Admin permission check
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        try {
            Client OverOpsApiClient = ClientBuilder.newClient();

            String usernameAndPassword = OverOpsUser + ":" + Secret.toString(OverOpsPWD);

            String authorizationHeaderValue;
            String keyName;

            if (Secret.toString(OOAPIKey) == null || Secret.toString(OOAPIKey).isEmpty()) {
                keyName = "Authorization";
                authorizationHeaderValue = "Basic "
                        + java.util.Base64.getEncoder().encodeToString(usernameAndPassword.getBytes("utf-8"));
            } else {
                keyName = "X-API-Key";
                authorizationHeaderValue = Secret.toString(OOAPIKey);

            }

            Response response = OverOpsApiClient.target(OverOpsURL).path("api/v1/services/" + OverOpsSID + "/views")
                    .request(MediaType.APPLICATION_JSON).header(keyName, authorizationHeaderValue).get();

            if (response.getStatus() == 200) {
                JSONObject result = response.readEntity(JSONObject.class);
                JSONArray Views = result.getJSONArray("views");
                System.out.println("Found " + Views.size() + " views in OverOps service " + OverOpsSID);
                return FormValidation.ok("Connection Successful.  \n Found " + Views.size()
                        + " views in OverOps service: " + OverOpsSID);
            } else {
                return FormValidation
                        .error("REST API error : " + response.getStatus() + " " + response.getStatusInfo());
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getMessage() != null)
                return FormValidation.error("REST API error : " + e.getMessage());

        }

        return null;
    }

}