package com.takipi.common.api.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.label.Label;
import com.takipi.common.api.data.view.SummarizedView;
import com.takipi.common.api.data.view.ViewFilters;
import com.takipi.common.api.data.view.ViewInfo;
import com.takipi.common.api.request.event.EventSnapshotRequest;
import com.takipi.common.api.request.label.CreateLabelRequest;
import com.takipi.common.api.request.label.LabelsRequest;
import com.takipi.common.api.request.view.CreateViewRequest;
import com.takipi.common.api.request.view.ViewsRequest;
import com.takipi.common.api.request.volume.EventsVolumeRequest;
import com.takipi.common.api.result.EmptyResult;
import com.takipi.common.api.result.event.EventSnapshotResult;
import com.takipi.common.api.result.label.LabelsResult;
import com.takipi.common.api.result.view.CreateViewResult;
import com.takipi.common.api.result.view.ViewsResult;
import com.takipi.common.api.result.volume.EventsVolumeResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.ValidationUtil.VolumeType;

public class ApiClientUtils {

	public static void createLabelViewsIfNotExists(ApiClient apiClient, String serviceId, Collection<Pair<String, String>> viewsAndLabels) {

		Map<String, SummarizedView> views = getServiceViewsByName(apiClient, serviceId);

		for (Pair<String, String> pair : viewsAndLabels) {

			String viewName = pair.getFirst();
			String labelName = pair.getSecond();

			SummarizedView view = views.get(viewName);

			if (views.containsKey(viewName)) {
				System.out.println("views " + viewName + " found with ID " + view.id);

				return;
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

	public static void createLabelsIfNotExists(ApiClient apiClient, String serviceId, String[] labelNames) {

		Map<String, Label> existingLabels = getServiceLabels(apiClient, serviceId);

		for (String labelName : labelNames) {

			Label label = existingLabels.get(labelName);

			if (label != null) {
				System.out.println("label " + labelName + " found");

				return;
			}

			CreateLabelRequest createLabelRequest = CreateLabelRequest.newBuilder().setServiceId(serviceId)
					.setName(labelName).build();

			Response<EmptyResult> labelResponse = apiClient.post(createLabelRequest);

			if (labelResponse.isBadResponse()) {
				System.err.println("Cannot create label " + labelName);
			}
		}
	}

	public static Map<String, SummarizedView> getServiceViewsByName(ApiClient apiClient, String serviceId) {
			
		Map<String, SummarizedView> result = new HashMap<String, SummarizedView>();

		ViewsRequest viewsRequest = ViewsRequest.newBuilder().setServiceId(serviceId).build();

		Response<ViewsResult> viewsResponse = apiClient.get(viewsRequest);

		if ((viewsResponse.isBadResponse()) || (viewsResponse.data == null) || (viewsResponse.data.views == null)) {
			System.err.println("Can't list views");
		}

		for (SummarizedView view : viewsResponse.data.views) {
			result.put(view.name, view);
		}

		return result;
	}
	
	public static SummarizedView getServiceViewByName(ApiClient apiClient, String serviceId, String viewName) {
		
		ViewsRequest viewsRequest = ViewsRequest.newBuilder().setServiceId(serviceId).setViewName(viewName).build();

		Response<ViewsResult> viewsResponse = apiClient.get(viewsRequest);
		
		if ((viewsResponse.data == null) || (viewsResponse.data.views == null)
		|| (viewsResponse.data.views.size() == 0)) {
			return null;
		}
			
		SummarizedView result = viewsResponse.data.views.get(0);
		
		return result;
	}

	public static Map<String, Label> getServiceLabels(ApiClient apiClient, String serviceId) {

		Map<String, Label> result = new HashMap<String, Label>();

		LabelsRequest viewsRequest = LabelsRequest.newBuilder().setServiceId(serviceId).build();

		Response<LabelsResult> labelsResponse = apiClient.get(viewsRequest);

		if ((labelsResponse.isBadResponse()) || (labelsResponse.data == null) || (labelsResponse.data.labels == null)) {
			System.err.println("Can't list labels");
		}

		for (Label label : labelsResponse.data.labels) {
			result.put(label.name, label);
		}

		return result;
	}
	
	public static String getEventRecentLink(ApiClient apiClient, String serviceId,
		String eventId, int timeSpan) {
		
		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(timeSpan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		EventSnapshotRequest eventsSnapshotRequest = EventSnapshotRequest.newBuilder()
				.setServiceId(serviceId)
				.setEventId(eventId)
				.setFrom(from.toString(fmt))
				.setTo(to.toString(fmt))
				.build();
	
		Response<EventSnapshotResult> eventSnapshotResult = apiClient.get(eventsSnapshotRequest);
		
		if (eventSnapshotResult.data == null) {
			return null;
		}
		
		return eventSnapshotResult.data.link;

	}
	
	public static EventsVolumeResult getEventsVolume(ApiClient apiClient, String serviceId,
		String viewId, int timeSpan) {
		
		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(timeSpan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		EventsVolumeRequest eventsVolumeRequest = EventsVolumeRequest.newBuilder()
				.setServiceId(serviceId)
				.setViewId(viewId)
				.setFrom(from.toString(fmt))
				.setTo(to.toString(fmt))
				.setVolumeType(VolumeType.all)
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
