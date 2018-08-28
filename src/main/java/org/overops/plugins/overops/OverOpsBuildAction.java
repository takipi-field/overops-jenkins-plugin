package org.overops.plugins.overops;

import hudson.model.Action;
import hudson.model.Run;

import java.util.ArrayList;


public class OverOpsBuildAction implements Action {


    private Run<?, ?> build;
    private ArrayList<OOReportEvent> EventList;
    private ArrayList<OOReportEvent> NewEventList;

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

    public ArrayList<OOReportEvent> getEventList() {
        return EventList;
    }

    public ArrayList<OOReportEvent> getNewEventList() {
        return NewEventList;
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    OverOpsBuildAction(final ArrayList<OOReportEvent> eventList2, final ArrayList<OOReportEvent> newEventList2, final Run<?, ?> build) {
        this.EventList = eventList2;
        this.NewEventList = newEventList2;
        this.build = build;
    }
}
