package com.monarchapis.oauth.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monarchapis.oauth.model.TypeReference;

public class BaseController {
	private static final Logger logger = LoggerFactory.getLogger(BaseController.class);

	protected static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

	protected static ResponseEntity<byte[]> json(ByteArrayOutputStream baos) {
		return json(baos, HttpStatus.OK);
	}

	protected static ResponseEntity<byte[]> json(ByteArrayOutputStream baos, HttpStatus httpStatus) {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("Content-Type", CONTENT_TYPE_JSON);
		return new ResponseEntity<byte[]>(baos.toByteArray(), responseHeaders, httpStatus);
	}

	protected ResponseEntity<byte[]> json(Object object) throws IOException,
			JsonGenerationException, JsonProcessingException {
		return json(object, HttpStatus.OK);
	}

	protected ResponseEntity<byte[]> json(Object object, HttpStatus httpStatus) throws IOException,
			JsonGenerationException, JsonProcessingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectMapper mapper = new ObjectMapper();
		mapper.writeValue(baos, object);
		baos.flush();

		return json(baos, httpStatus);
	}

	protected ResponseEntity<byte[]> error(String message) throws IOException,
			JsonGenerationException, JsonProcessingException {
		return error(message, HttpStatus.BAD_REQUEST);
	}

	protected ResponseEntity<byte[]> error(String message, HttpStatus httpStatus)
			throws IOException, JsonGenerationException, JsonProcessingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator writer = getStreamWriter(baos);

		writer.writeStartObject();
		writer.writeObjectField("error", message);
		writer.writeEndObject();
		writer.flush();

		return json(baos, httpStatus);
	}

	protected String error(HttpServletResponse response, String message) throws IOException {
		return error(response, message, HttpStatus.BAD_REQUEST);
	}

	protected String error(HttpServletResponse response, String message, HttpStatus httpStatus)
			throws IOException {
		response.setStatus(httpStatus.value());
		JsonGenerator writer = createStreamWriter(response);

		writer.writeStartObject();
		writer.writeObjectField("error", message);
		writer.writeEndObject();
		writer.flush();

		return null;
	}

	protected ResponseEntity<byte[]> internalError() throws IOException, JsonGenerationException,
			JsonProcessingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator writer = getStreamWriter(baos);

		writer.writeStartObject();
		writer.writeObjectField("error",
				"An error occured that prevented your request from being processed");
		writer.writeEndObject();
		writer.flush();

		return json(baos, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	protected String handleInternalError(HttpServletResponse response, Throwable e) {
		logger.error("Error", e);

		try {
			return error(response,
					"The system is not currently available.  Please try again later.",
					HttpStatus.INTERNAL_SERVER_ERROR);
		} catch (Exception ex) {
			return null;
		}
	}

	protected ResponseEntity<byte[]> success(String message) throws IOException,
			JsonGenerationException, JsonProcessingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator writer = getStreamWriter(baos);

		writer.writeStartObject();
		writer.writeObjectField("message", message);
		writer.writeEndObject();
		writer.flush();

		return json(baos, HttpStatus.OK);
	}

	protected ResponseEntity<byte[]> notFound(String message) throws IOException,
			JsonGenerationException, JsonProcessingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator writer = getStreamWriter(baos);

		writer.writeStartObject();
		writer.writeObjectField("error", message);
		writer.writeEndObject();
		writer.flush();

		return json(baos, HttpStatus.NOT_FOUND);
	}

	protected JsonGenerator createStreamWriter(HttpServletResponse response) throws IOException {
		response.setContentType(CONTENT_TYPE_JSON);
		response.setCharacterEncoding("UTF-8");
		return getStreamWriter(response.getOutputStream());
	}

	protected static JsonGenerator getStreamWriter(OutputStream output) {
		try {
			JsonFactory jsonFactory = new JsonFactory();
			JsonGenerator jsonGenerator = jsonFactory.createGenerator(output, JsonEncoding.UTF8);
			jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

			return jsonGenerator;
		} catch (IOException e) {
			throw new RuntimeException("Could not create JsonGenerator", e);
		}
	}

	protected static void setSessionVariable(final String key, final Object value,
			DefaultSessionAttributeStore status, WebRequest request, ModelMap model) {
		if (value != null) {
			model.put(key, value);
		} else {
			model.remove(key);
			status.cleanupAttribute(request, key);
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> T getModelVariable(String key, Class<T> clazz, ModelMap model) {
		try {
			return (T) model.get(key);
		} catch (ClassCastException cce) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	protected <T> T getModelVariable(String key, TypeReference<T> clazz, ModelMap model) {
		try {
			return (T) model.get(key);
		} catch (ClassCastException cce) {
			return null;
		}
	}

	protected static boolean nullCheck(Object... args) {
		for (Object object : args) {
			if (object == null) {
				return false;
			}
		}

		return true;
	}
}
