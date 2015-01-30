package com.monarchapis.oauth.extended;

import java.util.Map;
import java.util.Set;

public interface LoginExtendedData {
	public boolean isApplicable(Set<String> flags);

	public void addExtendedData(String username, String password, Map<String, Object> extended);
}
