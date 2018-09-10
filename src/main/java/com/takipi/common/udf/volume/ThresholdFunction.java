package com.takipi.common.udf.volume;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Comparator;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.volume.Transaction;
import com.takipi.common.api.request.alert.Anomaly;
import com.takipi.common.api.request.alert.Anomaly.AnomalyContributor;
import com.takipi.common.api.request.alert.AnomalyAlertRequest;
import com.takipi.common.api.request.event.EventModifyLabelsRequest;
import com.takipi.common.api.request.label.CreateLabelRequest;
import com.takipi.common.api.request.volume.EventsVolumeRequest;
import com.takipi.common.api.request.volume.TransactionsVolumeRequest;
import com.takipi.common.api.result.EmptyResult;
import com.takipi.common.api.result.GenericResult;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.volume.EventsVolumeResult;
import com.takipi.common.api.result.volume.TransactionsVolumeResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.ValidationUtil.VolumeType;
import com.takipi.common.udf.ContextArgs;
import com.takipi.common.udf.input.Input;

public class ThresholdFunction {
	static ThresholdInput getThresholdInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		ThresholdInput input;

		try {
			input = ThresholdInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (input.relative_to == null) {
			throw new IllegalArgumentException("Missing 'relative_to'");
		}

		if (input.timespan <= 0) {
			throw new IllegalArgumentException("'timespan' must be positive");
		}

		if (input.threshold <= 0l) {
			throw new IllegalArgumentException("'threshold' must be positive");
		}

		if ((input.relative_to == Mode.Method_Calls) && (input.rate <= 0.0)) {
			throw new IllegalArgumentException("'rate' must be positive");
		}

		if ((input.relative_to == Mode.Method_Calls) && (input.rate > 100.0)) {
			throw new IllegalArgumentException("'rate' can't be more then 100 for method calls");
		}

		if ((input.relative_to == Mode.Thread_Calls) && (input.rate <= 0.0)) {
			throw new IllegalArgumentException("'rate' must be positive");
		}

		return input;
	}

