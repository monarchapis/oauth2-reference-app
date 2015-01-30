package com.monarchapis.oauth.controller;

import java.net.URLEncoder;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

import com.monarchapis.driver.model.AuthorizationDetails;
import com.monarchapis.driver.model.Client;
import com.monarchapis.driver.model.Token;
import com.monarchapis.driver.model.TokenRequest;
import com.monarchapis.oauth.model.LoginResult;
import com.monarchapis.oauth.model.OAuthInfo;

@Controller
@SessionAttributes(value = { "oauthInfo", "authorizationDetails", "phase", "failedLogins",
		"username" })
public class OAuth2CommonController extends BaseAuthController {
	private static final Logger logger = LoggerFactory.getLogger(OAuth2CommonController.class);

	/**
	 * Checks the validity of the browser. Finds if the browser is "invalid" by
	 * checking for:
	 * 
	 * 1. Is the browser using an iFrame? 2. Is the browser using UIWebView
	 * control (iOS)? 3. Is the browser using a pop-up window?
	 * 
	 * @param standalone
	 *            - is the browser window standing alone?
	 * @param userAgent
	 *            - what browser opened/handled the page?
	 * @param framed
	 *            - is the browser window framed?
	 * @return - true if the browser is verified and valid, false if the browser
	 *         was flagged as invalid.
	 */
	@RequestMapping(value = "/authorize/renderForm", method = RequestMethod.POST)
	public String renderForm(
			@RequestHeader(value = "User-Agent", required = false) String userAgent,
			HttpServletResponse response, ModelMap model,
			@RequestParam("standalone") boolean standalone, @RequestParam("framed") boolean framed,
			@RequestParam("popup") boolean popup, @RequestParam("webview") boolean isWebView)
			throws Exception {
		try {
			String phase = getModelVariable("phase", String.class, model);
			OAuthInfo oauthInfo = getModelVariable("oauthInfo", OAuthInfo.class, model);
			AuthorizationDetails authorizationDetails = getModelVariable("authorizationDetails",
					AuthorizationDetails.class, model);
			Client client = authorizationDetails.getClient();

			if (!nullCheck(phase, oauthInfo)) {
				logger.debug("Session expired");
				return "session-expired";
			}

			if (!"authenticate".equals(phase)) {
				logger.debug("Invalid phase {}", phase);
				model.put("phase", "invalid-request");
				return "form-invalid-request";
			}

			// 1. Is the browser using an iFrame?
			//
			if (framed) {
				logger.debug("Framed OAuth is not allowed for this client");
				model.put("phase", "invalid-request");
				return "form-invalid-request";
			}

			// 2. Is the browser using UIWebView control (iOS)?
			//
			if (isWebView && !standalone && !userAgent.matches("/safari/")
					&& !client.isAllowWebView()) {
				logger.debug("Web view controls are not allowed for this client");
				model.put("phase", "invalid-request");
				return "form-invalid-request";
			}

			// 3. Is the browser using a popup window?
			//
			if (popup && !client.isAllowPopup()) {
				logger.debug("Pop up windows are not allowed for this client");
				model.put("phase", "invalid-request");
				return "form-invalid-request";
			}

			return "form";
		} catch (Throwable e) {
			return handleInternalError(response, e);
		}
	}

	@RequestMapping(value = "/authorize/authenticate", method = RequestMethod.POST)
	public String authenticate(@RequestParam("username") String username,
			@RequestParam("password") String password, HttpServletRequest req,
			HttpServletResponse response, DefaultSessionAttributeStore status, WebRequest request,
			ModelMap model) throws Exception {
		try {
			String phase = getModelVariable("phase", String.class, model);
			OAuthInfo oauthInfo = getModelVariable("oauthInfo", OAuthInfo.class, model);
			AuthorizationDetails authorizationDetails = getModelVariable("authorizationDetails",
					AuthorizationDetails.class, model);

			if (!nullCheck(phase, oauthInfo, authorizationDetails)) {
				return error(response, "session expired");
			}

			if (!"authenticate".equals(phase)) {
				return error(response, "There was an error in the request.");
			}

			LoginResult loginResult = authenticationService.authenticate(username, password);

			if (loginResult == LoginResult.SUCCESS) {
				setSessionVariable("username", username, status, request, model);

				Map<String, Object> extended = getLoginExtendedData(username, password,
						authorizationDetails);
				oauthInfo.setExtendedData(extended);
				setSessionVariable("oauthInfo", oauthInfo, status, request, model);

				if (authorizationDetails.getClient().isAutoAuthorize()) {
					model.put("phase", "authorize");
					return authorizationDecision("accepted", req, response, status, model);
				}

				return doAuthorize(req, response, oauthInfo, authorizationDetails, model);
			} else {
				incrementFailedLogins(model, username);
			}

			if (loginResult == LoginResult.LOCKED) {
				model.put("phase", "locked");
				return "form-locked";
			}

			return error(
					response,
					"The information you entered doesn't match what we have on file.  Please check the information you entered.",
					HttpStatus.UNAUTHORIZED);
		} catch (Throwable e) {
			return handleInternalError(response, e);
		}
	}

