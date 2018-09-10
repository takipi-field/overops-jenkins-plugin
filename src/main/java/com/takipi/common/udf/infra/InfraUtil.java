package com.takipi.common.udf.infra;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Set;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.takipi.common.api.ApiClient;
import com.takipi.common.api.data.category.Category;
import com.takipi.common.api.data.event.Location;
import com.takipi.common.api.data.view.SummarizedView;
import com.takipi.common.api.data.view.ViewFilters;
import com.takipi.common.api.data.view.ViewInfo;
import com.takipi.common.api.request.category.CategoriesRequest;
import com.takipi.common.api.request.category.CategoryAddViewRequest;
import com.takipi.common.api.request.category.CreateCategoryRequest;
import com.takipi.common.api.request.event.EventModifyLabelsRequest;
import com.takipi.common.api.request.event.EventRequest;
import com.takipi.common.api.request.label.CreateLabelRequest;
import com.takipi.common.api.request.view.CreateViewRequest;
import com.takipi.common.api.request.view.ViewsRequest;
import com.takipi.common.api.result.EmptyResult;
import com.takipi.common.api.result.category.CategoriesResult;
import com.takipi.common.api.result.category.CreateCategoryResult;
import com.takipi.common.api.result.event.EventResult;
import com.takipi.common.api.result.view.CreateViewResult;
import com.takipi.common.api.result.view.ViewsResult;
import com.takipi.common.api.url.UrlClient.Response;
import com.takipi.common.api.util.CollectionUtil;

public class InfraUtil {
	private static final String INFRA_SUFFIX = ".infra";

	public static void categorizeEvent(String eventId, String serviceId, String categoryId, Categories categories,
			Set<String> existingLabels, ApiClient apiClient) {
		EventRequest metadataRequest = EventRequest.newBuilder().setEventId(eventId).setServiceId(serviceId).build();

		Response<EventResult> metadataResult = apiClient.get(metadataRequest);

		if (metadataResult.isBadResponse()) {
			throw new IllegalStateException("Can't apply infrastructure routing to event " + eventId);
		}

		categorizeEvent(metadataResult.data, serviceId, categoryId, categories, existingLabels, apiClient);
	}

	public static void categorizeEvent(EventResult event, String serviceId, String categoryId, Categories categories,
			Set<String> existingLabels, ApiClient apiClient) {
		if ((event == null) || (event.error_origin == null)) {
			return;
		}

		Location errorOrigin = event.error_origin;

		Set<String> locationLabels = categories.getCategories(errorOrigin.class_name);

		if (locationLabels.isEmpty()) {
			return;
		}

		Set<String> applyLabels = Sets.newHashSet();

		for (String locationLabel : locationLabels) {
			applyLabels.add(toInternalInfraLabelName(locationLabel));

			if (!existingLabels.add(locationLabel)) {
				continue;
			}

			boolean labelExisted = createInfraLabel(locationLabel, serviceId, apiClient);

			if (labelExisted) {
				continue;
			}

			String viewId = createInfraView(locationLabel, serviceId, apiClient);
			addViewToCategory(categoryId, viewId, serviceId, apiClient);
		}

		EventModifyLabelsRequest addLabelsRequest = EventModifyLabelsRequest.newBuilder().setServiceId(serviceId)
				.setEventId(event.id).addLabels(applyLabels).build();

		Response<EmptyResult> addResult = apiClient.post(addLabelsRequest);

		if (addResult.isBadResponse()) {
			throw new IllegalStateException("Can't apply labels to event " + event.id);
		}
	}

	// Returns true if the label already existed.
	//
	public static boolean createInfraLabel(String labelName, String serviceId, ApiClient apiClient) {
		String infraLabelName = toInternalInfraLabelName(labelName);

		CreateLabelRequest createLabelRequest = CreateLabelRequest.newBuilder().setServiceId(serviceId)
				.setName(infraLabelName).build();

		Response<EmptyResult> createResponse = apiClient.post(createLabelRequest);

		if ((createResponse.isBadResponse()) && (createResponse.responseCode != HttpURLConnection.HTTP_CONFLICT)) {
			throw new IllegalStateException("Can't create label " + infraLabelName);
		}

		return (createResponse.responseCode == HttpURLConnection.HTTP_CONFLICT);
	}