	static void execute(String rawContextArgs, ThresholdInput input) {
		System.out.println("execute:" + rawContextArgs);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		if (!args.viewValidate()) {
			throw new IllegalArgumentException("Bad context args - " + rawContextArgs);
		}

		ApiClient apiClient = args.apiClient();

		VolumeType volumeType = ((input.relative_to == Mode.Method_Calls) ? VolumeType.all : VolumeType.hits);

		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(input.timespan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		EventsVolumeRequest eventsVolumeRequest = EventsVolumeRequest.newBuilder().setServiceId(args.serviceId)
				.setViewId(args.viewId).setFrom(from.toString(fmt)).setTo(to.toString(fmt)).setVolumeType(volumeType)
				.build();

		Response<EventsVolumeResult> eventsVolumeResponse = apiClient.get(eventsVolumeRequest);

		if (eventsVolumeResponse.isBadResponse()) {
			throw new IllegalStateException("Can't create events volume.");
		}

		EventsVolumeResult eventsVolumeResult = eventsVolumeResponse.data;

		if (eventsVolumeResult == null) {
			throw new IllegalStateException("Missing events volume result.");
		}

		if (eventsVolumeResult.events == null) {
			return;
		}

		long hitCount = 0l;

		for (EventResult event : eventsVolumeResult.events) {
			if (event.stats != null) {
				hitCount += event.stats.hits;
			}
		}

		if (hitCount <= input.threshold) {
			return;
		}

		boolean thresholdExceeded = false;

		switch (input.relative_to) {
		case Absolute: {
			thresholdExceeded = true;
		}
			break;

		case Method_Calls: {
			long invocationsCount = 0l;

			Collections.sort(eventsVolumeResult.events, new Comparator<EventResult>() {
				@Override
				public int compare(EventResult o1, EventResult o2) {
					int i1 = Integer.parseInt(o1.id);
					int i2 = Integer.parseInt(o2.id);
					return i1 - i2;
				};
			});

			for (EventResult event : eventsVolumeResult.events) {
				if (event.stats != null) {
					System.out.println(event.id + ": " + event.summary + " - hits: " + event.stats.hits + " - inv: "
							+ event.stats.invocations);

					invocationsCount += Math.max(event.stats.invocations, event.stats.hits);
				}
			}

			invocationsCount = Math.max(invocationsCount, hitCount);

			double failRate = (hitCount / (double) invocationsCount) * 100.0;

			thresholdExceeded = (failRate >= input.rate);
		}
			break;

		case Thread_Calls: {
			TransactionsVolumeRequest transactionsVolumeRequest = TransactionsVolumeRequest.newBuilder()
					.setServiceId(args.serviceId).setViewId(args.viewId)
					.setFrom(DateTime.now().minusMinutes(input.timespan).toString()).setTo(DateTime.now().toString())
					.build();

			Response<TransactionsVolumeResult> transactionsVolumeResponse = apiClient.get(transactionsVolumeRequest);

			if (transactionsVolumeResponse.isBadResponse()) {
				throw new IllegalStateException("Can't create transactions volume.");
			}

			TransactionsVolumeResult transactionsVolumeResult = transactionsVolumeResponse.data;

			if (transactionsVolumeResult == null) {
				throw new IllegalStateException("Missing events volume result.");
			}

			long transactionInvocationsCount = 0l;

			for (Transaction transaction : transactionsVolumeResult.transactions) {
				if (transaction.stats != null) {
					transactionInvocationsCount += transaction.stats.invocations;
				}
			}

			if (transactionInvocationsCount > 0l) {
				double failRate = (hitCount / (double) transactionInvocationsCount) * 100.0;

				thresholdExceeded = (failRate >= input.rate);
			}
		}
			break;
		}

		System.out.println("Threshold response: " + thresholdExceeded);

		if (!thresholdExceeded) {
			return;
		}

		// Send anomaly message to integrations

		Anomaly anomaly = Anomaly.create();

		anomaly.addAnomalyPeriod(args.viewId, from.getMillis(), to.getMillis());

		for (EventResult event : eventsVolumeResult.events) {
			if ((event.stats != null) && (event.stats.hits > 0)) {
				anomaly.addContributor(Integer.parseInt(event.id), event.stats.hits);
			}
		}

		AnomalyAlertRequest anomalyAlertRequest = AnomalyAlertRequest.newBuilder().setServiceId(args.serviceId)
				.setViewId(args.viewId).setFrom(from.toString()).setTo(to.toString()).setDesc(input.toString())
				.setAnomaly(anomaly).build();

		Response<GenericResult> anomalyAlertResponse = apiClient.post(anomalyAlertRequest);

		if (anomalyAlertResponse.isBadResponse()) {
			throw new IllegalStateException("Failed alerting on anomaly for view - " + args.viewId);
		}

		GenericResult alertResult = anomalyAlertResponse.data;

		if (alertResult == null) {
			throw new IllegalStateException("Failed getting anomaly alert result on view - " + args.viewId);
		}

		if (!alertResult.result) {
			throw new IllegalStateException(
					"Anomaly alert on view - " + args.viewId + " failed with - " + alertResult.message);
		}

		// Mark all contributors as Alert label
		if (!StringUtils.isEmpty(input.label)) {
			CreateLabelRequest createLabel = CreateLabelRequest.newBuilder().setServiceId(args.serviceId)
					.setName(input.label).build();

			Response<EmptyResult> createResult = apiClient.post(createLabel);

			if ((createResult.isBadResponse()) && (createResult.responseCode != HttpURLConnection.HTTP_CONFLICT)) {
				throw new IllegalStateException("Can't create label " + input);
			}

			for (AnomalyContributor contributor : anomaly.getAnomalyContributors()) {
				EventModifyLabelsRequest addLabel = EventModifyLabelsRequest.newBuilder().setServiceId(args.serviceId)
						.setEventId(String.valueOf(contributor.id)).addLabel(input.label).build();

				Response<EmptyResult> addResult = apiClient.post(addLabel);

				if (addResult.isBadResponse()) {
					throw new IllegalStateException("Can't apply label " + input.label + " to event " + args.eventId);
				}
			}
		}
	}

	static class ThresholdInput extends Input {
		public Mode relative_to;
		public long threshold;
		public double rate;
		public int timespan; // minutes
		public String label;

		private ThresholdInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Threshold(");

			switch (relative_to) {
			case Absolute:
				builder.append(threshold);
				break;

			case Method_Calls:
			case Thread_Calls: {
				builder.append(threshold);
				builder.append(", ");

				builder.append(String.format("%.2f", rate));
				builder.append('%');

				builder.append(" of ");
				builder.append(relative_to);
			}
				break;
			}

			builder.append(")");

			return builder.toString();
		}

		static ThresholdInput of(String raw) {
			return new ThresholdInput(raw);
		}
	}

	public enum Mode {
		Absolute, Method_Calls, Thread_Calls
	}
}
