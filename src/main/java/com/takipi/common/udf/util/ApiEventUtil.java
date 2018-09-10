package com.takipi.common.udf.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.takipi.common.api.ApiClient;
import com.takipi.common.api.request.event.EventSnapshotRequest;
import com.takipi.common.api.result.event.EventSnapshotResult;
import com.takipi.common.api.url.UrlClient.Response;

public class ApiEventUtil {
	public static String getEventRecentLink(ApiClient apiClient, String serviceId, String eventId, int timeSpan) {
		DateTime to = DateTime.now();
		DateTime from = to.minusMinutes(timeSpan);

		DateTimeFormatter fmt = ISODateTimeFormat.dateTime().withZoneUTC();

		EventSnapshotRequest eventsSnapshotRequest = EventSnapshotRequest.newBuilder().setServiceId(serviceId)
				.setEventId(eventId).setFrom(from.toString(fmt)).setTo(to.toString(fmt)).build();

		Response<EventSnapshotResult> eventSnapshotResult = apiClient.get(eventsSnapshotRequest);

		if ((eventSnapshotResult.isBadResponse()) || (eventSnapshotResult.data == null)) {
			return null;
		}

		return eventSnapshotResult.data.link;
	}
}
