package com.takipi.common.api.result.event.actions;

import com.takipi.common.api.result.intf.ApiResult;

public class EventActionsResult implements ApiResult {
	public String type;
	public String action;
	public String data;
	public String timestamp;
	public String initiator;
	public String initiator_type;	
}
