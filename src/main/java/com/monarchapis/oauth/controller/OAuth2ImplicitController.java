package com.monarchapis.oauth.controller;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

@Controller
@SessionAttributes(value = { "oauthInfo", "authorizationDetails", "phase", "failedLogins", "username" })
public class OAuth2ImplicitController extends BaseAuthController {
	private static final String AUTH_SCHEME = "oauth2-implicit";

	protected String getAuthorizationScheme() {
		return AUTH_SCHEME;
	}

	protected String getOAuthResponseType() {
		return "implicit";
	}

	/* AUTHORIZATION UI ----------------------------------------------------- */

	@RequestMapping(value = "/authorize", params = "response_type=token", method = RequestMethod.GET)
	public String renderUI(@RequestHeader(value = "referer", required = false) final String refererUrl,
			@RequestHeader(value = "") @RequestParam(value = "client_id") final String apiKey,
			@RequestParam(value = "redirect_uri", required = false) final String redirectUri,
			@RequestParam(value = "scope", required = false) String scope,
			@RequestParam(value = "state", required = false) final String state, DefaultSessionAttributeStore status,
			WebRequest request, ModelMap model, HttpServletResponse response) throws Exception {
		return doAuthorize(apiKey, redirectUri, scope, state, refererUrl, status, request, model);
	}
}
