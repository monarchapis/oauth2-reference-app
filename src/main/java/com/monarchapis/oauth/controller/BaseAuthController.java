package com.monarchapis.oauth.controller;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.monarchapis.driver.model.AuthorizationDetails;
import com.monarchapis.driver.model.Client;
import com.monarchapis.driver.model.LocaleInfo;
import com.monarchapis.driver.model.Message;
import com.monarchapis.driver.model.Permission;
import com.monarchapis.driver.service.v1.ServiceApi;
import com.monarchapis.oauth.extended.LoginExtendedData;
import com.monarchapis.oauth.extended.LoginExtendedDataRegistry;
import com.monarchapis.oauth.model.OAuthInfo;
import com.monarchapis.oauth.model.TypeReference;
import com.monarchapis.oauth.service.AuthenticationService;

public abstract class BaseAuthController extends BaseController {
	@Value("${google.analytics.account}")
	private String googleAccount;

	@Value("${oauth.forgot.password.url}")
	private String forgotPasswordUrl;

	@Value("${oauth.forgot.username.url}")
	private String forgotUsernameUrl;

	@Inject
	protected ServiceApi serviceApi;

	@Inject
	protected AuthenticationService authenticationService;

	@Inject
	protected LoginExtendedDataRegistry loginExtendedDataRegistry;

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

	protected Map<String, Object> getLoginExtendedData(String username, String password,
			AuthorizationDetails authorizationDetails) {
		Set<String> flags = new HashSet<String>();
		Map<String, Object> extended = new HashMap<String, Object>();

		for (Permission permission : authorizationDetails.getPermissions()) {
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

	protected String doAuthorize(HttpServletRequest request, HttpServletResponse response,
			OAuthInfo oauthInfo, AuthorizationDetails authorizationDetails, ModelMap model) {
		Client client = setAuthorizationModelAttributes(model, request, oauthInfo,
				authorizationDetails);
		model.put("phase", "authorize");

		if (client.getExpiration() != null) {
			int expriesIn = (int) client.getExpiration().longValue();
			int hours = expriesIn / 3600;
			int minutes = (expriesIn % 3600) / 60;
			int seconds = expriesIn % 60;

			Period period = Period.hours(hours).plusMinutes(minutes).plusSeconds(seconds);
			String formattedPeriod = PeriodFormat.getDefault().print(period.normalizedStandard());
			model.put("formattedPeriod", formattedPeriod);
		}

		return "form-authorize";
	}

	protected Client setAuthorizationModelAttributes(ModelMap model, HttpServletRequest request,
			OAuthInfo oauthInfo, AuthorizationDetails authorizationDetails) {
		Client client = authorizationDetails.getClient();
		List<Message> permissionMessages = getPermissionMessages(request,
				oauthInfo.getPermissions());

		model.put("application", authorizationDetails.getApplication());
		model.put("permissions", permissionMessages);
		model.put("client", authorizationDetails.getClient());
		return client;
	}

	protected List<Message> getPermissionMessages(HttpServletRequest request,
			Set<String> permissions) {
		if (permissions != null && permissions.size() > 0) {
			List<LocaleInfo> locales = getLocaleInfos(request);

			return serviceApi.getPermissionMessages(locales, permissions);
		}

		return Collections.emptyList();
	}

	private List<LocaleInfo> getLocaleInfos(HttpServletRequest request) {
		List<LocaleInfo> locales = new ArrayList<LocaleInfo>();
		Enumeration<?> e = request.getLocales();

		while (e.hasMoreElements()) {
			Locale locale = (Locale) e.nextElement();
			locales.add(new LocaleInfo(locale.getLanguage(), locale.getCountry(), locale
					.getVariant()));
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
		Map<String, Integer> failedLogins = getModelVariable("failedLogins",
				new TypeReference<Map<String, Integer>>() {
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
}
