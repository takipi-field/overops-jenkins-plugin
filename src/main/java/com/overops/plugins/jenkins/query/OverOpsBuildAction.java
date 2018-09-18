package com.overops.plugins.jenkins.query;

import java.util.List;

import com.overops.plugins.jenkins.query.RegressionReportBuilder.RegressionReport;

import hudson.model.Action;
import hudson.model.Run;

public class OverOpsBuildAction implements Action {
    private final Run<?, ?> build;
    private final RegressionReport regressionReport;
    
    OverOpsBuildAction(RegressionReport regressionReport, Run<?, ?> build) {
        this.regressionReport = regressionReport;
        this.build = build;
    }
    
    @Override
    public String getIconFileName() {
        return "/plugin/overops-query/images/OverOps.png";
    }

    @Override
    public String getDisplayName() {
        return "OverOps Reliability Report";
    }

    @Override
    public String getUrlName() {
        return "OverOpsReport";
    }

    public List<OOReportRegressedEvent> getRegressedEvents() {
        return regressionReport.getRegressions();
    }

    public List<OOReportEvent> getNewEvents() {
        return regressionReport.getNewIssues();
    }
    
    public List<OOReportEvent> getAllIssues() {
        return regressionReport.getAllIssues();
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public Run<?, ?> getBuild() {
        return build;
    }
}
