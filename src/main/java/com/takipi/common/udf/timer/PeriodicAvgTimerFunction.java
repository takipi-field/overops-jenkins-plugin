package com.takipi.common.udf.timer;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.timer.Timer;
import com.takipi.common.api.data.volume.Transaction;
import com.takipi.common.api.request.event.EventsRequest;
import com.takipi.common.api.request.timer.CreateTimerRequest;
import com.takipi.common.api.request.timer.EditTimerRequest;
import com.takipi.common.api.request.timer.TimersRequest;
import com.takipi.common.api.request.volume.TransactionsVolumeRequest;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.event.EventsResult;
import com.takipi.common.api.result.timer.TimersResult;
import com.takipi.common.api.result.volume.TransactionsVolumeResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.CollectionUtil;
import com.takipi.common.api.util.Pair;
import com.takipi.common.udf.ContextArgs;
import com.takipi.common.udf.input.Input;
import com.takipi.common.udf.util.JavaUtil;

import gnu.trove.iterator.TIntLongIterator;
import gnu.trove.iterator.TObjectLongIterator;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;

public class PeriodicAvgTimerFunction {
	private static final int MISSING_TIMER_ID = -1;

	public static String validateInput(String rawInput) {
		return getPeriodicAvgTimerInput(rawInput).toString();
	}

	static PeriodicAvgTimerInput getPeriodicAvgTimerInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		PeriodicAvgTimerInput input;

		try {
			input = PeriodicAvgTimerInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (input.timespan <= 0) {
			throw new IllegalArgumentException("'timespan' must be positive");
		}

		return input;
	}

	public static void execute(String rawContextArgs, String rawInput) {
		PeriodicAvgTimerInput input = getPeriodicAvgTimerInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		System.out.println("execute context: " + rawContextArgs);

		if (!args.validate()) {
			throw new IllegalArgumentException("Bad context args - " + rawContextArgs);
		}

		if (!args.viewValidate()) {
			return;
		}

		ApiClient apiClient = args.apiClient();

		DateTime to = DateTime.now();
		DateTime from = to.minusHours(input.timespan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		TransactionsVolumeRequest transactionsRequest = TransactionsVolumeRequest.newBuilder()
				.setServiceId(args.serviceId).setViewId(args.viewId).setFrom(from.toString(fmt)).setTo(to.toString(fmt))
				.build();

		Response<TransactionsVolumeResult> transactionsResponse = apiClient.get(transactionsRequest);

		if (transactionsResponse.isBadResponse()) {
			throw new IllegalStateException("Failed getting view transactions.");
		}

		TransactionsVolumeResult transactionsResult = transactionsResponse.data;

		if (CollectionUtil.safeIsEmpty(transactionsResult.transactions)) {
			return;
		}

		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
				.setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		if (eventsResponse.isBadResponse()) {
			throw new IllegalStateException("Failed getting view events.");
		}

		EventsResult eventsResult = eventsResponse.data;

		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			throw new IllegalStateException("Missing events");
		}

		TimersRequest timersRequest = TimersRequest.newBuilder().setServiceId(args.serviceId).build();

		Response<TimersResult> timersResponse = apiClient.get(timersRequest);

		if (timersResponse.isBadResponse()) {
			throw new IllegalStateException("Failed getting timers.");
		}

		TObjectLongMap<Pair<String, String>> newTimers = new TObjectLongHashMap<Pair<String, String>>();
		TIntLongMap updatedTimers = new TIntLongHashMap();

		for (Transaction transaction : transactionsResult.transactions) {
			if ((Strings.isNullOrEmpty(transaction.name)) || (transaction.stats == null)) {
				continue;
			}

			long timerThreshold = (long) Math.floor(transaction.stats.avg_time);

			if (timerThreshold < 1L) {
				continue;
			}

			Pair<String, String> fullName = getFullTransactionName(transaction.name, eventsResult.events);

			if (fullName == null) {
				continue;
			}

			int timerId = getExistingTimerId(fullName, timersResponse.data.timers);

			if (timerId <= 0) {
				newTimers.put(fullName, timerThreshold);
			} else {
				updatedTimers.put(timerId, timerThreshold);
			}
		}

		for (TObjectLongIterator<Pair<String, String>> iter = newTimers.iterator(); iter.hasNext();) {
			iter.advance();

			CreateTimerRequest createTimerRequest = CreateTimerRequest.newBuilder().setServiceId(args.serviceId)
					.setClassName(iter.key().getFirst()).setMethodName(iter.key().getSecond())
					.setThreshold(iter.value()).build();

			apiClient.post(createTimerRequest);
		}

		for (TIntLongIterator iter = updatedTimers.iterator(); iter.hasNext();) {
			iter.advance();

			EditTimerRequest editTimerRequest = EditTimerRequest.newBuilder().setServiceId(args.serviceId)
					.setTimerId(iter.key()).setThreshold(iter.value()).build();

			apiClient.post(editTimerRequest);
		}
	}

	private static Pair<String, String> getFullTransactionName(String name, List<EventResult> events) {
		String internalName = JavaUtil.toInternalName(name);

		for (EventResult event : events) {
			if ((event.entry_point == null) || (Strings.isNullOrEmpty(event.entry_point.class_name))
					|| (Strings.isNullOrEmpty(event.entry_point.method_name))) {
				continue;
			}

			if (internalName.equals(JavaUtil.toInternalName(event.entry_point.class_name))) {
				return Pair.of(JavaUtil.toInternalName(event.entry_point.class_name), event.entry_point.method_name);
			}
		}

		return null;
	}

	private static int getExistingTimerId(Pair<String, String> fullName, List<Timer> timers) {
		if (CollectionUtil.safeIsEmpty(timers)) {
			return MISSING_TIMER_ID;
		}

		for (Timer timer : timers) {
			if ((fullName.getFirst().equals(JavaUtil.toInternalName(timer.class_name)))
					&& (fullName.getSecond().equals(timer.method_name))) {
				return Integer.parseInt(timer.id);
			}
		}

		return MISSING_TIMER_ID;
	}

	static class PeriodicAvgTimerInput extends Input {
		public int timespan; // hours

		private PeriodicAvgTimerInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("PeriodicAvgTimer(");
			builder.append(timespan);
			builder.append(")");

			return builder.toString();
		}

		static PeriodicAvgTimerInput of(String raw) {
			return new PeriodicAvgTimerInput(raw);
		}
	}
}