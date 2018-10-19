package com.overops.plugins.jenkins.query;

import com.takipi.common.api.result.event.EventResult;

public class OOReportRegressedEvent extends OOReportEvent{

	private final long baselineHits;
	private final long baselineInvocations;
    
    public OOReportRegressedEvent(EventResult activeEvent, long baselineHits, long baselineInvocations, String type, String arcLink) {
    		
    		super(activeEvent, type, arcLink);
    		
    		this.baselineHits = baselineHits;
    		this.baselineInvocations = baselineInvocations;
    }
    
    @Override
    public String getEventRate() {
    	
    		double rate = (double)baselineHits / (double)baselineInvocations * 100; 	
    		
    		StringBuilder result = new StringBuilder();
    		result.append(super.getEventRate());
    		result.append(" from ");
    		result.append(decimalFormat.format(rate));
    		result.append("%");
    		
    		return result.toString();
	}
    
    public long getBaselineHits() {
        return baselineHits;
    }
    
    public long getBaselineCalls() {
        return  baselineInvocations;
    }
}
