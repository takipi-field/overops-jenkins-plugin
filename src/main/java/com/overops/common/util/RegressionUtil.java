package com.overops.common.util;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import com.overops.common.api.util.ApiViewUtil;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.volume.EventsVolumeResult;

public class RegressionUtil {
	public static class RegressionPair {
		private final EventResult baseEvent;
		private final EventResult activeEvent;

		RegressionPair(EventResult baseEvent, EventResult activeEvent) {
			this.baseEvent = baseEvent;
			this.activeEvent = activeEvent;
		}

		public EventResult getBaseEvent() {
			return baseEvent;
		}

		public EventResult getActiveEvent() {
			return activeEvent;
		}
	}

	public static class RateRegression {
		private final Map<String, EventResult> allNewEvents;

		private final Map<String, RegressionPair> allRegressions;
		private final Map<String, RegressionPair> criticalRegressions;

		private final Map<String, EventResult> exceededNewEvents;
		private final Map<String, EventResult> criticalNewEvents;

		private final Map<String, EventResult> baselineEvents;

		RateRegression() {
			allRegressions = new HashMap<String, RegressionPair>();
			criticalNewEvents = new HashMap<String, EventResult>();
			exceededNewEvents = new HashMap<String, EventResult>();
			allNewEvents = new HashMap<String, EventResult>();
			criticalRegressions = new HashMap<String, RegressionPair>();
			baselineEvents = new HashMap<String, EventResult>();
		}

		public Map<String, EventResult> getAllNewEvents() {
			return allNewEvents;
		}

		public Map<String, RegressionPair> getAllRegressions() {
			return allRegressions;
		}

		public Map<String, RegressionPair> getCriticalRegressions() {
			return criticalRegressions;
		}

		public Map<String, EventResult> getExceededNewEvents() {
			return exceededNewEvents;
		}

		public Map<String, EventResult> getCriticalNewEvents() {
			return criticalNewEvents;
		}

		public Map<String, EventResult> getBaselineEvents() {
			return baselineEvents;
		}
	}

	public static RateRegression calculateRateRegressions(ApiClient apiClient, String serviceId, String viewId,
			int activeTimespan, int baselineTimespan, int minVolumeThreshold, double minErrorRateThreshold,
			double reggressionDelta, double criticalRegressionDelta, Collection<String> criticalExceptionTypes,
			PrintStream printStream) {

		RateRegression result = new RateRegression();

		DateTime now = DateTime.now();
		DateTime baselineFrom = now.minusMinutes(baselineTimespan);

		EventsVolumeResult baselineEventVolume = ApiViewUtil.getEventsVolume(apiClient, serviceId, viewId, baselineFrom,
				now);

		if (baselineEventVolume.events != null) {
			for (EventResult eventResult : baselineEventVolume.events) {
				if (eventResult.stats == null) {
					continue;
				}
	
				result.getBaselineEvents().put(eventResult.id, eventResult);
			}
		}
		
		DateTime activeFrom = now.minusMinutes(activeTimespan);
		EventsVolumeResult activeEventVolume = ApiViewUtil.getEventsVolume(apiClient, serviceId, viewId, activeFrom,
				now);

		if (activeEventVolume.events != null) {
			for (EventResult activeEvent : activeEventVolume.events) {
				DateTime firstSeen = ISODateTimeFormat.dateTimeParser().parseDateTime(activeEvent.first_seen);
	
				boolean isNew = firstSeen.isAfter(activeFrom);
	
				if (isNew) {
					result.getAllNewEvents().put(activeEvent.id, activeEvent);
	
					// events types in the critical list are considered as new regardless of
					// threshold
	
					boolean isUncaught = activeEvent.type.equals("Uncaught Exception");
					boolean isCriticalEventType = criticalExceptionTypes.contains(activeEvent.name);
	
					if ((isUncaught) || (isCriticalEventType)) {
						result.getCriticalNewEvents().put(activeEvent.id, activeEvent);
	
						if (printStream != null) {
							printStream.println("Event " + activeEvent.id + " " + activeEvent.type + " - "
									+ activeEvent.name + " is critical with " + activeEvent.stats.hits);
						}
	
						continue;
					}
				}
	
				if ((activeEvent.stats == null) || (activeEvent.stats.invocations == 0) || (activeEvent.stats.hits == 0)) {
					continue;
				}
	
				double activeEventRatio = ((double) activeEvent.stats.hits / (double) activeEvent.stats.invocations);
	
				if ((activeEventRatio < minErrorRateThreshold) || (activeEvent.stats.hits < minVolumeThreshold)) {
					continue;
				}
	
				if (isNew) {
	
					result.getExceededNewEvents().put(activeEvent.id, activeEvent);
	
					if (printStream != null) {
						printStream.println("Event " + activeEvent.id + " " + activeEvent.type + " is new with ER: "
								+ activeEventRatio + " hits: " + activeEvent.stats.hits);
					}
	
					continue;
				}
	
				if (reggressionDelta == 0) {
					continue;
				}
	
				EventResult baseLineEvent = result.getBaselineEvents().get(activeEvent.id);
	
				if (baseLineEvent == null) {
					continue;
				}
	
				// see what the error rate is for the event
				double baselineEventRatio;
	
				if (baseLineEvent.stats.invocations > 0) {
					baselineEventRatio = (double) baseLineEvent.stats.hits / (double) baseLineEvent.stats.invocations;
				} else {
					baselineEventRatio = 0;
				}
	
				boolean regression;
	
				if (baselineEventRatio == 0) {
					regression = true;
				} else {
					// see if the error rate has increased by more than X%, if so an above min
					// volume, mark as regression
					regression = activeEventRatio - baselineEventRatio >= reggressionDelta;
				}
	
				// check if this event can be considered a rate regression
				if (!regression) {
					continue;
				}
				result.getAllRegressions().put(activeEvent.id, new RegressionPair(baseLineEvent, activeEvent));
	
				if (printStream != null) {
					printStream.println("Event " + activeEvent.id + " " + activeEvent.type + " regressed from ER: "
							+ baselineEventRatio + " to: " + activeEventRatio + " hits: " + activeEvent.stats.hits);
				}
	
				// check if this event can be considered a critical rate regression
				if (criticalRegressionDelta == 0) {
					continue;
				}
	
				boolean criticalRegression = activeEventRatio - baselineEventRatio >= criticalRegressionDelta;
	
				if (!criticalRegression) {
					continue;
				}
	
				result.getCriticalRegressions().put(activeEvent.id, new RegressionPair(baseLineEvent, activeEvent));
	
				if (printStream != null) {
					printStream.println("Event " + activeEvent.id + " " + activeEvent.type +
							" critically regressed from ER: " + baselineEventRatio + " to: " + activeEventRatio + 
							", hits: " + activeEvent.stats.hits);
				}
			}
		}

		return result;
	}
}
