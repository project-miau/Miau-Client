package dev.idle.igam.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import dev.idle.igam.AccountManager;
import dev.idle.igam.auth.Account;
import dev.idle.igam.auth.MicrosoftAuth;
import dev.idle.igam.auth.SessionManager;
import dev.idle.igam.localts.LocalTSApi;
import dev.idle.igam.localts.LocalTSApi.*;
import dev.idle.igam.utils.Notification;
import dev.idle.igam.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

public class GuiLocalTS extends GuiScreen {

  private static final int TAB_STOCK = 0;
  private static final int TAB_ORDERS = 1;

  private static final int BTN_CONNECT = 100;
  private static final int BTN_TAB_STOCK = 101;
  private static final int BTN_TAB_ORDERS = 102;
  private static final int BTN_BACK = 103;
  private static final int BTN_BUY = 104;
  private static final int BTN_PREV_PAGE = 105;
  private static final int BTN_NEXT_PAGE = 106;
  private static final int BTN_VIEW_ORDER = 107;
  private static final int BTN_IMPORT_SELECTED = 108;
  private static final int BTN_BACK_TO_ORDERS = 109;
  private static final int BTN_LOGOUT = 110;
  private static final int BTN_EDIT_KEY = 111;
  private static final int BTN_IMPORT_ALL = 112;
  private static final int BTN_USE_LOCALTS_KEY = 113;

  private static final File API_KEY_FILE = new File(Minecraft.getMinecraft().mcDataDir, "localts.key");

  private final GuiScreen previousScreen;

  private boolean connected = false;
  private String apiKey = "";
  private String username = "";
  private volatile double balance = 0;
  private volatile String status = null;
  private int currentTab = TAB_STOCK;

  private volatile List<Product> products = new ArrayList<>();
  private int selectedProduct = -1;
  private ProductList productList;

  private volatile List<Order> orders = new ArrayList<>();
  private int selectedOrder = -1;
  private int ordersPage = 0;
  private int ordersTotalPages = 0;
  private int ordersTotalElements = 0;
  private OrderList orderList;

  private boolean viewingOrderDetail = false;
  private volatile OrderDetail currentOrderDetail = null;
  private Set<Integer> selectedItems = new HashSet<>();
  private String orderDetailProductType = "Text";
  private OrderItemList orderItemList;

  private GuiTextField apiKeyField;
  private GuiTextField amountField;
  private GuiButton connectButton;
  private GuiButton backButton;
  private GuiButton buyButton;

  private ExecutorService executor;
  private volatile boolean loading = false;