	public static String createInfraView(String labelName, String serviceId, ApiClient apiClient) {
		ViewFilters viewFilters = new ViewFilters();
		viewFilters.labels = Collections.singletonList(toInternalInfraLabelName(labelName));

		ViewInfo viewInfo = new ViewInfo();
		viewInfo.name = labelName;
		viewInfo.shared = true;
		viewInfo.filters = viewFilters;

		CreateViewRequest createViewRequest = CreateViewRequest.newBuilder().setServiceId(serviceId)
				.setViewInfo(viewInfo).build();

		Response<CreateViewResult> createViewResponse = apiClient.post(createViewRequest);

		if (createViewResponse.isOK()) {
			CreateViewResult createViewResult = createViewResponse.data;

			if ((createViewResult == null) || (Strings.isNullOrEmpty(createViewResult.view_id))) {
				throw new IllegalStateException("Failed creating view - " + labelName);
			}

			return createViewResult.view_id;
		}

		if (createViewResponse.responseCode != HttpURLConnection.HTTP_CONFLICT) {
			throw new IllegalStateException("Failed creating view - " + labelName);
		}

		ViewsRequest getViewsRequest = ViewsRequest.newBuilder().setServiceId(serviceId).setViewName(labelName).build();

		Response<ViewsResult> getViewsResponse = apiClient.get(getViewsRequest);

		if ((getViewsResponse.isBadResponse()) || (getViewsResponse.data == null)
				|| (CollectionUtil.safeIsEmpty(getViewsResponse.data.views))) {
			throw new IllegalStateException("Failed getting view - " + labelName);
		}

		SummarizedView view = getViewsResponse.data.views.get(0);

		if ((!labelName.equalsIgnoreCase(view.name)) || (Strings.isNullOrEmpty(view.id))) {
			throw new IllegalStateException("Failed getting view - " + labelName);
		}

		return view.id;
	}

	public static String createCategory(String categoryName, String serviceId, ApiClient apiClient) {
		CreateCategoryRequest createCategoryRequest = CreateCategoryRequest.newBuilder().setServiceId(serviceId)
				.setName(categoryName).setShared(true).build();

		Response<CreateCategoryResult> createCategoryResponse = apiClient.post(createCategoryRequest);

		if (createCategoryResponse.isOK()) {
			CreateCategoryResult createCategoryResult = createCategoryResponse.data;

			if ((createCategoryResult == null) || (Strings.isNullOrEmpty(createCategoryResult.category_id))) {
				throw new IllegalStateException("Failed creating category - " + categoryName);
			}

			return createCategoryResult.category_id;
		}

		if (createCategoryResponse.responseCode != HttpURLConnection.HTTP_CONFLICT) {
			throw new IllegalStateException("Failed creating category - " + categoryName);
		}

		CategoriesRequest getCategoriesRequest = CategoriesRequest.newBuilder().setServiceId(serviceId).build();

		Response<CategoriesResult> getCategoriesResponse = apiClient.get(getCategoriesRequest);

		if ((getCategoriesResponse.isBadResponse()) || (getCategoriesResponse.data == null)
				|| (CollectionUtil.safeIsEmpty(getCategoriesResponse.data.categories))) {
			throw new IllegalStateException("Failed getting category - " + categoryName);
		}

		for (Category category : getCategoriesResponse.data.categories) {
			if ((categoryName.equalsIgnoreCase(category.name)) && (!Strings.isNullOrEmpty(category.id))) {
				return category.id;
			}
		}

		throw new IllegalStateException("Failed getting category - " + categoryName);
	}

	public static void addViewToCategory(String categoryId, String viewId, String serviceId, ApiClient apiClient) {
		CategoryAddViewRequest categoryAddViewRequest = CategoryAddViewRequest.newBuilder().setServiceId(serviceId)
				.setCategoryId(categoryId).setViewId(viewId).build();

		Response<EmptyResult> createResponse = apiClient.post(categoryAddViewRequest);

		if (createResponse.isBadResponse()) {
			throw new IllegalStateException("Failed adding view " + viewId + " to category " + categoryId);
		}
	}

	private static String toInternalInfraLabelName(String labelName) {
		return labelName + INFRA_SUFFIX;
	}
}
