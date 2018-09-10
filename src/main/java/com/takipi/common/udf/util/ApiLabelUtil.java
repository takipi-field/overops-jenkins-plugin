package com.takipi.common.udf.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.label.Label;
import com.takipi.common.api.request.label.CreateLabelRequest;
import com.takipi.common.api.request.label.LabelsRequest;
import com.takipi.common.api.result.EmptyResult;
import com.takipi.common.api.result.label.LabelsResult;
import com.takipi.common.api.url.UrlClient.Response;

public class ApiLabelUtil {
	public static Map<String, Label> getServiceLabels(ApiClient apiClient, String serviceId) {
		LabelsRequest viewsRequest = LabelsRequest.newBuilder().setServiceId(serviceId).build();

		Response<LabelsResult> labelsResponse = apiClient.get(viewsRequest);

		if ((labelsResponse.isBadResponse()) || (labelsResponse.data == null) || (labelsResponse.data.labels == null)) {
			System.err.println("Can't list labels");
			return Collections.emptyMap();
		}

		Map<String, Label> result = new HashMap<String, Label>();

		for (Label label : labelsResponse.data.labels) {
			result.put(label.name, label);
		}

		return result;
	}

	public static void createLabelsIfNotExists(ApiClient apiClient, String serviceId, String[] labelNames) {
		Map<String, Label> existingLabels = getServiceLabels(apiClient, serviceId);

		for (String labelName : labelNames) {
			if (existingLabels.containsKey(labelName)) {
				System.out.println("label " + labelName + " found");

				continue;
			}

			CreateLabelRequest createLabelRequest = CreateLabelRequest.newBuilder().setServiceId(serviceId)
					.setName(labelName).build();

			Response<EmptyResult> labelResponse = apiClient.post(createLabelRequest);

			if (labelResponse.isBadResponse()) {
				System.err.println("Cannot create label " + labelName);
			}
		}
	}
}
