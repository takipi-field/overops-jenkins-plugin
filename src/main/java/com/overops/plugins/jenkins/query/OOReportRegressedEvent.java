package com.overops.plugins.jenkins.query;

import com.takipi.common.api.result.event.EventResult;

public class OOReportRegressedEvent extends OOReportEvent{

    private final EventResult baseLineEvent;

    public OOReportRegressedEvent(EventResult activeEvent, EventResult baseLineEvent, String type, String arcLink) {
    		
    		super(activeEvent, type, arcLink);
    		
    		this.baseLineEvent = baseLineEvent;
    }
    
    @Override
    public String getEventRate() {
    	
    		double rate = (double)baseLineEvent.stats.hits / (double)baseLineEvent.stats.invocations * 100; 	
    		
    		StringBuilder result = new StringBuilder();
    		result.append(super.getEventRate());
    		result.append(" from ");
    		result.append(decimalFormat.format(rate));
    		result.append("%");
    		
    		return result.toString();
	}
    
    public long getBaselineHits() {
        return baseLineEvent.stats.hits;
    }
    
    public long getBaselineCalls() {
        return  baseLineEvent.stats.invocations;
    }
}
