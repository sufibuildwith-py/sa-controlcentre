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
 * Local native embedding provider powered by Qwen3-Embedding-0.6B-Q8_0.gguf and llama-server.
 *
 * Operational Invariants:
 * - 100% local, zero cloud inference, zero external HTTP calls.
 * - Single-flight inference governed by Semaphore(1) to protect memory/CPU bounds.
 * - Native process lifecycle managed via Spring @PostConstruct and @PreDestroy.
 * - Strict timeout boundaries on all HTTP calls to localhost inference server.
 */
@Component
@ConditionalOnProperty(name = "app.eve.semantic.provider", havingValue = "LOCAL")
public class LocalQwenEmbeddingProvider implements EveEmbeddingProvider {

  private static final Logger log = LoggerFactory.getLogger(LocalQwenEmbeddingProvider.class);

  private final String configuredModelPath;
  private final String configuredBinaryPath;
  private final String serverUrl;
  private final int port;
  private final int threads;
  private final int ctxSize;
  private final int gpuLayers;
  private final int dimensions;
  private final int timeoutMs;

  private final HttpClient httpClient;
  private final ObjectMapper json;
  private final Semaphore inferenceSemaphore = new Semaphore(1);

  private volatile Process managedProcess;
  private volatile String status = "INITIALIZING";
  private volatile String statusMessage = "Initializing local embedding runtime...";

  public LocalQwenEmbeddingProvider(
      @Value("${app.eve.semantic.embedding.model-path:eve/models/Qwen3-Embedding-0.6B-Q8_0.gguf}") String modelPath,
      @Value("${app.eve.semantic.embedding.binary-path:eve/runtime/llama-server/llama-server.exe}") String binaryPath,
      @Value("${app.eve.semantic.embedding.server-url:http://127.0.0.1:8087}") String serverUrl,
      @Value("${app.eve.semantic.embedding.port:8087}") int port,
      @Value("${app.eve.semantic.embedding.threads:4}") int threads,
      @Value("${app.eve.semantic.embedding.ctx-size:2048}") int ctxSize,
      @Value("${app.eve.semantic.embedding.gpu-layers:0}") int gpuLayers,
      @Value("${app.eve.semantic.embedding.dimensions:1024}") int dimensions,
      @Value("${app.eve.semantic.embedding.timeout-ms:4000}") int timeoutMs) {
    this.configuredModelPath = modelPath;
    this.configuredBinaryPath = binaryPath;
    this.serverUrl = serverUrl;
    this.port = port;
    this.threads = threads;
    this.ctxSize = ctxSize;
    this.gpuLayers = gpuLayers;
    this.dimensions = dimensions;
    this.timeoutMs = timeoutMs;

    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    this.json = new ObjectMapper();
  }

  @PostConstruct
  public synchronized void init() {
    log.info("Initializing LocalQwenEmbeddingProvider for model: {}", configuredModelPath);

    File modelFile = resolveFile(configuredModelPath);
    if (!modelFile.exists() || !modelFile.isFile()) {
      status = "UNAVAILABLE";
      statusMessage = "Embedding model file not found at: " + modelFile.getAbsolutePath();
      log.warn(statusMessage);
      return;
    }

    // Check if server is already running and healthy at serverUrl
    if (isServerHealthy()) {
      status = "READY";
      statusMessage = "Connected to existing local embedding server at " + serverUrl;
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
          "--embedding",
          "-c", String.valueOf(ctxSize),
          "-t", String.valueOf(threads),
          "-ngl", String.valueOf(gpuLayers),
          "--log-disable"
      );
      pb.directory(binaryFile.getParentFile());
      pb.redirectErrorStream(true);

      log.info("Starting local embedding llama-server on port {} with {} threads...", port, threads);
      managedProcess = pb.start();

      // Poll /health endpoint up to 20 seconds
      boolean healthy = false;
      for (int i = 0; i < 40; i++) {
        Thread.sleep(500);
        if (!managedProcess.isAlive()) {
          int exitCode = managedProcess.exitValue();
          status = "ERROR";
          statusMessage = "Local embedding server exited prematurely with code " + exitCode;
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
        statusMessage = String.format("Embedding model loaded successfully (PID: %d)", managedProcess.pid());
        log.info(statusMessage);
      } else {
        status = "ERROR";
        statusMessage = "Local embedding server failed to become healthy within 20 seconds";
        log.error(statusMessage);
      }
    } catch (Exception e) {
      status = "ERROR";
      statusMessage = "Failed to start local embedding server: " + e.getMessage();
      log.error("Exception starting local embedding server", e);
    }
  }

