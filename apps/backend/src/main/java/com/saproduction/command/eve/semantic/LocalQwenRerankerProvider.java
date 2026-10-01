package com.saproduction.command.eve.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saproduction.command.shared.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local native cross-encoder reranker powered by Qwen3-Reranker-0.6B-Q8_0.gguf and llama-server.
 *
 * Operational Invariants:
 * - 100% offline, localhost-only execution.
 * - Single-flight inference governed by Semaphore(1).
 * - Maximum top-K candidate bounds (<= 10) to eliminate latency spikes.
 * - Native process lifecycle governed via Spring @PostConstruct and @PreDestroy.
 */
@Component
@ConditionalOnProperty(name = "app.eve.semantic.provider", havingValue = "LOCAL")
public class LocalQwenRerankerProvider implements EveRerankerProvider {

  private static final Logger log = LoggerFactory.getLogger(LocalQwenRerankerProvider.class);

  private final String configuredModelPath;
  private final String configuredBinaryPath;
  private final String serverUrl;
  private final int port;
  private final int threads;
  private final int ctxSize;
  private final int gpuLayers;
  private final int timeoutMs;

  private final HttpClient httpClient;
  private final ObjectMapper json;
  private final Semaphore inferenceSemaphore = new Semaphore(1);

  private volatile Process managedProcess;
  private volatile String status = "INITIALIZING";
  private volatile String statusMessage = "Initializing local reranker runtime...";

  public LocalQwenRerankerProvider(
      @Value("${app.eve.semantic.reranker.model-path:eve/models/Qwen3-Reranker-0.6B-Q8_0.gguf}") String modelPath,
      @Value("${app.eve.semantic.reranker.binary-path:eve/runtime/llama-server/llama-server.exe}") String binaryPath,
      @Value("${app.eve.semantic.reranker.server-url:http://127.0.0.1:8088}") String serverUrl,
      @Value("${app.eve.semantic.reranker.port:8088}") int port,
      @Value("${app.eve.semantic.reranker.threads:4}") int threads,
      @Value("${app.eve.semantic.reranker.ctx-size:2048}") int ctxSize,
      @Value("${app.eve.semantic.reranker.gpu-layers:0}") int gpuLayers,
      @Value("${app.eve.semantic.reranker.timeout-ms:4000}") int timeoutMs) {
    this.configuredModelPath = modelPath;
    this.configuredBinaryPath = binaryPath;
    this.serverUrl = serverUrl;
    this.port = port;
    this.threads = threads;
    this.ctxSize = ctxSize;
    this.gpuLayers = gpuLayers;
    this.timeoutMs = timeoutMs;

    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    this.json = new ObjectMapper();
  }

  @PostConstruct
  public synchronized void init() {
    log.info("Initializing LocalQwenRerankerProvider for model: {}", configuredModelPath);

    File modelFile = resolveFile(configuredModelPath);
    if (!modelFile.exists() || !modelFile.isFile()) {
      status = "UNAVAILABLE";
      statusMessage = "Reranker model file not found at: " + modelFile.getAbsolutePath();
      log.warn(statusMessage);
      return;
    }

    // Check if server is already running and healthy at serverUrl
    if (isServerHealthy()) {
      status = "READY";
      statusMessage = "Connected to existing local reranker server at " + serverUrl;
      log.info(statusMessage);
      return;
    }

    File binaryFile = resolveFile(configuredBinaryPath);
    if (!binaryFile.exists() || !binaryFile.isFile()) {
      status = "UNAVAILABLE";
      statusMessage = "Inference binary not found at: " + binaryFile.getAbsolutePath();
      log.warn(statusMessage);
      return;
    }

    try {
      ProcessBuilder pb = new ProcessBuilder(
          binaryFile.getAbsolutePath(),
          "-m", modelFile.getAbsolutePath(),
          "--port", String.valueOf(port),
          "--host", "127.0.0.1",
          "--pooling", "rank",
          "--rerank",
          "-c", String.valueOf(ctxSize),
          "-t", String.valueOf(threads),
          "-ngl", String.valueOf(gpuLayers),
          "--log-disable"
      );
      pb.directory(binaryFile.getParentFile());
      pb.redirectErrorStream(true);

      log.info("Starting local reranker llama-server on port {} with {} threads...", port, threads);
      managedProcess = pb.start();

      // Poll /health endpoint up to 20 seconds
      boolean healthy = false;
      for (int i = 0; i < 40; i++) {
        Thread.sleep(500);
        if (!managedProcess.isAlive()) {
          int exitCode = managedProcess.exitValue();
          status = "ERROR";
          statusMessage = "Local reranker server exited prematurely with code " + exitCode;
          log.error(statusMessage);
          return;
        }
        if (isServerHealthy()) {
          healthy = true;
          break;
        }
      }

      if (healthy) {
        status = "READY";
        statusMessage = String.format("Reranker model loaded successfully (PID: %d)", managedProcess.pid());
        log.info(statusMessage);
      } else {
        status = "ERROR";
        statusMessage = "Local reranker server failed to become healthy within 20 seconds";
        log.error(statusMessage);
      }
    } catch (Exception e) {
      status = "ERROR";
      statusMessage = "Failed to start local reranker server: " + e.getMessage();
      log.error("Exception starting local reranker server", e);
    }
  }

