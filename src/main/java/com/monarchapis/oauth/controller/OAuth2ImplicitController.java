package com.monarchapis.oauth.controller;

import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

import com.monarchapis.driver.exception.ApiErrorException;
import com.monarchapis.driver.model.AuthorizationDetails;
import com.monarchapis.driver.model.Permission;
import com.monarchapis.oauth.model.OAuthInfo;

@Controller
@SessionAttributes(value = { "oauthInfo", "authorizationDetails", "phase", "failedLogins",
		"username" })
public class OAuth2ImplicitController extends BaseAuthController {
	private static final Logger logger = LoggerFactory.getLogger(OAuth2ImplicitController.class);

	private static final String AUTH_SCHEME = "oauth2-implicit";

	/* AUTHORIZATION UI ----------------------------------------------------- */

	@RequestMapping(value = "/authorize", params = "response_type=token", method = RequestMethod.GET)
	public String renderUI(
			@RequestHeader(value = "referer", required = false) final String refererUrl,
			@RequestHeader(value = "") @RequestParam(value = "client_id") final String apiKey,
			@RequestParam(value = "redirect_uri", required = false) final String redirectUri,
			@RequestParam(value = "scope", required = false) String scope,
			@RequestParam(value = "state", required = false) final String state,
			DefaultSessionAttributeStore status, WebRequest request, ModelMap model,
			HttpServletResponse response) throws Exception {
		String callbackUri = redirectUri != null ? redirectUri : refererUrl;

		try {
			Map<String, Integer> failedLogins = getFailedLogins(model);
			model.put("failedLogins", failedLogins);

			if (callbackUri == null) {
				logger.debug("The callback URI was not provided");
				return returnInvalidRequest(model, callbackUri, state);
			}

			Set<String> permissions = scopeToPermissions(scope);

			AuthorizationDetails authorizationDetails = serviceApi.getAuthorizationDetails(
					AUTH_SCHEME, apiKey, callbackUri, permissions);

			if (authorizationDetails == null) {
				logger.debug("Application not found for {}, {}", apiKey, callbackUri);
				return returnInvalidRequest(model, callbackUri, state);
			}

			// Use the permission list returned by the service because globally
			// managed permissions might be more inclusive.
			if (authorizationDetails.getPermissions() != null) {
				permissions.clear();

				for (Permission permission : authorizationDetails.getPermissions()) {
					permissions.add(permission.getName());
				}
			}

			OAuthInfo oauthInfo = new OAuthInfo();
			oauthInfo.setResponseType("implicit");
			oauthInfo.setRedirectUri(redirectUri);
			oauthInfo.setPermissions(permissions);
			oauthInfo.setState(state);
			oauthInfo.setScheme(AUTH_SCHEME);

			setSessionVariable("oauthInfo", oauthInfo, status, request, model);
			setSessionVariable("authorizationDetails", authorizationDetails, status, request, model);

			model.put("phase", "authenticate");

			return "authorize";
		} catch (ApiErrorException are) {
			logger.debug("The service API returned an error", are);
			return returnInvalidRequest(model, callbackUri, state);
		} catch (Throwable e) {
			logger.error("Internal error occurred", e);
			return returnInternalError(model, callbackUri, state);
		}
	}
}