  @PreDestroy
  public synchronized void destroy() {
    if (managedProcess != null && managedProcess.isAlive()) {
      log.info("Terminating local embedding process (PID: {})...", managedProcess.pid());
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
  public float[] embedQuery(String query, String instruction) {
    if (query == null || query.isBlank()) {
      return new float[dimensions];
    }
    // Qwen3-Embedding recommended prompt formatting:
    // Instruct: <instruction>\nQuery: <query>
    String formattedQuery = (instruction != null && !instruction.isBlank())
        ? "Instruct: " + instruction + "\nQuery: " + query
        : query;

    List<float[]> res = embedBatch(List.of(formattedQuery));
    return res.isEmpty() ? new float[dimensions] : res.get(0);
  }

  @Override
  public float[] embedCandidate(String text) {
    if (text == null || text.isBlank()) {
      return new float[dimensions];
    }
    List<float[]> res = embedBatch(List.of(text));
    return res.isEmpty() ? new float[dimensions] : res.get(0);
  }

  @Override
  public List<float[]> embedBatch(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    if (!isAvailable()) {
      throw ApiException.internal("EVE_EMBEDDING_UNAVAILABLE", "Local embedding model is unavailable: " + statusMessage);
    }

    boolean acquired = false;
    try {
      acquired = inferenceSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw ApiException.internal("EVE_EMBEDDING_BUSY", "Local embedding inference queue saturated.");
      }

      ObjectNode payload = json.createObjectNode();
      ArrayNode inputArr = payload.putArray("input");
      for (String t : texts) {
        inputArr.add(t != null ? t : "");
      }
      payload.put("model", "Qwen3-Embedding-0.6B");

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(serverUrl + "/v1/embeddings"))
          .timeout(Duration.ofMillis(timeoutMs))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        // Fallback: try /embedding endpoint if /v1/embeddings failed
        return callLegacyEmbeddingEndpoint(texts);
      }

      JsonNode root = json.readTree(response.body());
      JsonNode dataArr = root.get("data");
      if (dataArr == null || !dataArr.isArray()) {
        throw ApiException.internal("EVE_EMBEDDING_INVALID_RESPONSE", "Missing 'data' array in embeddings response.");
      }

      List<float[]> vectors = new ArrayList<>(dataArr.size());
      for (JsonNode item : dataArr) {
        JsonNode embArr = item.get("embedding");
        if (embArr != null && embArr.isArray()) {
          if (embArr.size() == 0) {
            throw ApiException.internal("EVE_EMBEDDING_EMPTY", "Received empty embedding vector from model.");
          }
          float[] vec = new float[embArr.size()];
          for (int i = 0; i < embArr.size(); i++) {
            double val = embArr.get(i).asDouble();
            if (Double.isNaN(val) || Double.isInfinite(val)) {
              throw ApiException.internal("EVE_EMBEDDING_INVALID_VALUE", "Embedding vector contains NaN or Infinite value.");
            }
            vec[i] = (float) val;
          }
          vectors.add(vec);
        }
      }
      return vectors;

    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("Embedding request failed on localhost server", e);
      throw ApiException.internal("EVE_EMBEDDING_FAILED", "Embedding inference failed: " + e.getMessage());
    } finally {
      if (acquired) {
        inferenceSemaphore.release();
      }
    }
  }

  private List<float[]> callLegacyEmbeddingEndpoint(List<String> texts) {
    try {
      List<float[]> results = new ArrayList<>(texts.size());
      for (String text : texts) {
        ObjectNode payload = json.createObjectNode();
        payload.put("content", text);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl + "/embedding"))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          JsonNode root = json.readTree(response.body());
          JsonNode embNode = root.get("embedding");
          if (embNode != null && embNode.isArray()) {
            float[] vec = new float[embNode.size()];
            for (int i = 0; i < embNode.size(); i++) {
              vec[i] = (float) embNode.get(i).asDouble();
            }
            results.add(vec);
          }
        }
      }
      return results;
    } catch (Exception e) {
      throw ApiException.internal("EVE_EMBEDDING_FAILED", "Legacy embedding call failed: " + e.getMessage());
    }
  }

  @Override
  public boolean isAvailable() {
    return "READY".equals(status);
  }

  @Override
  public String getModelName() {
    return "Qwen3-Embedding-0.6B-Q8_0";
  }

  @Override
  public int getDimension() {
    return dimensions;
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
