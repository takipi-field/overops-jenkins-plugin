package com.takipi.common.udf.infra;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.request.event.EventsRequest;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.event.EventsResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.CollectionUtil;
import com.takipi.common.udf.ContextArgs;
import com.takipi.common.udf.infra.InfrastructureRoutingFunction.InfrastructureInput;

public class PeriodicInfrastructureRoutingFunction {
	public static void main(String[] args) {

	}

	public static String validateInput(String rawInput) {
		getInfrastructureInput(rawInput);

		return "Infrastructure";
	}

	private static PeriodicInfrastructureInput getInfrastructureInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		PeriodicInfrastructureInput input;

		try {
			input = PeriodicInfrastructureInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (StringUtils.isEmpty(input.category_name)) {
			throw new IllegalArgumentException("'category_name' can't be empty");
		}

		if (input.timespan <= 0) {
			throw new IllegalArgumentException("'timespan' must be positive");
		}

		return input;
	}

	public static void execute(String rawContextArgs, String rawInput) {
		PeriodicInfrastructureInput input = getInfrastructureInput(rawInput);

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

		EventsRequest eventsRequest = EventsRequest.newBuilder().setServiceId(args.serviceId).setViewId(args.viewId)
				.setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		Response<EventsResult> eventsResponse = apiClient.get(eventsRequest);

		if (eventsResponse.isBadResponse()) {
			throw new IllegalStateException("Failed getting view events.");
		}

		EventsResult eventsResult = eventsResponse.data;

		if (CollectionUtil.safeIsEmpty(eventsResult.events)) {
			return;
		}

		String categoryId = InfraUtil.createCategory(input.category_name, args.serviceId, apiClient);
		Categories categories = input.getCategories();
		Set<String> createdLabels = Sets.newHashSet();

		for (EventResult event : eventsResult.events) {
			InfraUtil.categorizeEvent(event, args.serviceId, categoryId, categories, createdLabels, apiClient);
		}
	}

	static class PeriodicInfrastructureInput extends InfrastructureInput {
		public int timespan;

		private PeriodicInfrastructureInput(String raw) {
			super(raw);
		}

		static PeriodicInfrastructureInput of(String raw) {
			return new PeriodicInfrastructureInput(raw);
		}
	}
}
