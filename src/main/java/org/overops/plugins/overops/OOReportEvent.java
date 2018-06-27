package org.overops.plugins.overops;

public class OOReportEvent {
	
	private String eventSummary;
	private String introducedBy;
	private String ARCLink;
	
	public OOReportEvent(String eventSummary, String introducedBy, String ARCLink) {
	
		{
			this.eventSummary = eventSummary;
			this.introducedBy = introducedBy;
			this.ARCLink = ARCLink;
		
		}
		
	}

	
	 public String geteventSummary() {
	        return this.eventSummary;
	        }
	 public String getintroducedBy() {
	        return this.introducedBy;
	        }
	 public String getARCLink() {
	        return this.ARCLink;
	        }
}
