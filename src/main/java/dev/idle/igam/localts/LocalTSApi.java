package dev.idle.igam.localts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocalTSApi {

  private static final String BASE_URL = "https://localts.store/v1";
  private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0], null);
  private static final OkHttpClient CLIENT = newClient();
  private static final OkHttpClient DIRECT_CLIENT = CLIENT.newBuilder().proxy(Proxy.NO_PROXY).build();

  public static class MeResponse {
    public final String username;
    public final double balance;

    public MeResponse(String username, double balance) {
      this.username = username;
      this.balance = balance;
    }
  }

  public static class Product {
    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final double priceInCredits;
    public final int stock;
    public final String type;
    public final String logoUrl;
    public final List<String> tags;
    public final Map<Integer, Double> quantityDiscounts;

    public Product(String id, String name, String description, String category,
                   double priceInCredits, int stock, String type, String logoUrl,
                   List<String> tags, Map<Integer, Double> quantityDiscounts) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.category = category;
      this.priceInCredits = priceInCredits;
      this.stock = stock;
      this.type = type;
      this.logoUrl = logoUrl;
      this.tags = tags;
      this.quantityDiscounts = quantityDiscounts;
    }

    public double discountFor(int quantity) {
      double best = 0.0D;
      for (Map.Entry<Integer, Double> tier : quantityDiscounts.entrySet()) {
        if (quantity >= tier.getKey() && tier.getValue() > best) {
          best = tier.getValue();
        }
      }
      return best;
    }

    public double effectiveTotal(int quantity) {
      return priceInCredits * (1.0D - discountFor(quantity) / 100.0D) * quantity;
    }
  }

  public static class PurchaseResponse {
    public final boolean success;
    public final String orderId;
    public final String error;

    public PurchaseResponse(boolean success, String orderId, String error) {
      this.success = success;
      this.orderId = orderId;
      this.error = error;
    }
  }

  public static class Order {
    public final String id;
    public final String productId;
    public final String productType;
    public final long timestamp;

    public Order(String id, String productId, String productType, long timestamp) {
      this.id = id;
      this.productId = productId;
      this.productType = productType;
      this.timestamp = timestamp;
    }
  }

  public static class OrdersResponse {
    public final List<Order> orders;
    public final int page;
    public final int size;
    public final int totalPages;
    public final int totalElements;

    public OrdersResponse(List<Order> orders, int page, int size,
                          int totalPages, int totalElements) {
      this.orders = orders;
      this.page = page;
      this.size = size;
      this.totalPages = totalPages;
      this.totalElements = totalElements;
    }
  }

  public static class OrderItem {
    public final String id;
    public final String content;

    public OrderItem(String id, String content) {
      this.id = id;
      this.content = content;
    }
  }

  public static class OrderDetail {
    public final String orderId;
    public final String status;
    public final String productName;
    public final List<OrderItem> items;

    public OrderDetail(String orderId, String status, String productName,
                       List<OrderItem> items) {
      this.orderId = orderId;
      this.status = status;
      this.productName = productName;
      this.items = items;
    }
  }

  public static MeResponse getMe(String apiKey) throws Exception {
    JsonObject json = getJson(BASE_URL + "/me", apiKey);
    return new MeResponse(
        stringOr(json, "username", ""),
        doubleOr(json, "balance"));
  }

  public static boolean isExcludedProduct(String name, String category) {
    String nameLower = name == null ? "" : name.toLowerCase();
    String catLower = category == null ? "" : category.toLowerCase();
    return nameLower.contains("full access") || nameLower.contains("fullaccess")
        || nameLower.contains("ip vanish") || nameLower.contains("ipvanish")
        || nameLower.contains("skyblock coins")
        || catLower.contains("full access") || catLower.contains("fullaccess")
        || catLower.contains("ip vanish") || catLower.contains("ipvanish")
        || catLower.contains("skyblock coins");
  }

  public static List<Product> parseProducts(JsonElement root) {
    JsonArray arr;
    if (root != null && root.isJsonArray()) {
      arr = root.getAsJsonArray();
    } else if (root != null && root.isJsonObject() && root.getAsJsonObject().has("products")
        && root.getAsJsonObject().get("products").isJsonArray()) {
      arr = root.getAsJsonObject().getAsJsonArray("products");
    } else {
      arr = new JsonArray();
    }

    List<Product> products = new ArrayList<>();
    for (JsonElement el : arr) {
      if (!el.isJsonObject()) {
        continue;
      }
      JsonObject p = el.getAsJsonObject();
      String name = stringOr(p, "name", "");
      String category = stringOr(p, "category", "");
      if (isExcludedProduct(name, category)) {
        continue;
      }
      List<String> tags = new ArrayList<>();
      if (p.has("tags") && p.get("tags").isJsonArray()) {
        for (JsonElement t : p.getAsJsonArray("tags")) {
          tags.add(t.getAsString());
        }
      }
      Map<Integer, Double> quantityDiscounts = new TreeMap<>();
      if (p.has("quantityDiscounts") && p.get("quantityDiscounts").isJsonObject()) {
        for (Map.Entry<String, JsonElement> tier
            : p.getAsJsonObject("quantityDiscounts").entrySet()) {
          try {
            quantityDiscounts.put(
                Integer.parseInt(tier.getKey().trim()), tier.getValue().getAsDouble());
          } catch (NumberFormatException ignored) {
          }
        }
      }
      products.add(new Product(
          stringOr(p, "id", ""),
          name,
          stringOr(p, "description", ""),
          category,
          doubleOr(p, "priceInCredits"),
          intOr(p, "stock"),
          stringOr(p, "type", ""),
          stringOr(p, "logoUrl", ""),
          tags,
          quantityDiscounts));
    }
    return products;
  }

  public static List<Product> getProducts(String apiKey) throws Exception {
    return parseProducts(getJsonElement(BASE_URL + "/products", apiKey));
  }

  public static PurchaseResponse purchase(String apiKey, String productId,
                                          int amount) throws Exception {
    HttpUrl url = HttpUrl.get(BASE_URL).newBuilder()
        .addPathSegment("products")
        .addPathSegment(productId)
        .addPathSegment("purchase")
        .addQueryParameter("amount", Integer.toString(amount))
        .build();
    Request request = authenticatedRequest(url.toString(), apiKey)
        .post(EMPTY_BODY)
        .build();
    JsonObject json = readResponse(request);
    String orderId = stringOr(json, "orderId", stringOr(json, "order-id", null));
    JsonElement successValue = json.get("success");
    boolean success = successValue == null || successValue.isJsonNull()
        ? orderId != null && !orderId.isBlank()
        : successValue.getAsBoolean();
    String error = stringOr(json, "error", null);
    return new PurchaseResponse(success, orderId, error);
  }

  public static OrdersResponse getOrders(String apiKey, int page, int size) throws Exception {
    String urlStr = BASE_URL + "/orders?page=" + page + "&size=" + size;
    JsonObject json = getJson(urlStr, apiKey);
    JsonArray arr = json.has("orders") && json.get("orders").isJsonArray()
        ? json.getAsJsonArray("orders") : new JsonArray();
    List<Order> orders = new ArrayList<>();
    for (JsonElement el : arr) {
      JsonObject o = el.getAsJsonObject();
      String id = stringOr(o, "id", stringOr(o, "order-id", ""));
      String productId = stringOr(o, "productId", stringOr(o, "product-id", ""));
      String productType = stringOr(o, "productType", stringOr(o, "product-type", ""));
      long timestamp = longOr(o, "timestamp");
      orders.add(new Order(id, productId, productType, timestamp));
    }
    return new OrdersResponse(
        orders,
        intOr(json, "page"),
        intOr(json, "size"),
        intOr(json, "totalPages"),
        intOr(json, "totalElements"));
  }

  public static OrderDetail getOrderDetail(String apiKey, String orderId) throws Exception {
    HttpUrl url = HttpUrl.get(BASE_URL).newBuilder()
        .addPathSegment("orders")
        .addPathSegment("get-order")
        .addQueryParameter("id", orderId)
        .build();
    JsonObject json = getJson(url.toString(), apiKey);
    String status = stringOr(json, "status", "");
    String productName = stringOr(json, "product-name", stringOr(json, "productName", ""));
    String retOrderId = stringOr(json, "order-id", stringOr(json, "orderId", orderId));
    List<OrderItem> items = new ArrayList<>();
    if (json.has("items") && json.get("items").isJsonArray()) {
      for (JsonElement el : json.getAsJsonArray("items")) {
        JsonObject item = el.getAsJsonObject();
        items.add(new OrderItem(
            stringOr(item, "id", ""),
            stringOr(item, "content", "")));
      }
    }
    return new OrderDetail(
        retOrderId,
        status,
        productName,
        items);
  }

  private static String stringOr(JsonObject json, String field, String fallback) {
    JsonElement value = json.get(field);
    return value == null || value.isJsonNull() ? fallback : value.getAsString();
  }

  private static double doubleOr(JsonObject json, String field) {
    JsonElement value = json.get(field);
    return value == null || value.isJsonNull() ? 0.0D : value.getAsDouble();
  }

  private static int intOr(JsonObject json, String field) {
    JsonElement value = json.get(field);
    return value == null || value.isJsonNull() ? 0 : value.getAsInt();
  }

  private static long longOr(JsonObject json, String field) {
    JsonElement value = json.get(field);
    return value == null || value.isJsonNull() ? 0L : value.getAsLong();
  }

  static JsonObject getJson(String url, String apiKey) throws Exception {
    return readResponse(authenticatedRequest(url, apiKey).get().build());
  }

  private static JsonElement getJsonElement(String url, String apiKey) throws Exception {
    return execute(authenticatedRequest(url, apiKey).get().build());
  }

  private static Request.Builder authenticatedRequest(String url, String apiKey) throws IOException {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IOException("No API key set");
    }
    return new Request.Builder().url(url).header("X-API-Key", apiKey);
  }

  private static JsonObject readResponse(Request request) throws Exception {
    JsonElement element = execute(request);
    if (!element.isJsonObject()) {
      throw new IOException("Unexpected response format (expected JSON object)");
    }
    return element.getAsJsonObject();
  }

  static JsonElement execute(Request request) throws IOException {
    try (Response response = call(request)) {
      int code = response.code();
      String body = response.body() == null ? "" : response.body().string().trim();
      if (code == 401) {
        throw new IOException("Invalid or missing API key");
      }
      if (body.isEmpty()) {
        throw new IOException(code >= 400
            ? "HTTP " + code + " (no response body)"
            : "Empty response from server");
      }
      if (isHtml(response, body)) {
        if (code == 403) {
          throw new IOException(blockedMessage(response, body));
        }
        throw new IOException(code >= 400
            ? "HTTP " + code + " error"
            : "Received HTML instead of JSON (possible Cloudflare challenge)");
      }

      JsonElement element;
      try {
        element = new JsonParser().parse(body);
      } catch (RuntimeException parseError) {
        throw new IOException(code >= 400
            ? "HTTP " + code + " (invalid JSON response)"
            : "Invalid JSON response from server", parseError);
      }
      if (element == null || element.isJsonNull()) {
        throw new IOException(code >= 400 ? "HTTP " + code : "Empty response from server");
      }
      if (element.isJsonObject()) {
        JsonObject json = element.getAsJsonObject();
        if (json.has("success") && !json.get("success").isJsonNull()
            && !json.get("success").getAsBoolean()) {
          throw new IOException(errorMessage(json, "Request failed"));
        }
        if (code >= 400) {
          throw new IOException(errorMessage(json, "HTTP " + code));
        }
      } else if (code >= 400) {
        throw new IOException("HTTP " + code);
      }
      return element;
    }
  }

  private static boolean isHtml(Response response, String body) {
    String contentType = response.header("Content-Type", "");
    return contentType.toLowerCase(Locale.ROOT).contains("text/html") || body.startsWith("<");
  }

  private static String blockedMessage(Response response, String body) {
    if (!body.toLowerCase(Locale.ROOT).contains("cloudflare")) {
      return "HTTP 403: Server returned HTML instead of JSON";
    }
    String ray = response.header("CF-RAY");
    return "Cloudflare blocked the LocalTS request (HTTP 403"
        + (ray == null || ray.isBlank() ? "" : ", Ray ID " + ray)
        + ")";
  }

  private static String errorMessage(JsonObject json, String fallback) {
    for (String field : new String[] {"error", "message"}) {
      JsonElement value = json.get(field);
      if (value != null && value.isJsonPrimitive()) {
        String message = value.getAsString();
        if (!message.isBlank()) {
          return message;
        }
      }
    }
    return fallback;
  }

  private static Response call(Request request) throws IOException {
    try {
      return CLIENT.newCall(request).execute();
    } catch (SSLException viaProxy) {
      if (!(viaProxy instanceof SSLHandshakeException) && !"GET".equals(request.method())) {
        throw secureConnectionError(viaProxy);
      }
      try {
        return DIRECT_CLIENT.newCall(request).execute();
      } catch (SSLException direct) {
        throw secureConnectionError(direct);
      }
    }
  }

  private static IOException secureConnectionError(SSLException cause) {
    return new IOException("Couldn't establish a secure connection to Localts. "
        + "Check VPN, proxy, or antivirus HTTPS scanning. [" + cause + "]", cause);
  }

  private static OkHttpClient newClient() {
    OkHttpClient.Builder builder = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS);
    try {
      KeyStore windowsRoots = KeyStore.getInstance("Windows-ROOT");
      windowsRoots.load(null, null);
      X509TrustManager trust = new DualTrustManager(
          trustManagerFor(null), trustManagerFor(windowsRoots));
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {trust}, null);
      builder.sslSocketFactory(context.getSocketFactory(), trust);
    } catch (GeneralSecurityException | IOException ignored) {
    }
    return builder.build();
  }

  private static X509TrustManager trustManagerFor(KeyStore store)
      throws GeneralSecurityException {
    TrustManagerFactory factory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    factory.init(store);
    for (TrustManager manager : factory.getTrustManagers()) {
      if (manager instanceof X509TrustManager x509) {
        return x509;
      }
    }
    throw new NoSuchAlgorithmException("No X509TrustManager available");
  }

  private record DualTrustManager(X509TrustManager jvm, X509TrustManager windows)
      implements X509TrustManager {
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      try {
        jvm.checkServerTrusted(chain, authType);
      } catch (CertificateException rejectedByJvm) {
        try {
          windows.checkServerTrusted(chain, authType);
        } catch (CertificateException rejectedByWindows) {
          rejectedByJvm.addSuppressed(rejectedByWindows);
          throw rejectedByJvm;
        }
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {
      jvm.checkClientTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      X509Certificate[] jvmIssuers = jvm.getAcceptedIssuers();
      X509Certificate[] windowsIssuers = windows.getAcceptedIssuers();
      X509Certificate[] all = Arrays.copyOf(jvmIssuers, jvmIssuers.length + windowsIssuers.length);
      System.arraycopy(windowsIssuers, 0, all, jvmIssuers.length, windowsIssuers.length);
      return all;
    }
  }
}
