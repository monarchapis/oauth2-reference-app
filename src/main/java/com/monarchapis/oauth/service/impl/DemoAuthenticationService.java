package com.monarchapis.oauth.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.inject.Named;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import com.monarchapis.oauth.model.LoginResult;
import com.monarchapis.oauth.service.AuthenticationService;

@Named
public class DemoAuthenticationService implements AuthenticationService {
	private static Logger logger = LoggerFactory
			.getLogger(DemoAuthenticationService.class);

	private Map<String, String> directory = Collections.emptyMap();

	public void setDirectory(Map<String, String> directory) {
		this.directory = directory;
	}

	public void setDirectoryFromResource(Resource resource) throws IOException {
		Properties props = new Properties();
		InputStream is = null;

		try {
			is = resource.getInputStream();
			props.load(is);
			Map<String, String> directory = new LinkedHashMap<String, String>();

			for (Entry<Object, Object> entry : props.entrySet()) {
				directory.put(entry.getKey().toString().trim(), entry
						.getValue().toString().trim());
			}

			for (Entry<String, String> entry : this.directory.entrySet()) {
				logger.info("Demo user: un={} pw={}", entry.getKey(),
						entry.getValue());
			}

			setDirectory(directory);
		} catch (Exception e) {
			logger.error("Error reading demo OAuth users", e);
			throw new IOException("Error reading demo OAuth users", e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	@Override
	public LoginResult authenticate(String username, String password) {
		String pw = directory.get(username);

		if (pw != null && pw.equals(password)) {
			return LoginResult.SUCCESS;
		}

		return LoginResult.FAILURE;
	}
}