  public GuiLocalTS(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    Keyboard.enableRepeatEvents(true);

    if (executor == null) {
      executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Mimeow-LocalTS");
        thread.setDaemon(true);
        return thread;
      });
    }

    if (!connected && apiKey.isEmpty()) {
      String savedKey = loadApiKey();
      if (savedKey != null && !savedKey.isEmpty()) {
        apiKey = savedKey;
        doConnect();
        return;
      }
    }

    if (!connected) {
      initApiKeyScreen();
    } else if (viewingOrderDetail) {
      initOrderDetailScreen();
    } else {
      initMainScreen();
    }
  }

  private void initApiKeyScreen() {
    ScaledResolution sr = new ScaledResolution(mc);
    int cx = sr.getScaledWidth() / 2;
    int cy = sr.getScaledHeight() / 2;

    apiKeyField = new GuiTextField(1, mc.fontRendererObj, cx - 100, cy - 10, 200, 20);
    apiKeyField.setMaxStringLength(256);
    apiKeyField.setFocused(true);
    if (!apiKey.isEmpty()) {
      apiKeyField.setText(apiKey);
    }

    connectButton = new GuiButton(BTN_CONNECT, cx - 100, cy + 20, 200, 20, "Connect");
    buttonList.add(connectButton);

    String localtsKey = loadLocaltsAppKey();
    int backY = cy + 50;
    if (localtsKey != null && !localtsKey.equals(apiKey)) {
      buttonList.add(new GuiButton(BTN_USE_LOCALTS_KEY, cx - 100, backY, 200, 20,
          "Use Localts App Key"));
      backY += 30;
    }

    backButton = new GuiButton(BTN_BACK, cx - 100, backY, 200, 20, "Back");
    buttonList.add(backButton);
  }

  private void initMainScreen() {
    final int tabW = 80;
    final int gap = 4;
    int tabX = width / 2 - (tabW * 2 + gap) / 2;
    int tabY = 38;

    GuiButton stockTabButton = new GuiButton(BTN_TAB_STOCK, tabX, tabY, tabW, 20,
        currentTab == TAB_STOCK
            ? TextFormatting.translate("&a&lStock&r") : "Stock");
    GuiButton ordersTabButton = new GuiButton(BTN_TAB_ORDERS, tabX + tabW + gap, tabY, tabW, 20,
        currentTab == TAB_ORDERS
            ? TextFormatting.translate("&a&lOrders&r") : "Orders");
    buttonList.add(stockTabButton);
    buttonList.add(ordersTabButton);

    buttonList.add(new GuiButton(BTN_LOGOUT, width - 55, 4, 50, 16, "Logout"));
    buttonList.add(new GuiButton(BTN_EDIT_KEY, width - 110, 4, 50, 16, "Edit Key"));

    if (currentTab == TAB_STOCK) {
      initStockTab();
    } else {
      initOrdersTab();
    }

    backButton = new GuiButton(BTN_BACK, width / 2 - 50, height - 26, 100, 20, "Back");
    buttonList.add(backButton);
  }

  private void initStockTab() {
    productList = new ProductList(mc);
    productList.registerScrollButtons(11, 12);

    int bottomRow = height - 50;
    amountField = new GuiTextField(2, mc.fontRendererObj, width / 2 - 100, bottomRow, 60, 20);
    amountField.setMaxStringLength(4);
    amountField.setText("1");

    buyButton = new GuiButton(BTN_BUY, width / 2 - 30, bottomRow, 130, 20, "Purchase");
    buttonList.add(buyButton);
  }

  private void initOrdersTab() {
    orderList = new OrderList(mc);
    orderList.registerScrollButtons(11, 12);

    int bottomRow = height - 50;
    buttonList.add(new GuiButton(BTN_PREV_PAGE, width / 2 - 120, bottomRow, 50, 20, "< Prev"));
    buttonList.add(new GuiButton(BTN_NEXT_PAGE, width / 2 + 70, bottomRow, 50, 20, "Next >"));
    buttonList.add(new GuiButton(BTN_VIEW_ORDER, width / 2 - 60, bottomRow, 120, 20, "View Order"));
  }

  private void initOrderDetailScreen() {
    if (currentOrderDetail != null && !currentOrderDetail.items.isEmpty()) {
      orderItemList = new OrderItemList(mc);
      orderItemList.registerScrollButtons(11, 12);
    }

    int bottomRow = height - 50;
    buttonList.add(new GuiButton(BTN_IMPORT_SELECTED, width / 2 - 110, bottomRow, 100, 20, "Import Selected"));
    buttonList.add(new GuiButton(BTN_IMPORT_ALL, width / 2 + 10, bottomRow, 100, 20, "Import All"));

    backButton = new GuiButton(BTN_BACK, width / 2 - 50, height - 26, 100, 20, "Back");
    buttonList.add(backButton);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    if (!connected) {
      drawApiKeyScreen(mouseX, mouseY, partialTicks);
    } else if (viewingOrderDetail) {
      drawOrderDetailScreen(mouseX, mouseY, partialTicks);
    } else {
      drawMainScreen(mouseX, mouseY, partialTicks);
    }
  }

  private void drawApiKeyScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();

    drawCenteredString(fontRendererObj, "LocalTS Store", width / 2, height / 2 - 50, 0x55FFFF);
    drawCenteredString(fontRendererObj,
        TextFormatting.translate("&7Enter your API key to connect.&r"),
        width / 2, height / 2 - 30, -1);

    if (apiKeyField != null) {
      apiKeyField.drawTextBox();
    }

    if (status != null) {
      String formatted = TextFormatting.translate(status);
      List<String> lines = fontRendererObj.listFormattedStringToWidth(formatted, Math.max(200, width - 40));
      int y = height / 2 + 76;
      for (String line : lines) {
        drawCenteredString(fontRendererObj, line, width / 2, y, -1);
        y += fontRendererObj.FONT_HEIGHT + 2;
      }
    }

    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  private void drawMainScreen(int mouseX, int mouseY, float partialTicks) {
    if (currentTab == TAB_STOCK && productList != null) {
      productList.drawScreen(mouseX, mouseY, partialTicks);
    } else if (currentTab == TAB_ORDERS && orderList != null) {
      orderList.drawScreen(mouseX, mouseY, partialTicks);
    }

    drawCenteredString(fontRendererObj, "LocalTS Store", width / 2, 6, 0x55FFFF);

    String userInfo = TextFormatting.translate(
        String.format("&7User: &b%s &8| &7Balance: &a%.0f &7credits&r", username, balance));
    drawCenteredString(fontRendererObj, userInfo, width / 2, 18, -1);

    if (currentTab == TAB_STOCK) {
      if (amountField != null) {
        drawString(fontRendererObj,
            TextFormatting.translate("&7Qty:&r"),
            width / 2 - 120, height - 45, -1);
        amountField.drawTextBox();
      }
      if (buyButton != null) {
        buyButton.enabled = selectedProduct >= 0 && selectedProduct < products.size() && !loading;
      }

      if (selectedProduct >= 0 && selectedProduct < products.size()) {
        Product p = products.get(selectedProduct);
        int qty = parseAmount();
        double total = p.effectiveTotal(qty);
        String info = TextFormatting.translate(
            String.format("&7Selected: &f%s &8- &a%.0f credits &8- &eStock: %d%s&r",
                p.name, p.priceInCredits, p.stock, discountTiers(p)));
        drawCenteredString(fontRendererObj, info, width / 2, height - 62, -1);
        if (qty > 1) {
          String totalInfo = TextFormatting.translate(
              String.format("&7%d x &8= &a%.0f credits&r", qty, total));
          drawCenteredString(fontRendererObj, totalInfo, width / 2, height - 52, -1);
        }
      }
    } else if (currentTab == TAB_ORDERS) {
      String pageInfo = TextFormatting.translate(
          String.format("&7Page %d/%d &8(%d total)&r",
              ordersPage + 1, Math.max(ordersTotalPages, 1), ordersTotalElements));
      drawCenteredString(fontRendererObj, pageInfo, width / 2, height - 62, -1);
    }

    updateButtonStates();

    drawStatusBar();

    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  private void drawOrderDetailScreen(int mouseX, int mouseY, float partialTicks) {
    if (orderItemList != null) {
      orderItemList.drawScreen(mouseX, mouseY, partialTicks);
    } else {
      drawDefaultBackground();
    }

    drawCenteredString(fontRendererObj, "Order Detail", width / 2, 6, 0x55FFFF);

    if (currentOrderDetail != null) {
      String info = TextFormatting.translate(
          String.format("&7Order: &f%s &8| &7Status: &e%s&r",
              truncate(currentOrderDetail.orderId, 20), currentOrderDetail.status));
      drawCenteredString(fontRendererObj, info, width / 2, 18, -1);

      if (!currentOrderDetail.productName.isEmpty()) {
        int selectedCount = selectedItems.size();
        int totalCount = currentOrderDetail.items.size();
        String productInfo = TextFormatting.translate(
            String.format("&7Product: &b%s &8| &7Items: &a%d &8| &7Selected: &e%d&r",
                currentOrderDetail.productName, totalCount, selectedCount));
        drawCenteredString(fontRendererObj, productInfo, width / 2, 30, -1);
      }

      for (GuiButton btn : buttonList) {
        if (btn.id == BTN_IMPORT_SELECTED) {
          btn.enabled = !selectedItems.isEmpty() && !loading
              && "PACKAGED".equals(currentOrderDetail.status);
        }
        if (btn.id == BTN_IMPORT_ALL) {
          btn.enabled = !currentOrderDetail.items.isEmpty() && !loading
              && "PACKAGED".equals(currentOrderDetail.status);
        }
      }
    } else {
      drawCenteredString(fontRendererObj,
          TextFormatting.translate("&7Loading order detail...&r"),
          width / 2, height / 2, -1);
    }

    drawStatusBar();
    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  private void drawStatusBar() {
    if (status != null) {
      String statusText = TextFormatting.translate(status);
      Gui.drawRect(0, height - 14, 6 + mc.fontRendererObj.getStringWidth(statusText), height,
          0x64000000);
      drawString(fontRendererObj, statusText, 3, height - 12, -1);
    }
  }

  private void updateButtonStates() {
    for (GuiButton btn : buttonList) {
      switch (btn.id) {
        case BTN_VIEW_ORDER:
          btn.enabled = selectedOrder >= 0 && selectedOrder < orders.size() && !loading;
          break;
        case BTN_PREV_PAGE:
          btn.enabled = ordersPage > 0 && !loading;
          break;
        case BTN_NEXT_PAGE:
          btn.enabled = ordersPage < ordersTotalPages - 1 && !loading;
          break;
      }
    }
  }

  @Override
  public void handleMouseInput() throws java.io.IOException {
    if (connected && !viewingOrderDetail) {
      if (currentTab == TAB_STOCK && productList != null) {
        productList.handleMouseInput();
      } else if (currentTab == TAB_ORDERS && orderList != null) {
        orderList.handleMouseInput();
      }
    } else if (viewingOrderDetail && orderItemList != null) {
      orderItemList.handleMouseInput();
    }
    super.handleMouseInput();
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
    super.mouseClicked(mouseX, mouseY, mouseButton);
    if (!connected && apiKeyField != null) {
      apiKeyField.mouseClicked(mouseX, mouseY, mouseButton);
    }
    if (connected && !viewingOrderDetail && currentTab == TAB_STOCK && amountField != null) {
      amountField.mouseClicked(mouseX, mouseY, mouseButton);
    }
    if (viewingOrderDetail && orderItemList != null && mouseButton == 0) {
      int slot = orderItemList.getSlotIndexFromScreenCoords(mouseX, mouseY);
      if (slot >= 0 && currentOrderDetail != null && slot < currentOrderDetail.items.size()) {
        if (selectedItems.contains(slot)) {
          selectedItems.remove((Integer) slot);
        } else {
          selectedItems.add(slot);
        }
      }
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      if (viewingOrderDetail) {
        viewingOrderDetail = false;
        currentOrderDetail = null;
        orderItemList = null;
        selectedItems.clear();
        initGui();
        return;
      }
      mc.displayGuiScreen(previousScreen);
      return;
    }

    if (!connected && apiKeyField != null) {
      apiKeyField.textboxKeyTyped(typedChar, keyCode);
      if (keyCode == Keyboard.KEY_RETURN) {
        actionPerformed(connectButton);
      }
    }

    if (connected && !viewingOrderDetail && currentTab == TAB_STOCK && amountField != null) {
      if (Character.isDigit(typedChar) || keyCode == Keyboard.KEY_BACK
          || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT
          || keyCode == Keyboard.KEY_RIGHT) {
        amountField.textboxKeyTyped(typedChar, keyCode);
      }
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null || !button.enabled) {
      return;
    }

    switch (button.id) {
      case BTN_CONNECT:
        doConnect();
        break;

      case BTN_USE_LOCALTS_KEY:
        String localtsKey = loadLocaltsAppKey();
        if (localtsKey == null) {
          status = "&cNo Localts app key found.&r";
          break;
        }
        apiKey = localtsKey;
        apiKeyField.setText(localtsKey);
        doConnect();
        break;

      case BTN_TAB_STOCK:
        currentTab = TAB_STOCK;
        selectedProduct = -1;
        initGui();
        if (products.isEmpty()) {
          fetchProducts();
        }
        break;

      case BTN_TAB_ORDERS:
        currentTab = TAB_ORDERS;
        selectedOrder = -1;
        initGui();
        fetchOrders();
        break;

      case BTN_BUY:
        if (!loading) doPurchase();
        break;

      case BTN_PREV_PAGE:
        if (ordersPage > 0 && !loading) {
          ordersPage--;
          selectedOrder = -1;
          fetchOrders();
        }
        break;

      case BTN_NEXT_PAGE:
        if (ordersPage < ordersTotalPages - 1 && !loading) {
          ordersPage++;
          selectedOrder = -1;
          fetchOrders();
        }
        break;

      case BTN_VIEW_ORDER:
        if (selectedOrder >= 0 && selectedOrder < orders.size()) {
          Order order = orders.get(selectedOrder);
          orderDetailProductType = order.productType;
          viewOrderDetail(order.id);
        }
        break;

      case BTN_IMPORT_SELECTED:
        if (!loading) doImportSelected();
        break;

      case BTN_IMPORT_ALL:
        if (!loading) doImportAll();
        break;

      case BTN_LOGOUT:
        doLogout();
        break;

      case BTN_EDIT_KEY:
        connected = false;
        status = null;
        initGui();
        break;

      case BTN_BACK:
        if (viewingOrderDetail) {
          viewingOrderDetail = false;
          currentOrderDetail = null;
          orderItemList = null;
          selectedItems.clear();
          initGui();
        } else {
          mc.displayGuiScreen(previousScreen);
        }
        break;

      default:
        if (productList != null) productList.actionPerformed(button);
        if (orderList != null) orderList.actionPerformed(button);
        if (orderItemList != null) orderItemList.actionPerformed(button);
        break;
    }
  }

  private void doConnect() {
    String key;
    if (apiKeyField != null) {
      key = apiKeyField.getText().trim();
    } else {
      key = apiKey;
    }
    if (key.isEmpty()) {
      status = "&cPlease enter an API key.&r";
      return;
    }

    loading = true;
    status = "&7Connecting...&r";
    if (connectButton != null) connectButton.enabled = false;

    executor.submit(() -> {
      try {
        MeResponse me = LocalTSApi.getMe(key);
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          apiKey = key;
          username = me.username;
          balance = me.balance;
          connected = true;
          status = null;
          loading = false;

          saveApiKey(key);
          initGui();
          fetchProducts();
        });
      } catch (Exception e) {
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          status = String.format("&c%s&r", e.getMessage());
          loading = false;
          if (connectButton != null) connectButton.enabled = true;
          if (!connected) {
            initGui();
          }
        });
      }
    });
  }

  private void doLogout() {
    connected = false;
    apiKey = "";
    username = "";
    balance = 0;
    products.clear();
    orders.clear();
    currentOrderDetail = null;
    viewingOrderDetail = false;
    selectedItems.clear();
    deleteApiKey();
    status = null;
    initGui();
  }

  private void fetchProducts() {
    loading = true;
    status = "&7Loading products...&r";
    final String key = apiKey;
    executor.submit(() -> {
      try {
        List<Product> result = LocalTSApi.getProducts(key);
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          products = result;
          selectedProduct = -1;
          loading = false;
          status = result.isEmpty() ? "&eNo products match the current filters.&r" : null;
        });
      } catch (Exception e) {
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          loading = false;
          status = String.format("&cFailed to load products: %s&r", e.getMessage());
        });
      }
    });
  }

  private void doPurchase() {
    if (selectedProduct < 0 || selectedProduct >= products.size()) return;
    Product p = products.get(selectedProduct);
    int amount = parseAmount();
    if (amount <= 0) {
      status = "&cQuantity must be at least 1.&r";
      return;
    }
    double total = p.effectiveTotal(amount);
    if (total > balance + 0.001D) {
      status = String.format("&cInsufficient balance: need %.0f, have %.0f.&r", total, balance);
      return;
    }

    loading = true;
    final int qty = amount;
    status = String.format("&7Purchasing %d x %s &8(&a%.0f credits&8)...&r", qty, p.name, total);

    executor.submit(() -> {
      try {
        PurchaseResponse resp = LocalTSApi.purchase(apiKey, p.id, qty);
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          loading = false;
          if (resp.success) {
            status = String.format("&aPurchase successful! Order: %s&r", resp.orderId);
            refreshBalance();
            currentTab = TAB_ORDERS;
            selectedOrder = -1;
            initGui();
            fetchOrders();
          } else {
            status = String.format("&cPurchase failed: %s&r",
                resp.error != null ? resp.error : "Unknown error");
          }
        });
      } catch (Exception e) {
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          loading = false;
          status = String.format("&cPurchase error: %s&r", e.getMessage());
        });
      }
    });
  }

  private int parseAmount() {
    if (amountField == null || amountField.getText().trim().isEmpty()) {
      return 1;
    }
    try {
      return Integer.parseInt(amountField.getText().trim());
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  private static String discountTiers(Product product) {
    if (product.quantityDiscounts.isEmpty()) {
      return "";
    }
    StringBuilder tiers = new StringBuilder(" &8| &b");
    boolean first = true;
    for (Map.Entry<Integer, Double> tier : product.quantityDiscounts.entrySet()) {
      if (!first) {
        tiers.append(", ");
      }
      tiers.append(tier.getKey()).append("+: -")
          .append(tier.getValue().intValue()).append('%');
      first = false;
    }
    return tiers.append("&r").toString();
  }

  private void refreshBalance() {
    final String key = apiKey;
    executor.submit(() -> {
      try {
        MeResponse me = LocalTSApi.getMe(key);
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          username = me.username;
          balance = me.balance;
        });
      } catch (Exception ignored) {
      }
    });
  }

  private void fetchOrders() {
    loading = true;
    status = "&7Loading orders...&r";
    executor.submit(() -> {
      try {
        OrdersResponse resp = LocalTSApi.getOrders(apiKey, ordersPage, 25);
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          orders = resp.orders;
          ordersTotalPages = resp.totalPages;
          ordersTotalElements = resp.totalElements;
          selectedOrder = -1;
          loading = false;
          status = null;
        });
      } catch (Exception e) {
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          loading = false;
          status = String.format("&cFailed to load orders: %s&r", e.getMessage());
        });
      }
    });
  }

  private void viewOrderDetail(String orderId) {
    loading = true;
    status = "&7Loading order detail...&r";
    viewingOrderDetail = true;
    currentOrderDetail = null;
    selectedItems.clear();

    executor.submit(() -> {
      try {
        OrderDetail detail = LocalTSApi.getOrderDetail(apiKey, orderId);
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          currentOrderDetail = detail;
          loading = false;
          status = null;
          initGui();
        });
      } catch (Exception e) {
        mc.addScheduledTask(() -> {
          if (mc.currentScreen != this) return;
          loading = false;
          status = String.format("&cFailed to load order: %s&r", e.getMessage());
          viewingOrderDetail = false;
          initGui();
        });
      }
    });
  }

  private void doImportSelected() {
    if (currentOrderDetail == null || selectedItems.isEmpty()) return;
    List<OrderItem> toImport = new ArrayList<>();
    for (int idx : selectedItems) {
      if (idx >= 0 && idx < currentOrderDetail.items.size()) {
        toImport.add(currentOrderDetail.items.get(idx));
      }
    }
    doImportItems(toImport);
  }

  private void doImportAll() {
    if (currentOrderDetail == null || currentOrderDetail.items.isEmpty()) return;
    doImportItems(currentOrderDetail.items);
  }

  private void doImportItems(List<OrderItem> items) {
    if (items.isEmpty()) return;
    loading = true;
    final String productType = orderDetailProductType;
    final String orderId = currentOrderDetail != null ? currentOrderDetail.orderId : "unknown";

    status = String.format("&7Importing %d items...&r", items.size());

    executor.submit(() -> {
      int imported = 0;
      int failed = 0;
      final Executor inline = Runnable::run;
      final List<String> importedUsernames = new ArrayList<>();

      for (OrderItem item : items) {
        String content = item.content.trim();
        if (content.isEmpty()) {
          failed++;
          continue;
        }

        try {
          String importedUsername = null;
          boolean looksLikeCookie = content.startsWith("__Host-MSAAUTHP=")
              || content.startsWith("MSAAUTH=")
              || content.contains("login.live.com")
              || content.contains("__Host-MSAAUTHP")
              || content.contains("MSAAUTH")
              || "File".equalsIgnoreCase(productType);

          Exception firstError = null;

          if (looksLikeCookie) {
            try {
              importedUsername = importAsCookie(content, inline);
            } catch (Exception e) {
              firstError = e;
              try {
                String token = extractToken(content);
                importedUsername = importAsToken(token, inline);
              } catch (Exception ignored) {
                throw firstError;
              }
            }
          } else {
            try {
              String token = extractToken(content);
              importedUsername = importAsToken(token, inline);
            } catch (Exception e) {
              firstError = e;
              try {
                importedUsername = importAsCookie(content, inline);
              } catch (Exception ignored) {
                throw firstError;
              }
            }
          }

          imported++;
          if (importedUsername != null) {
            importedUsernames.add(importedUsername);
          }
        } catch (Exception e) {
          e.printStackTrace();
          System.err.println("Import failed for item: " + truncate(content, 30));
          failed++;
        }
      }

      final int importedF = imported;
      final int failedF = failed;

      mc.addScheduledTask(() -> {
        if (mc.currentScreen != this) return;
        loading = false;
        if (failedF > 0) {
          status = String.format("&aImported %d&r &8| &cFailed %d&r &8from order %s&r",
              importedF, failedF, truncate(orderId, 12));
        } else {
          status = String.format("&aImported %d items from order %s&r",
              importedF, truncate(orderId, 12));
        }

        if (!importedUsernames.isEmpty()) {
          mc.displayGuiScreen(new GuiImportResult(this, importedUsernames, failedF));
        }
      });
    });
  }

  private String extractToken(String content) {
    if (content.contains(":")) {
      String[] parts = content.split(":");
      return parts[parts.length - 1].trim();
    }
    return content.trim();
  }

  private String importAsCookie(String content, Executor inline) throws Exception {
    Map<String, String> cookiesMap = new LinkedHashMap<>();

    String[] lines = content.split("\\r?\\n");
    for (String line : lines) {
      String[] parts = line.split("\\t");
      if (parts.length >= 7 && parts[0].endsWith("login.live.com")) {
        cookiesMap.put(parts[5], parts[6]);
      }
    }

    if (cookiesMap.isEmpty()) {
      String[] partsArray = content.split("[;\\n\\r]+");
      for (String part : partsArray) {
        part = part.trim();
        if (part.isEmpty()) continue;
        int eq = part.indexOf('=');
        if (eq > 0) {
          cookiesMap.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        } else if (!part.contains(":")) {
          cookiesMap.put("MSAAUTH", part);
        }
      }

      if (cookiesMap.isEmpty()) {
        if (content.contains(":")) {
          String[] parts = content.split(":");
          cookiesMap.put("MSAAUTH", parts[parts.length - 1].trim());
        } else if (!content.contains("=")) {
          cookiesMap.put("MSAAUTH", content.trim());
        }
      }
    }

    if (cookiesMap.isEmpty()) {
      throw new Exception("No cookie data found in content");
    }

    StringBuilder cookies = new StringBuilder();
    for (Map.Entry<String, String> entry : cookiesMap.entrySet()) {
      if (cookies.length() > 0) cookies.append("; ");
      cookies.append(entry.getKey()).append("=").append(entry.getValue());
    }

    javax.net.ssl.HttpsURLConnection connection =
        (javax.net.ssl.HttpsURLConnection)
            new java.net.URL(
                    "https://sisu.xboxlive.com/connect/XboxLive/?state=login&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d&tid=896928775&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin&aid=1142970254")
                .openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    connection.setInstanceFollowRedirects(false);
    connection.connect();

    java.util.List<String> setCookies = connection.getHeaderFields().get("Set-Cookie");
    if (setCookies != null) {
      for (String c : setCookies) {
        String[] kv = c.split(";", 2)[0].split("=", 2);
        if (kv.length == 2) cookiesMap.put(kv[0].trim(), kv[1].trim());
      }
      cookies.setLength(0);
      for (Map.Entry<String, String> entry : cookiesMap.entrySet()) {
        if (cookies.length() > 0) cookies.append("; ");
        cookies.append(entry.getKey()).append("=").append(entry.getValue());
      }
    }

    String location = connection.getHeaderField("Location");
    if (location == null)
      throw new Exception("No Location header in first redirect");
    location = location.replaceAll(" ", "%20");

    connection = (javax.net.ssl.HttpsURLConnection) new java.net.URL(location).openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    connection.setRequestProperty("Cookie", cookies.toString());
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    connection.setInstanceFollowRedirects(false);
    connection.connect();

    String location2 = connection.getHeaderField("Location");
    if (location2 == null)
      throw new Exception("No Location header in second redirect");

    connection =
        (javax.net.ssl.HttpsURLConnection)
            new java.net.URL(location2).openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty(
        "Accept",
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
    connection.setInstanceFollowRedirects(false);
    connection.connect();

    String location3 = connection.getHeaderField("Location");
    if (location3 == null)
      throw new Exception(
          "No Location header in third redirect (Cookie invalid or expired)");

    String accTokenStr = location3.split("accessToken=")[1];
    if (accTokenStr.contains("&")) accTokenStr = accTokenStr.split("&")[0];

    String decoded =
        new String(
                java.util.Base64.getDecoder().decode(accTokenStr),
                java.nio.charset.StandardCharsets.UTF_8)
            .split("\"rp://api.minecraftservices.com/\",")[1];
    String token = decoded.split("\"Token\":\"")[1].split("\"")[0];
    String uhs =
        decoded
            .split(
                java.util.regex.Pattern.quote(
                    "{\"DisplayClaims\":{\"xui\":[{\"uhs\":\""))[1]
            .split("\"")[0];

    String mcToken = MicrosoftAuth.acquireMCAccessToken(
        token, uhs, inline).join();
    Session session = MicrosoftAuth.login(mcToken, inline).join();

    Account acc = new Account(
        "", mcToken, session.getUsername(), 0L,
        MicrosoftAuth.CLIENT_ID, MicrosoftAuth.SCOPE, Account.TYPE_COOKIE);
    AccountManager.store(acc);
    return session.getUsername();
  }

  private String importAsToken(String tokenInput, Executor inline) throws Exception {
    try {
      Session session = MicrosoftAuth.login(tokenInput, inline).join();
      Account acc = new Account(
          "", tokenInput, session.getUsername(), 0L,
          MicrosoftAuth.CLIENT_ID, MicrosoftAuth.SCOPE, Account.TYPE_TOKEN);
      AccountManager.store(acc);
      return session.getUsername();
    } catch (Exception ignored) {
    }

    String savedClientId = MicrosoftAuth.CLIENT_ID;
    String savedScope = MicrosoftAuth.SCOPE;
    try {
      MicrosoftAuth.CLIENT_ID = "00000000402b5328";
      MicrosoftAuth.SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

      Map<String, String> msTokens = MicrosoftAuth.refreshMSAccessTokens(
          tokenInput, inline).join();
      String refreshToken = msTokens.get("refresh_token");
      String xboxToken = MicrosoftAuth.acquireXboxAccessToken(
          msTokens.get("access_token"), inline).join();
      Map<String, String> xsts = MicrosoftAuth.acquireXboxXstsToken(xboxToken, inline).join();
      String mcToken = MicrosoftAuth.acquireMCAccessToken(
          xsts.get("Token"), xsts.get("uhs"), inline).join();
      Session session = MicrosoftAuth.login(mcToken, inline).join();

      Account acc = new Account(
          refreshToken, mcToken, session.getUsername(), 0L,
          MicrosoftAuth.CLIENT_ID, MicrosoftAuth.SCOPE, Account.TYPE_TOKEN);
      AccountManager.store(acc);
      return session.getUsername();
    } finally {
      MicrosoftAuth.CLIENT_ID = savedClientId;
      MicrosoftAuth.SCOPE = savedScope;
    }
  }

  private static String loadApiKey() {
    try {
      if (API_KEY_FILE.exists()) {
        try (FileReader reader = new FileReader(API_KEY_FILE)) {
          char[] buf = new char[(int) API_KEY_FILE.length()];
          int len = reader.read(buf);
          return new String(buf, 0, len).trim();
        }
      }
    } catch (Exception ignored) {}
    return null;
  }

  private static String loadLocaltsAppKey() {
    try {
      Preferences root = Preferences.userRoot();
      if (!root.nodeExists("/org/localts/ui")) {
        return null;
      }
      String key = root.node("/org/localts/ui").get("localts_api_key", "").trim();
      return key.isEmpty() ? null : key;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void saveApiKey(String key) {
    try {
      try (FileWriter writer = new FileWriter(API_KEY_FILE)) {
        writer.write(key);
      }
    } catch (Exception ignored) {}
  }

  private static void deleteApiKey() {
    try {
      if (API_KEY_FILE.exists()) {
        API_KEY_FILE.delete();
      }
    } catch (Exception ignored) {}
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  private class ProductList extends GuiSlot {
    public ProductList(Minecraft mc) {
      super(mc, GuiLocalTS.this.width, GuiLocalTS.this.height,
          60, GuiLocalTS.this.height - 74, 22);
    }

    @Override
    protected int getSize() {
      return products.size();
    }

    @Override
    protected boolean isSelected(int slotIndex) {
      return slotIndex == selectedProduct;
    }

    @Override
    protected int getScrollBarX() {
      return (this.width + getListWidth()) / 2 + 2;
    }

    @Override
    public int getListWidth() {
      return Math.min(width - 40, 360);
    }

    @Override
    protected int getContentHeight() {
      return products.size() * 22;
    }

    @Override
    protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
      selectedProduct = slotIndex;
    }

    @Override
    protected void drawBackground() {
      GuiLocalTS.this.drawDefaultBackground();
    }

    @Override
    protected void drawSlot(int entryID, int x, int y, int k, int mouseXIn, int mouseYIn) {
      if (entryID >= products.size()) return;
      FontRenderer fr = GuiLocalTS.this.fontRendererObj;
      Product p = products.get(entryID);

      String tagDisplay = "";
      if (!p.tags.isEmpty()) {
        tagDisplay = " &8[&7" + String.join(", ", p.tags) + "&8]";
      }
      String nameStr = TextFormatting.translate(
          String.format("&f%s%s&r", p.name, tagDisplay));
      GuiLocalTS.this.drawString(fr, nameStr, x + 2, y + 2, -1);

      int stockColor = p.stock > 0 ? 0xFFFF55 : 0xFF5555;
      String priceStr = TextFormatting.translate(
          String.format("&a%.0f credits", p.priceInCredits));
      GuiLocalTS.this.drawString(fr, priceStr, x + 2, y + 12, -1);

      String stockStr = String.format("Stock: %d", p.stock);
      int stockWidth = fr.getStringWidth(stockStr);
      fr.drawStringWithShadow(stockStr, x + getListWidth() - 5 - stockWidth, y + 12, stockColor);
    }
  }

  private class OrderList extends GuiSlot {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public OrderList(Minecraft mc) {
      super(mc, GuiLocalTS.this.width, GuiLocalTS.this.height,
          60, GuiLocalTS.this.height - 74, 20);
    }

    @Override
    protected int getSize() {
      return orders.size();
    }

    @Override
    protected boolean isSelected(int slotIndex) {
      return slotIndex == selectedOrder;
    }

    @Override
    protected int getScrollBarX() {
      return (this.width + getListWidth()) / 2 + 2;
    }

    @Override
    public int getListWidth() {
      return Math.min(width - 40, 360);
    }

    @Override
    protected int getContentHeight() {
      return orders.size() * 20;
    }

    @Override
    protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
      selectedOrder = slotIndex;
      if (isDoubleClick && slotIndex >= 0 && slotIndex < orders.size()) {
        viewOrderDetail(orders.get(slotIndex).id);
      }
    }

    @Override
    protected void drawBackground() {
      GuiLocalTS.this.drawDefaultBackground();
    }

    @Override
    protected void drawSlot(int entryID, int x, int y, int k, int mouseXIn, int mouseYIn) {
      if (entryID >= orders.size()) return;
      FontRenderer fr = GuiLocalTS.this.fontRendererObj;
      Order o = orders.get(entryID);

      String nameStr = TextFormatting.translate(
          String.format("&f%s &8[&e%s&8]&r",
              truncate(o.id, 16), o.productType));
      GuiLocalTS.this.drawString(fr, nameStr, x + 2, y + 4, -1);

      String dateStr = TextFormatting.translate(
          String.format("&7%s&r", DATE_FORMAT.format(new Date(o.timestamp))));
      int dateWidth = fr.getStringWidth(dateStr);
      GuiLocalTS.this.drawString(fr, dateStr,
          x + getListWidth() - 5 - dateWidth, y + 4, -1);
    }
  }

  private class OrderItemList extends GuiSlot {
    public OrderItemList(Minecraft mc) {
      super(mc, GuiLocalTS.this.width, GuiLocalTS.this.height,
          44, GuiLocalTS.this.height - 60, 16);
    }

    @Override
    protected int getSize() {
      return currentOrderDetail != null ? currentOrderDetail.items.size() : 0;
    }

    @Override
    protected boolean isSelected(int slotIndex) {
      return selectedItems.contains(slotIndex);
    }

    @Override
    protected int getScrollBarX() {
      return (this.width + getListWidth()) / 2 + 2;
    }

    @Override
    public int getListWidth() {
      return Math.min(width - 40, 400);
    }

    @Override
    protected int getContentHeight() {
      return getSize() * 16;
    }

    @Override
    protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
      if (isDoubleClick && currentOrderDetail != null
          && slotIndex >= 0 && slotIndex < currentOrderDetail.items.size()) {
        GuiScreen.setClipboardString(currentOrderDetail.items.get(slotIndex).content);
        status = "&aCopied to clipboard!&r";
      }
    }

    @Override
    protected void drawBackground() {
      GuiLocalTS.this.drawDefaultBackground();
    }

    @Override
    protected void drawSlot(int entryID, int x, int y, int k, int mouseXIn, int mouseYIn) {
      if (currentOrderDetail == null || entryID >= currentOrderDetail.items.size()) return;
      FontRenderer fr = GuiLocalTS.this.fontRendererObj;
      OrderItem item = currentOrderDetail.items.get(entryID);

      boolean selected = selectedItems.contains(entryID);
      String checkbox = selected ? "&a[x]" : "&7[ ]";

      String display = truncate(item.content, 55);
      String entryStr = TextFormatting.translate(
          String.format("%s &8%d. &f%s&r", checkbox, entryID + 1, display));
      GuiLocalTS.this.drawString(fr, entryStr, x + 2, y + 2, -1);
    }
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
  }
}
