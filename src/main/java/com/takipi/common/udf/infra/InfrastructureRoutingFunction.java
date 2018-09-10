package com.takipi.common.udf.infra;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.util.CollectionUtil;
import com.takipi.common.udf.ContextArgs;
import com.takipi.common.udf.input.Input;

public class InfrastructureRoutingFunction {
	public static void main(String[] args) {

	}

	public static String validateInput(String rawInput) {
		getInfrastructureInput(rawInput);

		return "Infrastructure";
	}

	private static InfrastructureInput getInfrastructureInput(String rawInput) {
		System.out.println("validateInput rawInput:" + rawInput);

		if (Strings.isNullOrEmpty(rawInput)) {
			throw new IllegalArgumentException("Input is empty");
		}

		InfrastructureInput input;

		try {
			input = InfrastructureInput.of(rawInput);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}

		if (StringUtils.isEmpty(input.category_name)) {
			throw new IllegalArgumentException("'category_name' can't be empty");
		}

		return input;
	}

	public static void execute(String rawContextArgs, String rawInput) {
		InfrastructureInput input = getInfrastructureInput(rawInput);

		ContextArgs args = (new Gson()).fromJson(rawContextArgs, ContextArgs.class);

		System.out.println("execute context: " + rawContextArgs);

		if (!args.validate()) {
			throw new IllegalArgumentException("Bad context args - " + rawContextArgs);
		}

		if (!args.eventValidate()) {
			return;
		}

		ApiClient apiClient = args.apiClient();

		String categoryId = InfraUtil.createCategory(input.category_name, args.serviceId, apiClient);

		InfraUtil.categorizeEvent(args.eventId, args.serviceId, categoryId, input.getCategories(), Sets.newHashSet(),
				apiClient);
	}

	static class InfrastructureInput extends Input {
		public List<String> namespaces;
		public String template_view;
		public String category_name;

		private final Map<String, String> namespaceToLabel = Maps.newHashMap();

		InfrastructureInput(String raw) {
			super(raw);

			processNamespaces();
		}

		private void processNamespaces() {
			if (CollectionUtil.safeIsEmpty(namespaces)) {
				return;
			}

			for (String namespace : namespaces) {
				int index = namespace.indexOf('=');

				if ((index <= 0) || (index > namespace.length() - 1)) {
					throw new IllegalArgumentException("Invalid namespaces");
				}

				String key = StringUtils.trim(namespace.substring(0, index));
				String value = StringUtils.trim(namespace.substring(index + 1, namespace.length()));

				if ((key.isEmpty()) || (value.isEmpty())) {
					throw new IllegalArgumentException("Invalid namespaces");
				}

				namespaceToLabel.put(key, value);
			}
		}

		public Categories getCategories() {
			if (CollectionUtil.safeIsEmpty(namespaceToLabel)) {
				return Categories.defaultCategories();
			}

			return Categories.from(namespaceToLabel);
		}

		static InfrastructureInput of(String raw) {
			return new InfrastructureInput(raw);
		}
	}
}
