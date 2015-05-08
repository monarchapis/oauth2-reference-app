package com.monarchapis.oauth.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Optional;
import com.monarchapis.api.exception.ApiError;
import com.monarchapis.api.exception.ApiErrorException;
import com.monarchapis.api.v1.client.SecurityResource;
import com.monarchapis.api.v1.client.ServiceApi;
import com.monarchapis.api.v1.model.AuthorizationDetails;
import com.monarchapis.api.v1.model.AuthorizationRequest;
import com.monarchapis.api.v1.model.ClientAuthenticationRequest;
import com.monarchapis.api.v1.model.ClientDetails;
import com.monarchapis.api.v1.model.LocaleInfo;
import com.monarchapis.api.v1.model.MessageDetails;
import com.monarchapis.api.v1.model.MessageDetailsList;
import com.monarchapis.api.v1.model.PermissionDetails;
import com.monarchapis.api.v1.model.PermissionMessagesRequest;
import com.monarchapis.oauth.extended.LoginExtendedData;
import com.monarchapis.oauth.extended.LoginExtendedDataRegistry;
import com.monarchapis.oauth.model.OAuthInfo;
import com.monarchapis.oauth.model.TypeReference;
import com.monarchapis.oauth.service.AuthenticationService;

public abstract class BaseAuthController extends BaseController {
	private static final Logger logger = LoggerFactory.getLogger(BaseAuthController.class);

	@Value("${google.analytics.account}")
	private String googleAccount;

	@Value("${oauth.forgot.password.url}")
	private String forgotPasswordUrl;

	@Value("${oauth.forgot.username.url}")
	private String forgotUsernameUrl;

	@Value("${company.name}")
	private String companyName;

	@Inject
	protected ServiceApi serviceApi;

	@Inject
	protected AuthenticationService authenticationService;

	@Inject
	protected LoginExtendedDataRegistry loginExtendedDataRegistry;

	protected String getAuthorizationScheme() {
		throw new UnsupportedOperationException();
	}

	protected String getOAuthResponseType() {
		throw new UnsupportedOperationException();
	}

	protected String doAuthorize(String apiKey, final String redirectUri, String scope, final String state,
			final String refererUrl, DefaultSessionAttributeStore status, WebRequest request, ModelMap model)
			throws UnsupportedEncodingException {
		String callbackUri = redirectUri != null ? redirectUri : refererUrl;

		try {
			Map<String, Integer> failedLogins = getFailedLogins(model);
			model.put("failedLogins", failedLogins);

			if (callbackUri == null) {
				logger.debug("The callback URI was not provided");
				return returnInvalidRequest(model, callbackUri, state);
			}

			Set<String> permissions = scopeToPermissions(scope);

			AuthorizationDetails authorizationDetails = getAuthorizationDetails(apiKey, callbackUri, permissions);

			OAuthInfo oauthInfo = new OAuthInfo();
			oauthInfo.setResponseType(getOAuthResponseType());
			oauthInfo.setRedirectUri(redirectUri);
			oauthInfo.setPermissions(permissions);
			oauthInfo.setState(state);
			oauthInfo.setScheme(getAuthorizationScheme());

			setSessionVariable("oauthInfo", oauthInfo, status, request, model);
			setSessionVariable("authorizationDetails", authorizationDetails, status, request, model);

			model.put("phase", "authenticate");

			return "authorize";
		} catch (ApiErrorException are) {
			logger.debug("The service API returned an error", are);
			return returnInvalidRequest(model, callbackUri, state);
		} catch (Exception e) {
			logger.error("Internal error occurred", e);
			return returnInternalError(model, callbackUri, state);
		}
	}

	protected Set<String> scopeToPermissions(String scope) {
		Set<String> ret = new HashSet<String>();

		if (scope != null) {
			String[] split = StringUtils.split(scope, ',');

			for (String item : split) {
				ret.add(item.trim());
			}
		}

		return ret;
	}