	@RequestMapping(value = "/authorize/authorize", method = RequestMethod.POST)
	public String authorizationDecision(@RequestParam("choice") String choice,
			HttpServletRequest request, HttpServletResponse response,
			DefaultSessionAttributeStore status, ModelMap model) throws Exception {
		try {
			String phase = getModelVariable("phase", String.class, model);
			OAuthInfo oauthInfo = getModelVariable("oauthInfo", OAuthInfo.class, model);
			AuthorizationDetails authorizationDetails = getModelVariable("authorizationDetails",
					AuthorizationDetails.class, model);
			String username = getModelVariable("username", String.class, model);

			if (!nullCheck(phase, oauthInfo, username)) {
				return "session-expired";
			}

			if (!"authorize".equals(phase)) {
				return "error";
			}

			String redirectUri = oauthInfo.getRedirectUri();
			StringBuffer sb = new StringBuffer(redirectUri);
			boolean accepted = false;

			String qsSeperator = "implicit".equals(oauthInfo.getResponseType()) ? "#" : "?";
			sb.append(redirectUri.contains(qsSeperator) ? "&" : qsSeperator);

			if ("accepted".equals(choice)) {
				if ("code".equals(oauthInfo.getResponseType())) {
					TokenRequest codeRequest = new TokenRequest();
					codeRequest.setAuthorizationScheme(oauthInfo.getScheme());
					codeRequest.setApiKey(authorizationDetails.getClient().getApiKey());
					codeRequest.setGrantType("authorization_code");
					codeRequest.setPermissions(oauthInfo.getPermissions());
					codeRequest.setUri(redirectUri);
					codeRequest.setUserId(username);
					codeRequest.setUserContext(username);
					codeRequest.setTokenType("authorization");
					codeRequest.setState(oauthInfo.getState());
					codeRequest.setExtended(oauthInfo.getExtendedData());

					logger.debug("Creating authorization code: {}", codeRequest);
					Token authorizationCode = serviceApi.createToken(codeRequest);

					sb.append("code=");
					sb.append(URLEncoder.encode(authorizationCode.getToken(), "UTF-8"));
				} else if ("implicit".equals(oauthInfo.getResponseType())) {
					TokenRequest tokenRequest = new TokenRequest();
					tokenRequest.setAuthorizationScheme(oauthInfo.getScheme());
					tokenRequest.setApiKey(authorizationDetails.getClient().getApiKey());
					tokenRequest.setGrantType("implicit");
					tokenRequest.setPermissions(oauthInfo.getPermissions());
					tokenRequest.setUri(redirectUri);
					tokenRequest.setUserId(username);
					tokenRequest.setUserContext(username);
					tokenRequest.setExtended(oauthInfo.getExtendedData());
					tokenRequest.setTokenType("bearer");
					// Omitting state from the bearer token

					logger.debug("Creating access token: {}", tokenRequest);
					Token accessToken = serviceApi.createToken(tokenRequest);

					sb.append("access_token=");
					sb.append(URLEncoder.encode(accessToken.getToken(), "UTF-8"));
					sb.append("&token_type=");
					sb.append(URLEncoder.encode(accessToken.getTokenType(), "UTF-8"));

					if (accessToken.getExpiresIn() != null) {
						sb.append("&expires_in=");
						sb.append(accessToken.getExpiresIn());
					}
				}

				accepted = true;
			} else {
				sb.append("error_reason=user_denied");
				sb.append("&error=access_denied");
				sb.append("&error_description=The+user+denied+your+request.");
			}

			if (oauthInfo.getState() != null) {
				sb.append("&state=");
				sb.append(URLEncoder.encode(oauthInfo.getState(), "UTF-8"));
			}

			model.clear();

			setAuthorizationModelAttributes(model, request, oauthInfo, authorizationDetails);
			model.put("redirectUrl", sb.toString());

			return accepted ? "result-accepted" : "result-declined";
		} catch (Exception e) {
			logger.error("Error", e);
			throw e;
		}
	}

	@RequestMapping(value = "/authorize/return", method = RequestMethod.GET)
	public String returnToApplication(HttpServletResponse response, ModelMap model)
			throws Exception {
		OAuthInfo oauthInfo = getModelVariable("oauthInfo", OAuthInfo.class, model);

		if (!nullCheck(oauthInfo)) {
			return error(response, "session expired");
		}

		String redirectUri = oauthInfo.getRedirectUri();
		StringBuffer sb = new StringBuffer(redirectUri);
		sb.append(redirectUri.contains("?") ? '&' : '?');
		sb.append("error=access_denied");

		if (oauthInfo.getState() != null) {
			sb.append("&state=");
			sb.append(URLEncoder.encode(oauthInfo.getState(), "UTF-8"));
		}

		return "redirect:" + sb.toString();
	}

	@ModelAttribute("returnUrl")
	public String getReturnUrl(ModelMap model) {
		OAuthInfo oauthInfo = getModelVariable("oauthInfo", OAuthInfo.class, model);

		return oauthInfo != null ? getReturnUrl(oauthInfo.getRedirectUri(), oauthInfo.getState())
				: "";
	}
}
