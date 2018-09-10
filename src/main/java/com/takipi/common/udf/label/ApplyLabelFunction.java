package com.takipi.common.udf.label;

import java.net.HttpURLConnection;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.request.event.EventModifyLabelsRequest;
import com.takipi.common.api.request.label.CreateLabelRequest;
import com.takipi.common.api.result.EmptyResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.udf.ContextArgs;
import com.takipi.common.udf.input.Input;

public class ApplyLabelFunction {
	public static String validateInput(String rawInput) {
		return getLabelInput(rawInput).toString();
	}

	private static LabelInput getLabelInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		LabelInput input;

		try {
			input = LabelInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (StringUtils.isEmpty(input.label)) {
			throw new IllegalArgumentException("Label name can't be empty");
		}

		return input;
	}

	public static void execute(String rawContextArgs, String rawInput) {
		LabelInput input = getLabelInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		System.out.println("execute context: " + rawContextArgs);

		if (!args.validate()) {
			throw new IllegalArgumentException("Bad context args - " + rawContextArgs);
		}

		if (!args.eventValidate()) {
			return;
		}

		ApiClient apiClient = args.apiClient();

		CreateLabelRequest createLabel = CreateLabelRequest.newBuilder().setServiceId(args.serviceId).setName(input.label)
				.build();

		Response<EmptyResult> createResult = apiClient.post(createLabel);

		if ((createResult.isBadResponse()) && (createResult.responseCode != HttpURLConnection.HTTP_CONFLICT)) {
			throw new IllegalStateException("Can't create label " + input);
		}

		EventModifyLabelsRequest addLabel = EventModifyLabelsRequest.newBuilder().setServiceId(args.serviceId)
				.setEventId(args.eventId).addLabel(input.label).build();

		Response<EmptyResult> addResult = apiClient.post(addLabel);

		if (addResult.isBadResponse()) {
			throw new IllegalStateException("Can't apply label " + input + " to event " + args.eventId);
		}
	}

	static class LabelInput extends Input {
		public String label;

		private LabelInput(String raw) {
			super(raw);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();

			builder.append("Label name - ");
			builder.append(label);

			return builder.toString();
		}

		static LabelInput of(String raw) {
			return new LabelInput(raw);
		}
	}
}