	protected AuthorizationDetails getAuthorizationDetails(String apiKey, String callbackUri, Set<String> permissions) {
		SecurityResource securityResource = serviceApi.getSecurityResource();

		AuthorizationRequest ar = new AuthorizationRequest();
		ar.setAuthorizationScheme(getAuthorizationScheme());
		ar.setApiKey(apiKey);
		ar.setCallbackUri(Optional.of(callbackUri));
		ar.setPermissions(permissions);

		logger.debug("Fetching authorization details");
		AuthorizationDetails authorizationDetails = securityResource.getAuthorizationDetails(ar);

		// Use the permission list returned by the service because globally
		// managed permissions might be more inclusive.
		if (authorizationDetails.getPermissions() != null) {
			permissions.clear();

			for (PermissionDetails permission : authorizationDetails.getPermissions()) {
				permissions.add(permission.getName());
			}
		}

		return authorizationDetails;
	}

	protected Map<String, Object> getLoginExtendedData(String username, String password,
			AuthorizationDetails authorizationDetails) {
		Set<String> flags = new HashSet<String>();
		Map<String, Object> extended = new HashMap<String, Object>();

		for (PermissionDetails permission : authorizationDetails.getPermissions()) {
			Set<String> next = permission.getFlags();

			if (next != null) {
				flags.addAll(next);
			}
		}

		for (LoginExtendedData loginExtendedData : loginExtendedDataRegistry.getItems()) {
			if (loginExtendedData.isApplicable(flags)) {
				loginExtendedData.addExtendedData(username, password, extended);
			}
		}

		return extended;
	}

	protected String doAuthorize(HttpServletRequest request, HttpServletResponse response, OAuthInfo oauthInfo,
			AuthorizationDetails authorizationDetails, ModelMap model) {
		ClientDetails client = setAuthorizationModelAttributes(model, request, oauthInfo, authorizationDetails);
		model.put("phase", "authorize");

		if (client.getExpiration().isPresent()) {
			int expriesIn = client.getExpiration().get().intValue();
			int hours = expriesIn / 3600;
			int minutes = (expriesIn % 3600) / 60;
			int seconds = expriesIn % 60;

			Period period = Period.hours(hours).plusMinutes(minutes).plusSeconds(seconds);
			String formattedPeriod = PeriodFormat.getDefault().print(period.normalizedStandard());
			model.put("formattedPeriod", formattedPeriod);
		}

		return "form-authorize";
	}

	protected ClientDetails setAuthorizationModelAttributes(ModelMap model, HttpServletRequest request,
			OAuthInfo oauthInfo, AuthorizationDetails authorizationDetails) {
		ClientDetails client = authorizationDetails.getClient();
		List<MessageDetails> permissionMessages = getPermissionMessages(request, oauthInfo.getPermissions());

		model.put("application", authorizationDetails.getApplication());
		model.put("permissions", permissionMessages);
		model.put("client", authorizationDetails.getClient());

		return client;
	}

	protected List<MessageDetails> getPermissionMessages(HttpServletRequest request, Set<String> permissions) {
		if (permissions != null && permissions.size() > 0) {
			List<LocaleInfo> locales = getLocaleInfos(request);

			PermissionMessagesRequest pmr = new PermissionMessagesRequest();
			pmr.setLocales(locales);
			pmr.setPermissions(permissions);

			MessageDetailsList list = serviceApi.getSecurityResource().getPermissionMessages(pmr);

			return list.getItems();
		}

		return Collections.emptyList();
	}

	private List<LocaleInfo> getLocaleInfos(HttpServletRequest request) {
		List<LocaleInfo> locales = new ArrayList<LocaleInfo>();
		Enumeration<?> e = request.getLocales();

		while (e.hasMoreElements()) {
			Locale locale = (Locale) e.nextElement();
			LocaleInfo li = new LocaleInfo();
			li.setLanguage(locale.getLanguage());
			li.setCountry(Optional.fromNullable(locale.getCountry()));
			li.setVariant(Optional.fromNullable(locale.getVariant()));
			locales.add(li);
		}

		return locales;
	}

