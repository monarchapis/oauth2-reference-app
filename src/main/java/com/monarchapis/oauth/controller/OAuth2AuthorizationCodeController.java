package com.monarchapis.oauth.controller;

import java.io.ByteArrayOutputStream;

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
import com.monarchapis.api.exception.ApiErrorException;
import com.monarchapis.api.v1.client.SecurityResource;
import com.monarchapis.api.v1.model.TokenDetails;
import com.monarchapis.api.v1.model.TokenRequest;

@Controller
@SessionAttributes(value = { "oauthInfo", "authorizationDetails", "phase", "failedLogins", "username" })
public class OAuth2AuthorizationCodeController extends BaseAuthController {
	private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthorizationCodeController.class);

	private static final String AUTH_SCHEME = "oauth2-authorization-code";
	
	protected String getAuthorizationScheme() {
		return AUTH_SCHEME;
	}
	
	protected String getOAuthResponseType() {
		return "code";
	}

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
				String[] unpw = StringUtils.split(new String(Base64.decodeBase64(authorization), "UTF-8"), ':');

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

			SecurityResource securityResource = serviceApi.getSecurityResource();

			ResponseEntity<byte[]> re = authenticateClient(AUTH_SCHEME, apiKey, sharedSecret);
			if (re != null) {
				return re;
			}

			logger.debug("Fetching authorization code {}", code);
			TokenDetails authorizationCode = securityResource.loadToken(apiKey, code, null, redirectUri);

			if (!"authorization_code".equals(authorizationCode.getGrantType())
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
				logger.debug("Invalid redirect URI {}, expected {}", redirectUri, authorizationCode.getUri());
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
			TokenDetails accessToken = securityResource.createToken(tokenRequest);
			securityResource.revokeToken(apiKey, code, redirectUri);

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
		} catch (ApiErrorException are) {
			logger.debug("The access token was not created");
			return error("invalid_grant");
		} catch (Exception e) {
			logger.error("Could not create access token", e);
			return internalError();
		}
	}

	/* AUTHORIZATION UI ----------------------------------------------------- */

	@RequestMapping(value = "/authorize", params = "response_type=code", method = RequestMethod.GET)
	public String renderUI(@RequestHeader(value = "referer", required = false) final String refererUrl,
			@RequestParam(value = "client_id") final String apiKey,
			@RequestParam(value = "redirect_uri", required = false) final String redirectUri,
			@RequestParam(value = "scope", required = false) String scope,
			@RequestParam(value = "state", required = false) final String state, DefaultSessionAttributeStore status,
			WebRequest request, ModelMap model, HttpServletResponse response) throws Exception {
		return doAuthorize(apiKey, redirectUri, scope, state, refererUrl, status, request, model);
	}
}
