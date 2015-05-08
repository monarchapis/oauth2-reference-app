package com.monarchapis.oauth.controller;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Optional;
import com.monarchapis.api.exception.ApiError;
import com.monarchapis.api.exception.ApiErrorException;
import com.monarchapis.api.v1.client.SecurityResource;
import com.monarchapis.api.v1.model.AuthorizationDetails;
import com.monarchapis.api.v1.model.AuthorizationRequest;
import com.monarchapis.api.v1.model.Extended;
import com.monarchapis.api.v1.model.TokenDetails;
import com.monarchapis.api.v1.model.TokenRequest;
import com.monarchapis.oauth.model.LoginResult;

@Controller
@SessionAttributes(value = { "oauthInfo", "authorizationDetails", "phase", "failedLogins", "username" })
public class OAuth2PasswordController extends BaseAuthController {
	private static final Logger logger = LoggerFactory.getLogger(OAuth2PasswordController.class);

	private static final String AUTH_SCHEME = "oauth2-password";

	/* SERVICE CALLS -------------------------------------------------------- */

	@RequestMapping(value = "/token", params = "grant_type=password", method = RequestMethod.POST)
	public ResponseEntity<byte[]> createAccessToken(@RequestHeader(value = "Authorization") final String authorization,
			@RequestParam(value = "username") final String username,
			@RequestParam(value = "password") final String password,
			@RequestParam(value = "scope", required = false) String scope, HttpServletResponse response, Model model)
			throws Exception {
		try {
			String apiKey = null;
			String sharedSecret = null;

			if (authorization != null && authorization.startsWith("Basic ")) {
				String base64Value = authorization.substring(6);

				String[] unpw = StringUtils.split(new String(Base64.decodeBase64(base64Value), "UTF-8"), ':');

				if (unpw.length != 2) {
					return error("invalid_credentials", HttpStatus.UNAUTHORIZED);
				}

				apiKey = unpw[0];
				sharedSecret = unpw[1];
			} else {
				return error("invalid_credentials", HttpStatus.UNAUTHORIZED);
			}

			ResponseEntity<byte[]> re = authenticateClient(AUTH_SCHEME, apiKey, sharedSecret);
			if (re != null) {
				return re;
			}

			Set<String> permissions = scopeToPermissions(scope);

			SecurityResource securityResource = serviceApi.getSecurityResource();

			AuthorizationRequest ar = new AuthorizationRequest();
			ar.setAuthorizationScheme(AUTH_SCHEME);
			ar.setApiKey(apiKey);
			ar.setPermissions(permissions);

			logger.debug("Fetching authorization details");
			AuthorizationDetails authorizationDetails = securityResource.getAuthorizationDetails(ar);

			LoginResult loginResult = authenticationService.authenticate(username, password);

			if (loginResult == LoginResult.SUCCESS) {
				Map<String, Object> extended = getLoginExtendedData(username, password, authorizationDetails);

				TokenRequest tokenRequest = new TokenRequest();
				tokenRequest.setAuthorizationScheme(AUTH_SCHEME);
				tokenRequest.setApiKey(apiKey);
				tokenRequest.setGrantType("password");
				tokenRequest.setPermissions(permissions);
				tokenRequest.setUri(null);
				tokenRequest.setUserId(Optional.of(username));
				tokenRequest.setUserContext(Optional.of(username));
				Extended ext = new Extended();
				ext.putAll(extended);
				tokenRequest.setExtended(ext);
				tokenRequest.setTokenType("bearer");
				// Omitting state from the bearer token

				logger.debug("Creating access token: {}", tokenRequest);
				TokenDetails accessToken = securityResource.createToken(tokenRequest);
				// serviceApi.revokeToken(apiKey, code, null);

				if (accessToken != null) {
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
				} else {
					logger.debug("The access token was not created");

					return error("invalid_grant");
				}
			} else if (loginResult != LoginResult.SYSTEM_UNAVAILABLE) {
				if (loginResult == LoginResult.LOCKED) {
					logger.debug("The account is locked");
				}

				return error("invalid_request", HttpStatus.UNAUTHORIZED);
			} else {
				logger.debug("The login failure due to system unavailability");

				return error("server_error", HttpStatus.SERVICE_UNAVAILABLE);
			}
		} catch (ApiErrorException apie) {
			ApiError error = apie.getError();
			logger.debug("The access token was not created: {}", error);
			return error("invalid_grant"); // unauthorized_client???
		} catch (Exception e) {
			logger.error("Could not create access token", e);
			return internalError();
		}
	}
}
