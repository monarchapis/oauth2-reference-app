package com.monarchapis.oauth.controller;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.monarchapis.driver.exception.ApiErrorException;
import com.monarchapis.driver.model.AuthorizationDetails;
import com.monarchapis.driver.model.Token;
import com.monarchapis.driver.model.TokenRequest;
import com.monarchapis.oauth.model.OAuthInfo;

@Controller
@SessionAttributes(value = { "oauthInfo", "authorizationDetails", "phase", "failedLogins",
		"username" })
public class OAuth2AuthorizationCodeController extends BaseAuthController {
	private static final Logger logger = LoggerFactory
			.getLogger(OAuth2AuthorizationCodeController.class);

	private static final String AUTH_SCHEME = "oauth2-authorization-code";

	/* SERVICE CALLS -------------------------------------------------------- */

	@RequestMapping(value = "/token", params = "grant_type=authorization_code", method = RequestMethod.POST)
	public ResponseEntity<byte[]> createAccessToken(
			@RequestHeader(value = "Authorization", required = false) final String authorization,
			@RequestParam(value = "client_id", required = false) final String clientId,
			@RequestParam(value = "code") final String code,
			@RequestParam(value = "redirect_uri", required = false) final String redirectUri,
			HttpServletResponse response, Model model) throws Exception {
		try {
			String apiKey = null;
			String sharedSecret = null;

			if (authorization != null) {
				String[] unpw = StringUtils.split(new String(Base64.decodeBase64(authorization),
						"UTF-8"), ':');

				if (unpw.length != 2) {
					return error("access_denied");
				}

				apiKey = unpw[0];
				sharedSecret = unpw[1];
			} else {
				apiKey = clientId;
			}

			if (apiKey == null) {
				return error("access_denied");
			}

			if (!serviceApi.authenticateClient(AUTH_SCHEME, apiKey, sharedSecret)) {
				return error("access_denied");
			}

			Token authorizationCode = serviceApi.getToken(apiKey, code, redirectUri);

			if (authorizationCode == null
					|| !"authorization_code".equals(authorizationCode.getGrantType())
					|| !"authorization".equals(authorizationCode.getTokenType())) {
				logger.debug("Authorization code {} not found for API Key {}", code, apiKey);
				return error("access_denied");
			}

			/*
			 * Ensure that the "redirect_uri" parameter is present if the
			 * "redirect_uri" parameter was included in the initial
			 * authorization request as described in Section 4.1.1, and if
			 * included ensure their values are identical.
			 */
			if (redirectUri != null && !redirectUri.equals(authorizationCode.getUri())) {
				logger.debug("Invalid redirect URI {}, expected {}", redirectUri,
						authorizationCode.getUri());
				return error("access_denied");
			}

			TokenRequest tokenRequest = new TokenRequest();
			tokenRequest.setAuthorizationScheme(AUTH_SCHEME);
			tokenRequest.setApiKey(apiKey);
			tokenRequest.setGrantType(authorizationCode.getGrantType());
			tokenRequest.setPermissions(authorizationCode.getPermissions());
			tokenRequest.setUri(redirectUri);
			tokenRequest.setUserId(authorizationCode.getUserId());
			tokenRequest.setUserContext(authorizationCode.getUserContext());
			tokenRequest.setExtended(authorizationCode.getExtended());
			tokenRequest.setTokenType("bearer");
			// Omitting state from the bearer token

			logger.debug("Creating access token: {}", tokenRequest);
			Token accessToken = serviceApi.createToken(tokenRequest);
			serviceApi.revokeToken(apiKey, code, redirectUri);

			if (accessToken != null) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				JsonGenerator writer = getStreamWriter(baos);

				writer.writeStartObject();
				writer.writeObjectField("access_token", accessToken.getToken());
				writer.writeObjectField("token_type", accessToken.getTokenType());
				writer.writeObjectField("expires_in", accessToken.getExpiresIn());
				writer.writeObjectField("refresh_token", accessToken.getRefreshToken());
				writer.writeEndObject();
				writer.flush();

				response.setHeader("Cache-Control", "no-store");
				response.setHeader("Pragma", "no-cache");

				return json(baos);
			} else {
				logger.debug("The access token was not created");
				return error("invalid_grant");
			}
		} catch (Throwable e) {
			logger.error("Could not create access token", e);
			return internalError();
		}
	}

	/* AUTHORIZATION UI ----------------------------------------------------- */

	@RequestMapping(value = "/authorize", params = "response_type=code", method = RequestMethod.GET)
	public String renderUI(
			@RequestHeader(value = "referer", required = false) final String refererUrl,
			@RequestParam(value = "client_id") final String apiKey,
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

			OAuthInfo oauthInfo = new OAuthInfo();
			oauthInfo.setResponseType("code");
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
