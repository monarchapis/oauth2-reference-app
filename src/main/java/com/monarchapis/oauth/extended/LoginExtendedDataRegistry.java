package com.monarchapis.oauth.extended;

import java.util.Collections;
import java.util.List;

import javax.inject.Named;

@Named
public class LoginExtendedDataRegistry {
	private List<LoginExtendedData> items;

	public List<LoginExtendedData> getItems() {
		return Collections.unmodifiableList(items);
	}

	public void setItems(List<LoginExtendedData> items) {
		this.items = items;
	}
}