  @PreDestroy
  public synchronized void destroy() {
    if (managedProcess != null && managedProcess.isAlive()) {
      log.info("Terminating local reranker process (PID: {})...", managedProcess.pid());
      managedProcess.destroyForcibly();
      try {
        managedProcess.waitFor(3, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public boolean isServerHealthy() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(serverUrl + "/health"))
          .timeout(Duration.ofSeconds(2))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public List<RerankResult> rerank(String query, List<String> documents, String instruction, int topK) {
    if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
      return List.of();
    }
    if (!isAvailable()) {
      throw ApiException.internal("EVE_RERANKER_UNAVAILABLE", "Local reranker model is unavailable: " + statusMessage);
    }

    boolean acquired = false;
    try {
      acquired = inferenceSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw ApiException.internal("EVE_RERANKER_BUSY", "Local reranker inference queue saturated.");
      }

      String formattedQuery = "<Instruct>: "
          + (instruction != null && !instruction.isBlank() ? instruction : "Given a search query, retrieve relevant entity records that answer the query.")
          + "\n<Query>: " + query;

      ObjectNode payload = json.createObjectNode();
      payload.put("query", formattedQuery);
      ArrayNode docArr = payload.putArray("documents");
      for (String doc : documents) {
        docArr.add(doc != null ? doc : "");
      }
      payload.put("top_n", topK > 0 ? topK : documents.size());

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(serverUrl + "/v1/rerank"))
          .timeout(Duration.ofMillis(timeoutMs))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        // Fallback: try /rerank endpoint
        return callLegacyRerankEndpoint(formattedQuery, documents, topK);
      }

      JsonNode root = json.readTree(response.body());
      JsonNode resultsArr = root.get("results");
      if (resultsArr == null || !resultsArr.isArray()) {
        throw ApiException.internal("EVE_RERANKER_INVALID_RESPONSE", "Missing 'results' array in reranker response.");
      }

      List<RerankResult> results = new ArrayList<>(resultsArr.size());
      for (JsonNode item : resultsArr) {
        int index = item.get("index").asInt();
        double score = item.has("relevance_score") ? item.get("relevance_score").asDouble() : item.get("score").asDouble();
        if (Double.isNaN(score) || Double.isInfinite(score)) {
          score = 0.0;
        }
        String docText = index >= 0 && index < documents.size() ? documents.get(index) : "";
        results.add(new RerankResult(index, score, docText));
      }
      return results;

    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("Reranker request failed on localhost server", e);
      throw ApiException.internal("EVE_RERANKER_FAILED", "Reranker inference failed: " + e.getMessage());
    } finally {
      if (acquired) {
        inferenceSemaphore.release();
      }
    }
  }

  private List<RerankResult> callLegacyRerankEndpoint(String query, List<String> documents, int topK) {
    try {
      ObjectNode payload = json.createObjectNode();
      payload.put("query", query);
      ArrayNode docArr = payload.putArray("documents");
      for (String doc : documents) {
        docArr.add(doc != null ? doc : "");
      }
      payload.put("top_n", topK > 0 ? topK : documents.size());

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(serverUrl + "/rerank"))
          .timeout(Duration.ofMillis(timeoutMs))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw ApiException.internal("EVE_RERANKER_FAILED", "Reranker returned status " + response.statusCode());
      }
      JsonNode root = json.readTree(response.body());
      JsonNode resultsArr = root.get("results");
      List<RerankResult> results = new ArrayList<>();
      if (resultsArr != null && resultsArr.isArray()) {
        for (JsonNode item : resultsArr) {
          int index = item.get("index").asInt();
          double score = item.has("relevance_score") ? item.get("relevance_score").asDouble() : item.get("score").asDouble();
          String docText = index >= 0 && index < documents.size() ? documents.get(index) : "";
          results.add(new RerankResult(index, score, docText));
        }
      }
      return results;
    } catch (Exception e) {
      throw ApiException.internal("EVE_RERANKER_FAILED", "Legacy rerank call failed: " + e.getMessage());
    }
  }

  @Override
  public boolean isAvailable() {
    return "READY".equals(status);
  }

  @Override
  public String getModelName() {
    return "Qwen3-Reranker-0.6B-Q8_0";
  }

  private File resolveFile(String configuredPath) {
    Path p = Paths.get(configuredPath);
    if (p.isAbsolute()) {
      return p.toFile();
    }
    File f = new File(configuredPath);
    if (f.exists()) {
      return f.getAbsoluteFile();
    }
    File rootF = new File("../../" + configuredPath);
    if (rootF.exists()) {
      return rootF.getAbsoluteFile();
    }
    return f.getAbsoluteFile();
  }
}
