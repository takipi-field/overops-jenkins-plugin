package com.takipi.common.udf.infra;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.takipi.common.api.util.CollectionUtil;

public class Categories {
	private static final String DEFAULT_CATEGORIES = "infra/categories.json";
	private static final Categories EMPTY_CATEGORIES = new Categories();
	
	private static boolean initialized;
	private static volatile Categories instance = null;
	
	public static Categories defaultCategories() {
		if ((instance == null) && (!initialized)) {
			synchronized (Categories.class) {
				if ((instance == null) && (!initialized)) {
					initialized = true;

					InputStream stream = null;

					try {
						ClassLoader classLoader = Categories.class.getClassLoader();

						stream = classLoader.getResourceAsStream(DEFAULT_CATEGORIES);

						if (stream == null) {
							return null;
						}

						instance = (new Gson()).fromJson(IOUtils.toString(stream, Charset.defaultCharset()),
								Categories.class);
					} catch (Exception e) {
						instance = EMPTY_CATEGORIES;
					} finally {
						IOUtils.closeQuietly(stream);
					}
				}
			}
		}

		return instance;
	}

	public List<Category> categories;

	public Set<String> getCategories(String className) {
		if (CollectionUtil.safeIsEmpty(categories)) {
			return Collections.emptySet();
		}

		Set<String> result = Sets.newHashSet();

		for (Category category : categories) {
			if ((CollectionUtil.safeIsEmpty(category.names)) || (CollectionUtil.safeIsEmpty(category.labels))) {
				continue;
			}

			for (String name : category.names) {
				if (className.startsWith(name)) {
					result.addAll(category.labels);
					break;
				}
			}
		}

		return result;
	}

	public static class Category {
		public List<String> names;
		public List<String> labels;
	}

	public static Categories from(Map<String, String> namespaceToLabel)
	{
		List<Category> categories = Lists.newArrayListWithExpectedSize(namespaceToLabel.size());
		
		for (Map.Entry<String, String> entry : namespaceToLabel.entrySet())
		{
			Category category = new Category();
			category.names = Collections.singletonList(entry.getKey());
			category.labels = Collections.singletonList(entry.getValue());
			
			categories.add(category);
		}
		
		Categories result = new Categories();
		result.categories = categories;
		
		return result;
	}
}
