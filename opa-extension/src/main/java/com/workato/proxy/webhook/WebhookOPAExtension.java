package com.workato.proxy.webhook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.support.StandardServletEnvironment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class WebhookOPAExtension {

	private static final Logger logger = Logger.getLogger(WebhookOPAExtension.class.getName());
	
	// Store webhook subscriptions: connection_id -> webhook_url
	private final Map<String, String> webhookSubscriptions = new ConcurrentHashMap<>();
	
	private HttpServer httpServer;
	private String profileName;
	private Environment env;

	public WebhookOPAExtension(Environment env) {
		logger.info("Initializing Webhook OPA Extension...");
		this.env = env;
		
		if (env == null) {
			logger.warning("No environment properties available");
			this.env = new StandardServletEnvironment();
		}
		
		// Get profile name from environment
		Iterator<PropertySource<?>> it = ((StandardServletEnvironment) this.env).getPropertySources().iterator();
		while (it.hasNext()) {
			PropertySource<?> propertySource = it.next();
			if (propertySource.getClass().getName().equals("com.workato.agent.extension.EndpointPropertySource")) {
				String profileNameWithSuffix = propertySource.getName();
				this.profileName = profileNameWithSuffix.substring(0, profileNameWithSuffix.length() - 14);
				logger.info("Profile: " + this.profileName);
				break;
			}
		}
		
		if (this.profileName == null) {
			this.profileName = "";
			logger.warning("No profile name available. HTTP listener will not start.");
			return;
		}
		
		// Start HTTP listener
		Integer port = this.env.getProperty("listenPort", Integer.class, 8080);
		startHttpListener(port);
	}

	@RequestMapping(path = "/health", method = RequestMethod.GET, 
					produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> health() {
		JSONObject response = new JSONObject();
		try {
			response.put("status", "healthy");
			response.put("service", "workato-webhook-proxy");
		} catch (JSONException e) {
			logger.warning("Error creating health response: " + e.getMessage());
		}
		return ResponseEntity.ok(response.toString());
	}

	@RequestMapping(path = "/webhook/subscribe", method = RequestMethod.POST,
					consumes = MediaType.APPLICATION_JSON_VALUE,
					produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> webhookSubscribe(@RequestBody String requestBody) {
		logger.info("Webhook subscribe request received");
		
		JSONObject response = new JSONObject();
		try {
			JSONObject body = new JSONObject(requestBody);
			
			// Extract webhook URL from request
			String webhookUrl = body.optString("workato_webhook_url");
			String connectionId = body.optString("connection_id");
			
			logger.info("Subscribing webhook - connection_id: " + connectionId + 
					   ", webhook_url: " + webhookUrl);
			
			// Store the subscription
			webhookSubscriptions.put(connectionId, webhookUrl);
			
			response.put("success", true);
			response.put("message", "Webhook subscribed successfully");
			response.put("connection_id", connectionId);
			response.put("webhook_url", webhookUrl);
			
		} catch (Exception e) {
			logger.warning("Error subscribing webhook: " + e.getMessage());
			try {
				response.put("success", false);
				response.put("error", e.getMessage());
			} catch (JSONException ignored) {}
			return ResponseEntity.status(500).body(response.toString());
		}
		
		return ResponseEntity.ok(response.toString());
	}

	@RequestMapping(path = "/webhook/unsubscribe", method = RequestMethod.POST,
					consumes = MediaType.APPLICATION_JSON_VALUE,
					produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> webhookUnsubscribe(@RequestBody String requestBody) {
		logger.info("Webhook unsubscribe request received");
		
		JSONObject response = new JSONObject();
		try {
			JSONObject body = new JSONObject(requestBody);
			
			String connectionId = body.optString("connection_id");
			
			logger.info("Unsubscribing webhook - connection_id: " + connectionId);
			
			// Remove the subscription
			webhookSubscriptions.remove(connectionId);
			
			response.put("success", true);
			response.put("message", "Webhook unsubscribed successfully");
			response.put("connection_id", connectionId);
			
		} catch (Exception e) {
			logger.warning("Error unsubscribing webhook: " + e.getMessage());
			try {
				response.put("success", false);
				response.put("error", e.getMessage());
			} catch (JSONException ignored) {}
			return ResponseEntity.status(500).body(response.toString());
		}
		
		return ResponseEntity.ok(response.toString());
	}
	
	private void startHttpListener(int port) {
		try {
			httpServer = HttpServer.create(new InetSocketAddress(port), 0);
			httpServer.createContext("/", new HttpHandler() {
				@Override
				public void handle(HttpExchange exchange) throws IOException {
					handleIncomingRequest(exchange);
				}
			});
			httpServer.setExecutor(null); // Use default executor
			httpServer.start();
			logger.info("HTTP listener started on port " + port);
		} catch (IOException e) {
			logger.severe("Failed to start HTTP listener on port " + port + ": " + e.getMessage());
		}
	}
	
	private void handleIncomingRequest(HttpExchange exchange) throws IOException {
		logger.info("Received HTTP request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
		
		try {
			// Read request body
			String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
				.lines().collect(Collectors.joining("\n"));
			
			// Get connection_id from query parameter or path
			String path = exchange.getRequestURI().getPath();
			String query = exchange.getRequestURI().getQuery();
			String connectionId = extractConnectionId(path, query);
			
			if (connectionId == null || connectionId.isEmpty()) {
				sendResponse(exchange, 400, "{\"error\": \"connection_id is required in path or query\"}");
				return;
			}
			
			// Look up webhook URL for this connection
			String webhookUrl = webhookSubscriptions.get(connectionId);
			if (webhookUrl == null || webhookUrl.isEmpty()) {
				sendResponse(exchange, 404, "{\"error\": \"No webhook registered for connection_id: " + connectionId + "\"}");
				return;
			}
			
			// Extract headers from incoming request
			Map<String, String> headers = new HashMap<>();
			exchange.getRequestHeaders().forEach((key, values) -> {
				if (!values.isEmpty() && !"host".equalsIgnoreCase(key)) {
					headers.put(key, values.get(0));
				}
			});
			
			// Generate unique event ID for deduplication in Workato
			headers.put("X-Workato-Event-Id", UUID.randomUUID().toString());
			
			// Forward to Workato webhook
			String response = forwardToWebhook(webhookUrl, requestBody, headers);
			sendResponse(exchange, 200, response);
			
		} catch (Exception e) {
			logger.warning("Error handling incoming request: " + e.getMessage());
			sendResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
		}
	}
	
	private String extractConnectionId(String path, String query) {
		// Try to extract from path like /connection_id or /webhook/connection_id
		if (path != null && !path.equals("/")) {
			String[] parts = path.split("/");
			if (parts.length > 1) {
				return parts[parts.length - 1];
			}
		}
		
		// Try to extract from query like ?connection_id=xxx
		if (query != null && query.contains("connection_id=")) {
			String[] params = query.split("&");
			for (String param : params) {
				if (param.startsWith("connection_id=")) {
					return param.substring("connection_id=".length());
				}
			}
		}
		
		return null;
	}
	
	private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, body.getBytes().length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(body.getBytes());
		}
	}

	@RequestMapping(path = "/proxy", method = RequestMethod.POST,
					consumes = MediaType.APPLICATION_JSON_VALUE,
					produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> proxyPost(
			@RequestBody String requestBody,
			@RequestParam(required = false) String webhook_url,
			HttpServletRequest request) {
		
		logger.info("Received POST request to proxy");

		JSONObject response = new JSONObject();
		
		try {
			// Parse request body
			JSONObject body = new JSONObject(requestBody);

			// Get webhook URL from body or query parameter
			String targetUrl = webhook_url;
			if (targetUrl == null && body.has("webhook_url")) {
				targetUrl = body.getString("webhook_url");
			}

			if (targetUrl == null || targetUrl.isEmpty()) {
				logger.warning("Missing webhook_url parameter");
				response.put("success", false);
				response.put("error", "webhook_url is required");
				return ResponseEntity.badRequest().body(response.toString());
			}

			// Extract headers
			Map<String, String> headers = extractHeaders(request);

			// Add unique event ID for deduplication in Workato
			headers.put("X-Workato-Event-Id", UUID.randomUUID().toString());

			// Forward the request
			String forwardedResponse = forwardToWebhook(targetUrl, body.toString(), headers);
			
			response.put("success", true);
			response.put("response", forwardedResponse);
			
		} catch (Exception e) {
			logger.warning("Error proxying request: " + e.getMessage());
			try {
				response.put("success", false);
				response.put("error", e.getMessage());
			} catch (JSONException ignored) {}
			return ResponseEntity.status(500).body(response.toString());
		}

		return ResponseEntity.ok(response.toString());
	}

	@RequestMapping(path = "/proxy", method = RequestMethod.GET,
					produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> proxyGet(
			@RequestParam(required = false) String webhook_url,
			HttpServletRequest request) {
		
		logger.info("Received GET request to proxy");

		JSONObject response = new JSONObject();

		try {
			if (webhook_url == null || webhook_url.isEmpty()) {
				logger.warning("Missing webhook_url parameter");
				response.put("success", false);
				response.put("error", "webhook_url is required");
				return ResponseEntity.badRequest().body(response.toString());
			}

			// Extract headers
			Map<String, String> headers = extractHeaders(request);

			// Add unique event ID for deduplication in Workato
			headers.put("X-Workato-Event-Id", UUID.randomUUID().toString());

			// Build query params as body
			JSONObject body = new JSONObject();
			Enumeration<String> paramNames = request.getParameterNames();
			while (paramNames.hasMoreElements()) {
				String paramName = paramNames.nextElement();
				if (!"webhook_url".equals(paramName)) {
					body.put(paramName, request.getParameter(paramName));
				}
			}

			// Forward the request
			String forwardedResponse = forwardToWebhook(webhook_url, body.toString(), headers);
			
			response.put("success", true);
			response.put("response", forwardedResponse);
			
		} catch (Exception e) {
			logger.warning("Error proxying request: " + e.getMessage());
			try {
				response.put("success", false);
				response.put("error", e.getMessage());
			} catch (JSONException ignored) {}
			return ResponseEntity.status(500).body(response.toString());
		}

		return ResponseEntity.ok(response.toString());
	}

	private Map<String, String> extractHeaders(HttpServletRequest request) {
		Map<String, String> headers = new HashMap<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			// Skip host header to avoid conflicts
			if (!"host".equalsIgnoreCase(headerName)) {
				headers.put(headerName, request.getHeader(headerName));
			}
		}
		return headers;
	}

	private String forwardToWebhook(String webhookUrl, String body, Map<String, String> headers) throws Exception {
		logger.info("Forwarding request to webhook: " + webhookUrl);

		URL url = new URL(webhookUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		
		try {
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(30000);
			
			// Add forwarded headers
			for (Map.Entry<String, String> header : headers.entrySet()) {
				connection.setRequestProperty(header.getKey(), header.getValue());
			}
			
			connection.setDoOutput(true);
			
			// Send request body
			try (OutputStream os = connection.getOutputStream()) {
				byte[] input = body.getBytes("utf-8");
				os.write(input, 0, input.length);
			}
			
			// Read response
			int responseCode = connection.getResponseCode();
			logger.info("Webhook response code: " + responseCode);
			
			InputStream inputStream = responseCode < 400 ? 
				connection.getInputStream() : connection.getErrorStream();
			
			String responseBody = new BufferedReader(new InputStreamReader(inputStream))
				.lines().collect(Collectors.joining("\n"));
			
			if (responseCode >= 400) {
				throw new Exception("Webhook returned error: " + responseCode + " - " + responseBody);
			}
			
			return responseBody;
			
		} finally {
			connection.disconnect();
		}
	}
}
