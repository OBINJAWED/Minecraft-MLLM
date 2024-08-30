package name.modid;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.client.gui.hud.ChatHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendAndReceiveMsgs implements ModInitializer {
    public static final String MOD_ID = "llm_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(4);
    private static final Pattern SCREENSHOT_PATH_PATTERN = Pattern.compile("Saved screenshot as (.*)");
    private static final String SERVER_URL = "http://127.0.0.1:5000/";
    private static final int MAX_RETRIES = 10;
    private static final long RETRY_DELAY_MS = 100;
    private static final long SCREENSHOT_DELAY_MS = 50;

    private volatile String latestScreenshotBase64 = null;
    private StringBuilder fullMessage = new StringBuilder();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SendAndReceiveMessages mod");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("send")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(this::sendChatMessageToServer)));
    }

    private int sendChatMessageToServer(CommandContext<FabricClientCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");
        String fullMessage = "Me: " + message;

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(SCREENSHOT_DELAY_MS);
                captureScreenshotAndSendMessage(message, fullMessage);
            } catch (InterruptedException e) {
                LOGGER.error("Screenshot delay interrupted", e);
            }
        }, EXECUTOR_SERVICE);

        return Command.SINGLE_SUCCESS;
    }

    private void captureScreenshotAndSendMessage(String message, String fullMessage) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(), screenshotResult -> {
            if (screenshotResult != null) {
                String resultText = screenshotResult.getString();
                LOGGER.info("Screenshot captured: {}", resultText);

                Matcher matcher = SCREENSHOT_PATH_PATTERN.matcher(resultText);
                if (matcher.find()) {
                    String screenshotFilename = matcher.group(1);
                    Path screenshotPath = mc.runDirectory.toPath().resolve("screenshots").resolve(screenshotFilename);
                    LOGGER.info("Constructed screenshot path: {}", screenshotPath);

                    CompletableFuture.runAsync(() -> waitForScreenshotAndSend(screenshotPath, message, fullMessage), EXECUTOR_SERVICE);
                } else {
                    LOGGER.error("Failed to capture screenshot.");
                }
            } else {
                LOGGER.error("Failed to capture screenshot.");
            }
        });
    }

    private void waitForScreenshotAndSend(Path screenshotPath, String message, String fullMessage) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            if (Files.exists(screenshotPath)) {
                try {
                    byte[] imageBytes = Files.readAllBytes(screenshotPath);
                    latestScreenshotBase64 = Base64.getEncoder().encodeToString(imageBytes);
                    sendMessageToServer(message, latestScreenshotBase64, fullMessage);
                    return;
                } catch (IOException e) {
                    LOGGER.error("Failed to read screenshot file", e);
                }
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while waiting for screenshot file", e);
                return;
            }
        }
        LOGGER.error("Screenshot file does not exist: {}", screenshotPath);
    }

    private void sendMessageToServer(String message, String encodedImage, String fullMessage) {
        String jsonMessage = String.format("{\"message\": \"%s\", \"image\": \"%s\"}", message, encodedImage);

        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(SERVER_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonMessage.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                MinecraftClient.getInstance().execute(() -> {
                    ChatHud chatHud = MinecraftClient.getInstance().inGameHud.getChatHud();
                    chatHud.addMessage(Text.literal(fullMessage));
                });

                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                    streamResponse(br);
                }

                MinecraftClient.getInstance().execute(() -> {
                    ChatHud chatHud = MinecraftClient.getInstance().inGameHud.getChatHud();
                });
            } catch (IOException e) {
                LOGGER.error("Failed to send chat message to server", e);
            }
        }, EXECUTOR_SERVICE);
    }

    private void streamResponse(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String word = line.trim();
            if (!word.isEmpty()) {
                displayToken(word);
                try {
                    Thread.sleep(50); // Adjust this delay as needed for smooth appearance
                } catch (InterruptedException e) {
                    LOGGER.error("Streaming interrupted", e);
                }
            }
        }
        // Ensure the last line is displayed
        displayToken("\n");
    }

    private void displayToken(String token) {
        MinecraftClient.getInstance().execute(() -> {
            ChatHud chatHud = MinecraftClient.getInstance().inGameHud.getChatHud();

            // Append the new token to the full message
            if (token.equals("\n")) {
                fullMessage.append("\n");
            } else {
                if (fullMessage.length() > 0 && fullMessage.charAt(fullMessage.length() - 1) != '\n') {
                    fullMessage.append(" ");
                }
                fullMessage.append(token);
            }

            // Maintain a separate displayed content buffer to avoid re-displaying the entire message
            StringBuilder displayedContent = new StringBuilder();

            // Split the full message into lines
            String[] lines = fullMessage.toString().split("\n");
            for (String line : lines) {
                displayedContent.append(line).append("\n");
            }

            // Remove the last newline for proper display in chat
            if (displayedContent.length() > 0) {
                displayedContent.deleteCharAt(displayedContent.length() - 1);
            }

            // Clear previous messages to avoid duplication
            chatHud.clear(false);

            // Add the updated message to the chat HUD
            chatHud.addMessage(Text.literal(displayedContent.toString()));
        });
    }

    private String getLatestScreenshotBase64() {
        return latestScreenshotBase64 != null ? latestScreenshotBase64 : "";
    }
}