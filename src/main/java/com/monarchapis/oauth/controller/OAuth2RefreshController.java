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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonGenerator;
import com.monarchapis.api.exception.ApiError;
import com.monarchapis.api.exception.ApiErrorException;
import com.monarchapis.api.v1.client.SecurityResource;
import com.monarchapis.api.v1.model.TokenDetails;
import com.monarchapis.api.v1.model.TokenRequest;

@Controller
public class OAuth2RefreshController extends BaseAuthController {
	private static final Logger logger = LoggerFactory.getLogger(OAuth2RefreshController.class);

	private static final String AUTH_SCHEME = "oauth2-authorization-code";

	/* SERVICE CALLS -------------------------------------------------------- */

	@RequestMapping(value = "/token", params = "grant_type=refresh_token", method = RequestMethod.POST)
	public ResponseEntity<byte[]> createAccessToken(@RequestHeader(value = "Authorization") final String authorization,
			@RequestParam(value = "refresh_token") final String refreshToken,
			@RequestParam(value = "scope", required = false) final String redirectUri, HttpServletResponse response,
			Model model) throws Exception {
		try {
			if (authorization == null) {
				return error("access_denied");
			}

			String[] unpw = StringUtils.split(new String(Base64.decodeBase64(authorization), "UTF-8"), ':');

			if (unpw.length != 2) {
				return error("access_denied");
			}

			String apiKey = unpw[0];
			String sharedSecret = unpw[1];

			SecurityResource securityResource = serviceApi.getSecurityResource();

			ResponseEntity<byte[]> re = authenticateClient(AUTH_SCHEME, apiKey, sharedSecret);
			if (re != null) {
				return re;
			}

			TokenDetails currentToken = securityResource.loadToken(apiKey, null, refreshToken, redirectUri);

			if (!"authorization_code".equals(currentToken.getGrantType())
					|| !"bearer".equals(currentToken.getTokenType())) {
				logger.debug("Access token {} not found for API Key {}", refreshToken, apiKey);
				return error("access_denied");
			}

			// TODO check scope

			logger.debug("Revoking current token: {}", currentToken);
			securityResource.revokeToken(apiKey, currentToken.getToken(), redirectUri);

			TokenRequest tokenRequest = new TokenRequest();
			tokenRequest.setAuthorizationScheme(AUTH_SCHEME);
			tokenRequest.setApiKey(apiKey);
			tokenRequest.setGrantType(currentToken.getGrantType());
			tokenRequest.setPermissions(currentToken.getPermissions());
			tokenRequest.setUserId(currentToken.getUserId());
			tokenRequest.setUserContext(currentToken.getUserContext());
			tokenRequest.setExtended(currentToken.getExtended());
			tokenRequest.setTokenType("bearer");
			// Omitting state from the bearer token

			logger.debug("Creating access token: {}", tokenRequest);
			TokenDetails accessToken = securityResource.createToken(tokenRequest);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JsonGenerator writer = getStreamWriter(baos);

			writer.writeStartObject();
			writer.writeObjectField("access_token", accessToken.getToken());
			writer.writeObjectField("token_type", accessToken.getTokenType());
			writer.writeObjectField("expires_in", accessToken.getExpiresIn().orNull());
			writer.writeObjectField("refresh_token", accessToken.getRefreshToken().orNull());
			writer.writeEndObject();
			writer.flush();

			response.setHeader("Cache-Control", "no-store");
			response.setHeader("Pragma", "no-cache");

			return json(baos);
		} catch (ApiErrorException apie) {
			ApiError error = apie.getError();
			logger.debug("The access token was not created: {}", error);
			return error("invalid_grant");
		} catch (Exception e) {
			logger.error("Could not create access token", e);
			return internalError();
		}
	}
}
