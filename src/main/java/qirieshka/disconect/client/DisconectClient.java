package qirieshka.disconect.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DisconectClient implements ClientModInitializer {

    private static final Set<UUID> whitelist = new HashSet<>();
    private static boolean isWhitelistEnabled = true; // Флаг для включения или отключения белого списка

    @Override
    public void onInitializeClient() {
        // Загрузка UUID игроков из JSON файла

        // Запускаем проверку в отдельном потоке
        new Thread(() -> {
            while (true) {
                loadWhitelistFromJson();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    checkNearbyPlayers(client);
                }
                try {
                    Thread.sleep(100); // Задержка между проверками в 5 секунд
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Поток проверки был прерван: " + e.getMessage());
                }
            }
        }).start();
    }

    private void loadWhitelistFromJson() {
        Path configPath = Paths.get("config", "whitelist.json");

        // Проверка, существует ли файл, если нет, создаем его с пустым массивом
        if (Files.notExists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent()); // Создаем папку config, если ее нет
                try (FileWriter writer = new FileWriter(configPath.toFile())) {
                    new Gson().toJson(new JsonArray(), writer); // Создаем пустой JSON массив
                }
                System.out.println("Файл whitelist.json создан в папке config.");
            } catch (IOException e) {
                System.err.println("Ошибка при создании файла whitelist.json: " + e.getMessage());
            }
        }

        // Загружаем UUID из JSON файла
        try (FileReader reader = new FileReader(configPath.toFile())) {
            JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();

            for (JsonElement element : jsonArray) {
                String uuidString = element.getAsString();
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    whitelist.add(uuid);
                } catch (IllegalArgumentException e) {
                    System.err.println("Некорректный формат UUID: " + uuidString);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке файла whitelist.json: " + e.getMessage());
        }
    }

    private void checkNearbyPlayers(MinecraftClient client) {
        if (!isWhitelistEnabled || client.player == null || client.world == null) return;

        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof PlayerEntity player && !whitelist.contains(player.getUuid())) {
                disconnectFromServer(client, "Игрок вне белого списка обнаружен поблизости. UUID: " + player.getUuid());
                break;
            }
        }
    }

    private void disconnectFromServer(MinecraftClient client, String reason) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().getConnection().disconnect(Text.of(reason));
        }
    }
}
