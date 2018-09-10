package com.takipi.common.udf.input;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Input {
	private static final String COMMENT_LINE = "#";

	protected Input(String raw) {
		reflectiveSetFields(getPropertyMap(raw));
	}

	private void reflectiveSetFields(Map<String, String> properties) {
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			try {
				Field fld = getClass().getField(key);
				fld.setAccessible(true);

				final Class<?> type = fld.getType();

				if (type == String.class) {
					fld.set(this, value);
				} else if ((type == Boolean.class) || (type == boolean.class)) {
					fld.set(this, getBoolean(value));
				} else if ((type == Integer.class) || (type == int.class)) {
					fld.set(this, getInt(value));
				} else if ((type == Long.class) || (type == long.class)) {
					fld.set(this, getLong(value));
				} else if ((type == Double.class) || (type == double.class)) {
					fld.set(this, getDouble(value));
				} else if (type == List.class) {
					Type genericType = getGenericType(fld);

					if (genericType == String.class) {
						fld.set(this, getStringList(value));
					} else {
						throw new IllegalArgumentException("Invalid value for " + key + " - " + value);
					}
				} else if (type.isEnum()) {
					boolean enumTypeFound = false;

					for (Object enumeration : type.getEnumConstants()) {
						if (enumeration.toString().equals(value)) {
							fld.set(this, enumeration);
							enumTypeFound = true;
							break;
						}
					}

					if (!enumTypeFound) {
						throw new IllegalArgumentException("Invalid value for " + key + " - " + value);
					}
				} else {
					throw new UnsupportedOperationException("No support for type - " + type);
				}
			} catch (NoSuchFieldException e) {
				throw new IllegalArgumentException("No field called " + key, e);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Failed setting field - " + key, e);
			}
		}
	}

	private static boolean getBoolean(String value) {
		if (StringUtils.isEmpty(value)) {
			return false;
		}

		return Boolean.parseBoolean(value.trim());
	}

	private static int getInt(String value) {
		if (StringUtils.isEmpty(value)) {
			return 0;
		}

		return Integer.parseInt(value.trim());
	}

	private static long getLong(String value) {
		if (StringUtils.isEmpty(value)) {
			return 0l;
		}

		return Long.parseLong(value.trim());
	}

	private static double getDouble(String value) {
		if (StringUtils.isEmpty(value)) {
			return 0.0;
		}

		return Double.parseDouble(value.trim());
	}

	private static List<String> getStringList(String value) {
		if ((value == null) || (value.isEmpty())) {
			return Collections.emptyList();
		}

		String[] split = value.split(";");

		List<String> result = Lists.newArrayListWithCapacity(split.length);

		Collections.addAll(result, split);

		return result;
	}

	private static Type getGenericType(Field fld) {
		Type[] genericTypes = getGenericTypes(fld);

		if (genericTypes == null) {
			return null;
		}

		return genericTypes[0];
	}

	private static Type[] getGenericTypes(Field fld) {
		Type type = fld.getGenericType();

		if (type instanceof ParameterizedType) {
			ParameterizedType genericType = (ParameterizedType) type;

			return genericType.getActualTypeArguments();
		} else {
			return null;
		}
	}

	private static Map<String, String> getPropertyMap(String rawInput) {
		Map<String, String> result = Maps.newHashMap();

		String lines[] = rawInput.split("\\r?\\n");

		for (String line : lines) {
			String actualLine = StringUtils.trimToEmpty(line);

			if ((actualLine.startsWith(COMMENT_LINE)) || (actualLine.isEmpty())) {
				continue;
			}

			int index = actualLine.indexOf('=');

			if ((index <= 0) || (index > actualLine.length() - 1)) {
				throw new IllegalArgumentException("Invalid input line - " + actualLine);
			}

			String key = StringUtils.trim(actualLine.substring(0, index));
			String value = StringUtils.trim(actualLine.substring(index + 1, actualLine.length()));

			if (key.isEmpty()) {
				throw new IllegalArgumentException("Invalid input line - " + actualLine);
			}

			result.put(key, value);
		}

		return result;
	}
}
