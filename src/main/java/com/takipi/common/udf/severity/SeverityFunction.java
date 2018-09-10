package com.takipi.common.udf.severity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.request.event.EventModifyLabelsRequest;
import com.takipi.common.api.request.event.actions.EventActionsRequest;
import com.takipi.common.api.result.EmptyResult;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.event.actions.EventActionsResult;
import com.takipi.common.api.result.event.actions.EventsActionsResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.ApiClientUtils;
import com.takipi.common.api.util.Pair;
import com.takipi.common.udf.ContextArgs;
import com.takipi.common.udf.input.Input;
import com.takipi.common.udf.label.ApplyLabelFunction;
import com.takipi.common.udf.regression.RegressionUtils;
import com.takipi.common.udf.regression.RegressionUtils.RateRegression;

public class SeverityFunction {
	public static String validateInput(String rawInput) {
		return parseSeverityInput(rawInput).toString();
	}

	private static void setupSeverityViews(ContextArgs args, SeverityInput input) {
		ApiClientUtils.createLabelsIfNotExists(args.apiClient(), args.serviceId,
				new String[] { input.newEventslabel, input.regressedEventsLabel });

		Collection<Pair<String, String>> views = new ArrayList<Pair<String, String>>();

		views.add(Pair.of(input.newEventsView, input.newEventslabel));
		views.add(Pair.of(input.regressedEventsView, input.regressedEventsLabel));

		ApiClientUtils.createLabelViewsIfNotExists(args.apiClient(), args.serviceId, views);
	}

	public static void execute(String rawContextArgs, String rawInput) {
		System.out.println("execute:" + rawContextArgs);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		if (!args.viewValidate()) {
			throw new IllegalArgumentException("Invalid context args - " + rawContextArgs);
		}

		SeverityInput input = parseSeverityInput(rawInput);

		setupSeverityViews(args, input);

		RateRegression rateRegression = RegressionUtils.calculateRateRegressions(args.apiClient(), args.serviceId,
				args.viewId, input.activeTimespan, input.baseTimespan, input.minVolumeThreshold,
				input.minErrorRateThreshold, input.reggressionDelta, 0, input.criticalExceptionTypes, System.out);

		Map<String, EventResult> allNewAndCritical = new HashMap<String, EventResult>();

		allNewAndCritical.putAll(rateRegression.getExceededNewEvents());
		allNewAndCritical.putAll(rateRegression.getCriticalNewEvents());

		// apply the "New Issue" and "Regression" labels to each of the lists
		applySeverityLabels(args, input.newEventslabel, input.labelRetention, allNewAndCritical.values(),
				rateRegression.getBaselineEvents());

		applySeverityLabels(args, input.regressedEventsLabel, input.labelRetention,
				getActiveRegressionEvents(rateRegression.getAllRegressions()), rateRegression.getBaselineEvents());
	}

	private static Collection<EventResult> getActiveRegressionEvents(
			Map<String, RegressionUtils.RegressionPair> regressions) {
		List<EventResult> result = new ArrayList<EventResult>();

		for (RegressionUtils.RegressionPair pair : regressions.values()) {
			result.add(pair.getActiveEvent());
		}

		return result;
	}

