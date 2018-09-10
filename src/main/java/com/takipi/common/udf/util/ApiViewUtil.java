package com.takipi.common.udf.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.view.SummarizedView;
import com.takipi.common.api.data.view.ViewFilters;
import com.takipi.common.api.data.view.ViewInfo;
import com.takipi.common.api.request.view.CreateViewRequest;
import com.takipi.common.api.request.view.ViewsRequest;
import com.takipi.common.api.request.volume.EventsVolumeRequest;
import com.takipi.common.api.result.view.CreateViewResult;
import com.takipi.common.api.result.view.ViewsResult;
import com.takipi.common.api.result.volume.EventsVolumeResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.Pair;
import com.takipi.common.api.util.ValidationUtil.VolumeType;

public class ApiViewUtil {
	public static void createLabelViewsIfNotExists(ApiClient apiClient, String serviceId,
			Collection<Pair<String, String>> viewsAndLabels) {
		Map<String, SummarizedView> views = getServiceViewsByName(apiClient, serviceId);

		for (Pair<String, String> pair : viewsAndLabels) {
			String viewName = pair.getFirst();
			String labelName = pair.getSecond();

			SummarizedView view = views.get(viewName);

			if (views.containsKey(viewName)) {
				System.out.println("views " + viewName + " found with ID " + view.id);

				continue;
			}

			ViewInfo viewInfo = new ViewInfo();

			viewInfo.name = viewName;
			viewInfo.filters = new ViewFilters();
			viewInfo.filters.labels = Collections.singletonList(labelName);

			CreateViewRequest createViewRequest = CreateViewRequest.newBuilder().setServiceId(serviceId)
					.setViewInfo(viewInfo).build();

			Response<CreateViewResult> viewResponse = apiClient.post(createViewRequest);

			if ((viewResponse.isBadResponse()) || (viewResponse.data == null)) {
				System.err.println("Cannot create view " + viewName);
			} else {
				System.out.println("Created view " + viewResponse.data.view_id + " for label " + labelName);
			}
		}
	}

	public static Map<String, SummarizedView> getServiceViewsByName(ApiClient apiClient, String serviceId) {
		ViewsRequest viewsRequest = ViewsRequest.newBuilder().setServiceId(serviceId).build();

		Response<ViewsResult> viewsResponse = apiClient.get(viewsRequest);

		if ((viewsResponse.isBadResponse()) || (viewsResponse.data == null) || (viewsResponse.data.views == null)) {
			System.err.println("Can't list views");
			return Collections.emptyMap();
		}

		Map<String, SummarizedView> result = new HashMap<String, SummarizedView>();

		for (SummarizedView view : viewsResponse.data.views) {
			result.put(view.name, view);
		}

		return result;
	}

	public static SummarizedView getServiceViewByName(ApiClient apiClient, String serviceId, String viewName) {
		ViewsRequest viewsRequest = ViewsRequest.newBuilder().setServiceId(serviceId).setViewName(viewName).build();

		Response<ViewsResult> viewsResponse = apiClient.get(viewsRequest);

		if ((viewsResponse.isBadResponse()) || (viewsResponse.data == null) || (viewsResponse.data.views == null)
				|| (viewsResponse.data.views.size() == 0)) {
			return null;
		}

		SummarizedView result = viewsResponse.data.views.get(0);

		return result;
	}

	public static EventsVolumeResult getEventsVolume(ApiClient apiClient, String serviceId, String viewId,
			DateTime from, DateTime to) {
		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		EventsVolumeRequest eventsVolumeRequest = EventsVolumeRequest.newBuilder().setServiceId(serviceId)
				.setViewId(viewId).setFrom(from.toString(fmt)).setTo(to.toString(fmt)).setVolumeType(VolumeType.all)
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
			throw new IllegalStateException("Missing events volume event data.");
		}

		return eventsVolumeResult;
	}

}