	protected void incrementFailedLogins(ModelMap model, String username) {
		Map<String, Integer> failedLogins = getFailedLogins(model);

		if (failedLogins.containsKey(username)) {
			failedLogins.put(username, failedLogins.get(username) + 1);
		} else {
			failedLogins.put(username, 1);
		}

		model.put("failedLogins", failedLogins);
	}

	protected Map<String, Integer> getFailedLogins(ModelMap model) {
		Map<String, Integer> failedLogins = getModelVariable("failedLogins", new TypeReference<Map<String, Integer>>() {
		}, model);

		if (failedLogins == null) {
			failedLogins = new HashMap<String, Integer>();
			model.put("failedLogins", failedLogins);
		}

		return failedLogins;
	}

	protected static String returnInvalidRequest(ModelMap model, String redirectUri, String state)
			throws UnsupportedEncodingException {
		model.put("phase", "invalid-request");
		setReturnUrl(model, redirectUri, state);

		return "authorize";
	}

	protected static String returnInternalError(ModelMap model, String redirectUri, String state)
			throws UnsupportedEncodingException {
		model.put("phase", "internal-error");
		setReturnUrl(model, redirectUri, state);

		return "authorize";
	}

	protected static void setReturnUrl(ModelMap model, String redirectUri, String state) {
		String returnUrl = getReturnUrl(redirectUri, state);

		if (returnUrl != null) {
			model.put("returnUrl", returnUrl);
		}
	}

	protected static String getReturnUrl(String redirectUri, String state) {
		if (redirectUri != null) {
			StringBuffer sb = new StringBuffer(redirectUri);
			sb.append(redirectUri.contains("?") ? '&' : '?');
			sb.append("error_reason=user_denied");
			sb.append("&error=access_denied");
			sb.append("&error_description=The+user+denied+your+request.");

			if (state != null) {
				sb.append("&state=");

				try {
					sb.append(URLEncoder.encode(state, "UTF-8"));
				} catch (UnsupportedEncodingException e) {
					sb.append(state);
				}
			}

			return sb.toString();
		}

		return null;
	}

	protected static String getBaseUrl(HttpServletRequest request) {
		String protocol = getProtocol(request);
		String hostname = getHostname(request);
		String port = getPort(request);

		return String.format("%s://%s%s%s", protocol, hostname, port, request.getContextPath());
	}

	protected ResponseEntity<byte[]> authenticateClient(String authorizationScheme, String apiKey, String sharedSecret)
			throws JsonGenerationException, JsonProcessingException, IOException {
		ClientAuthenticationRequest car = new ClientAuthenticationRequest();
		car.setAuthorizationScheme(authorizationScheme);
		car.setApiKey(apiKey);
		car.setSharedSecret(Optional.fromNullable(sharedSecret));

		try {
			logger.debug("Authenticating client {}", apiKey);
			serviceApi.getSecurityResource().authenticateClient(car);

			return null;
		} catch (ApiErrorException apie) {
			ApiError error = apie.getError();

			if (error.getCode() == 401) {
				return error("access_denied");
			} else {
				return internalError();
			}
		}
	}

	private static String getProtocol(HttpServletRequest request) {
		return request.isSecure() ? "https" : "http";
	}

	private static String getHostname(HttpServletRequest request) {
		return request.getServerName();
	}

	private static String getPort(HttpServletRequest request) {
		int port = request.getServerPort();

		if ((!request.isSecure() && port == 80) || (request.isSecure() && port == 443)) {
			return "";
		} else {
			return ":" + port;
		}
	}

	@ModelAttribute("prefix")
	public String getViewPrefix() {
		return "authorize";
	}

	@ModelAttribute("gaAccount")
	public String getGoogleAnalyticsAccount() {
		return googleAccount;
	}

	@ModelAttribute("forgotPasswordUrl")
	public String getForgotPasswordUrl() {
		return forgotPasswordUrl;
	}

	@ModelAttribute("forgotUsernameUrl")
	public String getForgotUsernameUrl() {
		return forgotUsernameUrl;
	}

	@ModelAttribute("companyName")
	public String getCompanyName() {
		return companyName;
	}
}
