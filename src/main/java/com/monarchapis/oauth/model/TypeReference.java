package com.monarchapis.oauth.model;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeReference<T> implements Comparable<TypeReference<T>> {
	private final Type _type;
	private T object;

	protected TypeReference() {
		Type superClass = getClass().getGenericSuperclass();
		if (superClass instanceof Class<?>) {
			throw new IllegalArgumentException(
					"Internal error: TypeReference constructed without actual type information");
		}

		_type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
	}

	protected TypeReference(T object) {
		this();
		this.object = object;
	}

	public Type getType() {
		return _type;
	}

	public T getObject() {
		return object;
	}

	@Override
	public int compareTo(TypeReference<T> o) {
		// just need an implementation, not a good one... hence:
		return 0;
	}
}