	private static void applySeverityLabels(ContextArgs args, String label, int labelRetention,
			Collection<EventResult> targetEvents, Map<String, EventResult> allEvents) {

		ApiClient apiClient = args.apiClient();

		ContextArgs eventArgs = new ContextArgs();
		eventArgs.apiHost = args.apiHost;
		eventArgs.apiKey = args.apiKey;
		eventArgs.serviceId = args.serviceId;

		String applyLabelParams = "label=" + label;

		Map<String, EventResult> newlyLabeledEvents = new HashMap<String, EventResult>();

		for (EventResult event : targetEvents) {
			boolean hasLabel = (event.labels != null) && (event.labels.contains(label));

			if (!hasLabel) {
				// let's leverage the existing Apply Label UDF
				eventArgs.eventId = event.id;
				newlyLabeledEvents.put(event.id, event);

				ApplyLabelFunction.execute(new Gson().toJson(eventArgs), applyLabelParams);

				System.out.println("Applying label " + label + " to " + event.id);
			}
		}

		for (EventResult event : allEvents.values()) {

			// if this is a new severe issue, no need to cleanup its label
			if (newlyLabeledEvents.containsKey(event.id)) {
				continue;
			}

			// if this event wasn't prev marked as severe - skip
			if ((event.labels == null) && (!event.labels.contains(label))) {
				continue;
			}

			// get the actions for this event. Let's see when was that event marked severe
			EventActionsRequest eventActionsRequest = EventActionsRequest.newBuilder().setServiceId(args.serviceId)
					.setEventId(event.id).build();

			Response<EventsActionsResult> eventsActionsResponse = apiClient.get(eventActionsRequest);

			if (eventsActionsResponse.isBadResponse()) {
				System.err.println("Can't create events actions for event " + event.id);
			}

			if (eventsActionsResponse.data.events == null) {
				continue;
			}

			for (EventActionsResult eventAction : eventsActionsResponse.data.events) {

				// we should add a constant for this in the Java API wrapper
				if (!"Add Label".equals(eventAction.action)) {
					continue;
				}

				if (!label.equals(eventAction.data)) {
					continue;
				}

				DateTime labelAddTime = ISODateTimeFormat.dateTimeParser().parseDateTime(eventAction.timestamp);
				DateTime retentionWindow = DateTime.now().minusMinutes(labelRetention);

				// lets see if the label was added before the retention window, is so - remove
				// it
				if (labelAddTime.isBefore(retentionWindow)) {
					EventModifyLabelsRequest eventModifyLabelsRequest = EventModifyLabelsRequest.newBuilder()
							.setServiceId(args.serviceId).setEventId(event.id).removeLabel(label).build();

					Response<EmptyResult> eventModifyLabelsResponse = apiClient.post(eventModifyLabelsRequest);

					if (eventModifyLabelsResponse.isBadResponse()) {
						System.err.println("Can't remove label " + label + " for event " + event.id);
					}

					System.out.println("Removing label " + label + " from " + event.id);
				}
			}
		}
	}

	static SeverityInput parseSeverityInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		SeverityInput input;

		try {
			input = SeverityInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (input.activeTimespan <= 0) {
			throw new IllegalArgumentException("'activeTimespan' must be positive'");
		}

		if (input.baseTimespan <= 0) {
			throw new IllegalArgumentException("'timespan' must be positive");
		}

		if (input.reggressionDelta <= 0) {
			throw new IllegalArgumentException("'reggressionDelta' must be positive");
		}

		if (input.newEventslabel == null) {
			throw new IllegalArgumentException("'newEventslabel' must exist");
		}

		if (input.regressedEventsLabel == null) {
			throw new IllegalArgumentException("'regressedEventsLabel' must exist");
		}

		return input;
	}

	static class SeverityInput extends Input {
		public int activeTimespan; // the time window (min) that we compare the baseline to
		public int baseTimespan; // the time window (min) to compare the last <activeTimespan> against
		public double reggressionDelta; // a change in % that would be considered a regression
		public List<String> criticalExceptionTypes; // comma delimited list of exception types that are severe by def
		public double minErrorRateThreshold; // min ER that a regression, new + non-critical event must exceed
		public int minVolumeThreshold; // min volume that a regression, new + non-critical event must exceed
		public String newEventslabel; // how to label new issues
		public String regressedEventsLabel; // how to label regressions
		public String newEventsView; // view containing new issues
		public String regressedEventsView; // view containing regressions
		public int labelRetention; // how long (min) should thse labels "stick" to an event

		private SeverityInput(String raw) {
			super(raw);
		}

		static SeverityInput of(String raw) {
			return new SeverityInput(raw);
		}

		@Override
		public String toString() {
			return String.format("Severe(Window = %d, Baseline = %d, Thresh = %d, Rate = %d)", activeTimespan,
					baseTimespan, minVolumeThreshold, minErrorRateThreshold);
		}
	}
}
