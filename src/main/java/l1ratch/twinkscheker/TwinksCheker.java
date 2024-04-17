package l1ratch.twinkscheker;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class TwinksCheker extends JavaPlugin implements Listener, CommandExecutor {

    private Connection connection;
    private Map<String, Integer> twinksMap;
    private boolean checkTwinksEnabled;
    private boolean notifyPlayerOnJoin;

    @Override
    public void onEnable() {
        // Подключение к базе данных
        connectToDatabase();
        // Создание таблицы, если она не существует
        createTable();
        // Загрузка конфигурации
        loadConfig();
        // Регистрация слушателя событий и команд
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("checktwinks").setExecutor(this);
        getCommand("checktwinkstoggle").setExecutor(this);
    }

    private void connectToDatabase() {
        String host = getConfig().getString("mysql.host");
        String port = getConfig().getString("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;

        try {
            connection = DriverManager.getConnection(url, username, password);
            getLogger().info("Connected to database!");
        } catch (SQLException e) {
            getLogger().warning("Failed to connect to database: " + e.getMessage());
        }
    }

    private void createTable() {
        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "nickname VARCHAR(100) NOT NULL," +
                    "ip VARCHAR(100) NOT NULL)");
            statement.close();
        } catch (SQLException e) {
            getLogger().warning("Failed to create table: " + e.getMessage());
        }
    }

    private void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        checkTwinksEnabled = getConfig().getBoolean("check_twinks_enabled", true);
        notifyPlayerOnJoin = getConfig().getBoolean("notify_player_on_join", true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String nickname = player.getName();
        String ip = player.getAddress().getAddress().getHostAddress();

        // Запись данных в базу данных
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO player_data (nickname, ip) VALUES (?, ?)");
            statement.setString(1, nickname);
            statement.setString(2, ip);
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            getLogger().warning("Failed to insert player data: " + e.getMessage());
        }

        // Оповещение администратора
        if (checkTwinksEnabled) {
            int twinksCount = getTwinksCount(ip);
            String message = "§c§l[TwChecker] §7§l| §aЗашел игрок §6" + nickname + "§a с §6IP §a" + ip + " и имеет §6" + twinksCount + "§a твинков.";
            getServer().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("twinks.notify"))
                    .forEach(admin -> admin.sendMessage(message));
        }

        // Оповещение игрока
        if (notifyPlayerOnJoin) {
            int twinksCount = getTwinksCount(ip);
            player.sendMessage("§c§l[Epsilon] §7§l| §aУ вас §6" + twinksCount + "§a твинков. Вы вошли с IP: §6" + ip + "§a.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("checktwinks")) {
            if (sender.hasPermission("twinks.check")) {
                if (args.length == 1) {
                    String search = args[0];
                    String query = "SELECT * FROM player_data WHERE nickname=? OR ip=?";
                    try {
                        PreparedStatement statement = connection.prepareStatement(query);
                        statement.setString(1, search);
                        statement.setString(2, search);
                        ResultSet resultSet = statement.executeQuery();
                        sender.sendMessage("§c§l[TwChecker] §7§l| §aСвязанные ники и IP для запроса: " + search);
                        while (resultSet.next()) {
                            sender.sendMessage(resultSet.getString("nickname") + " - " + resultSet.getString("ip"));
                        }
                        statement.close();
                    } catch (SQLException e) {
                        sender.sendMessage("§c§l[TwChecker] §7§l| §aОшибка при выполнении запроса: " + e.getMessage());
                    }
                } else {
                    sender.sendMessage("§c§l[TwChecker] §7§l| §aИспользование: §6/checktwinks <nickname>|<ip>");
                }
            } else {
                sender.sendMessage("§c§l[TwChecker] §7§l| §cУ вас нет прав на использование этой команды!");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("checktwinkstoggle")) {
            if (sender.hasPermission("twinks.toggle")) {
                checkTwinksEnabled = !checkTwinksEnabled;
                getConfig().set("check_twinks_enabled", checkTwinksEnabled);
                saveConfig();
                sender.sendMessage("§c§l[TwChecker] §7§l| §aСтатус проверки твинков изменен на: " + checkTwinksEnabled);
            } else {
                sender.sendMessage("§c§l[TwChecker] §7§l| §aУ вас нет прав на использование этой команды!");
            }
            return true;
        }
        return false;
    }

    private int getTwinksCount(String ip) {
        if (twinksMap == null) {
            twinksMap = new HashMap<>();
        }
        twinksMap.put(ip, twinksMap.getOrDefault(ip, 0) + 1);
        return twinksMap.get(ip);
    }

    @Override
    public void onDisable() {
        // Закрытие соединения с базой данных при отключении плагина
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().warning("Failed to close database connection: " + e.getMessage());
        }
    }
}
