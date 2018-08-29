package org.overops.plugins.overops;

import hudson.model.Action;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.List;


public class OverOpsBuildAction implements Action {


    private Run<?, ?> build;
    private List<OOReportEvent> events;
    private List<OOReportEvent> newEvents;

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

    public List<OOReportEvent> getEvents() {
        return events;
    }

    public List<OOReportEvent> getNewEvents() {
        return newEvents;
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    OverOpsBuildAction(final List<OOReportEvent> events, final List<OOReportEvent> newEvents, final Run<?, ?> build) {
        this.events = events;
        this.newEvents = newEvents;
        this.build = build;
    }
}
