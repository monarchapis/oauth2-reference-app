package com.monarchapis.oauth.service;

import com.monarchapis.oauth.model.LoginResult;

public interface AuthenticationService {
	public LoginResult authenticate(String username, String password);
}
