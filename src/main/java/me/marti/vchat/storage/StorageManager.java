package me.marti.vchat.storage;

import me.marti.vchat.VChat;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/** Durable vChat state. Every JDBC operation runs on the shared plugin I/O executor. */
public final class StorageManager {

    private final VChat plugin;
    private final ExecutorService io;
    private final boolean mysql;
    private final String url;
    private final String user;
    private final String password;
    private final File dataFolder;
    private final int legacyPlayerCount;
    private final CompletableFuture<Void> ready;

    public StorageManager(VChat plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
        this.dataFolder = plugin.getDataFolder();
        this.legacyPlayerCount = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new File(dataFolder, "data.yml")).getInt("player-count", 0);
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        this.mysql = "mysql".equalsIgnoreCase(config.getString("storage.type", "sqlite"));
        if (mysql) {
            String host = config.getString("storage.mysql.host", "localhost");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "valerin");
            boolean ssl = config.getBoolean("storage.mysql.use-ssl", false);
            this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            this.user = config.getString("storage.mysql.user", "root");
            this.password = config.getString("storage.mysql.password", "");
        } else {
            File file = new File(dataFolder, config.getString("storage.sqlite-file", "vchat.db"));
            this.url = "jdbc:sqlite:" + file.getAbsolutePath();
            this.user = "";
            this.password = "";
        }
        this.ready = CompletableFuture.runAsync(this::createSchema, io);
    }

    public boolean isMysql() {
        return mysql;
    }

    public CompletableFuture<Void> ready() {
        return ready;
    }

    /** Inserts PDC defaults only when no durable row exists, then returns MySQL/SQLite authority. */
    public CompletableFuture<PlayerState> loadOrMigrate(UUID uuid, String name, PlayerState legacy) {
        return supply(connection -> {
            try {
                connection.setAutoCommit(false);
                PlayerState current = selectState(connection, uuid);
                if (current == null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO vchat_preferences(player_uuid,player_name,name_normalized,msg_enabled,social_spy,chat_muted,mentions_enabled,death_muted,notify_enabled) VALUES(?,?,?,?,?,?,?,?,?)")) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, name);
                        statement.setString(3, normalizeName(name));
                        bindState(statement, 4, legacy);
                        statement.executeUpdate();
                        replaceIgnores(connection, uuid, legacy.ignores());
                        current = legacy;
                    } catch (SQLException race) {
                        current = selectState(connection, uuid);
                        if (current == null) throw race;
                    }
                }
                updateName(connection, uuid, name);
                connection.commit();
                return current;
            } catch (SQLException error) {
                rollback(connection);
                throw new IllegalStateException("Could not load player state", error);
            }
        });
    }

    public CompletableFuture<PlayerState> load(UUID uuid) {
        return supply(connection -> {
            try {
                PlayerState state = selectState(connection, uuid);
                return state == null ? defaults() : state;
            } catch (SQLException error) {
                throw new IllegalStateException("Could not load player state", error);
            }
        });
    }

    public CompletableFuture<Void> save(UUID uuid, String name, PlayerState state) {
        return supply(connection -> {
            try {
                connection.setAutoCommit(false);
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE vchat_preferences SET player_name=?,name_normalized=?,msg_enabled=?,social_spy=?,chat_muted=?,mentions_enabled=?,death_muted=?,notify_enabled=? WHERE player_uuid=?")) {
                    statement.setString(1, name);
                    statement.setString(2, normalizeName(name));
                    bindState(statement, 3, state);
                    statement.setString(9, uuid.toString());
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO vchat_preferences(player_uuid,player_name,name_normalized,msg_enabled,social_spy,chat_muted,mentions_enabled,death_muted,notify_enabled) VALUES(?,?,?,?,?,?,?,?,?)")) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, name);
                        statement.setString(3, normalizeName(name));
                        bindState(statement, 4, state);
                        statement.executeUpdate();
                    }
                }
                replaceIgnores(connection, uuid, state.ignores());
                connection.commit();
                return null;
            } catch (SQLException error) {
                rollback(connection);
                throw new IllegalStateException("Could not save player state", error);
            }
        });
    }

    public CompletableFuture<PlayerRegistration> registerPlayer(UUID uuid, String name) {
        return supply(connection -> {
            try {
                boolean first;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO vchat_players(player_uuid,player_name,name_normalized) VALUES(?,?,?)")) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, name);
                    statement.setString(3, normalizeName(name));
                    first = statement.executeUpdate() == 1;
                } catch (SQLException duplicate) {
                    first = false;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE vchat_players SET player_name=?,name_normalized=? WHERE player_uuid=?")) {
                    update.setString(1, name);
                    update.setString(2, normalizeName(name));
                    update.setString(3, uuid.toString());
                    update.executeUpdate();
                }
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT player_number FROM vchat_players WHERE player_uuid=?")) {
                    query.setString(1, uuid.toString());
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) throw new SQLException("Player registration disappeared");
                        return new PlayerRegistration(first, result.getLong(1));
                    }
                }
            } catch (SQLException error) {
                throw new IllegalStateException("Could not register player", error);
            }
        });
    }

    public CompletableFuture<Boolean> loadGlobalMute() {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT state_value FROM vchat_network_state WHERE state_key='global_mute'")) {
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && "1".equals(result.getString(1));
                }
            } catch (SQLException error) {
                throw new IllegalStateException("Could not load global mute", error);
            }
        });
    }

    public CompletableFuture<Void> saveGlobalMute(boolean muted) {
        return supply(connection -> {
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE vchat_network_state SET state_value=? WHERE state_key='global_mute'")) {
                    statement.setString(1, muted ? "1" : "0");
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO vchat_network_state(state_key,state_value) VALUES('global_mute',?)")) {
                        statement.setString(1, muted ? "1" : "0");
                        statement.executeUpdate();
                    }
                }
                return null;
            } catch (SQLException error) {
                throw new IllegalStateException("Could not save global mute", error);
            }
        });
    }

    private void createSchema() {
        dataFolder.mkdirs();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            String number = mysql ? "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vchat_players (player_number " + number
                    + ", player_uuid VARCHAR(36) NOT NULL UNIQUE, player_name VARCHAR(16) NOT NULL, name_normalized VARCHAR(16) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vchat_preferences (player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16) NOT NULL, name_normalized VARCHAR(16) NOT NULL, msg_enabled SMALLINT NOT NULL, social_spy SMALLINT NOT NULL, chat_muted SMALLINT NOT NULL, mentions_enabled SMALLINT NOT NULL, death_muted SMALLINT NOT NULL, notify_enabled SMALLINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vchat_ignores (owner_uuid VARCHAR(36) NOT NULL, ignored_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(owner_uuid,ignored_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vchat_network_state (state_key VARCHAR(64) PRIMARY KEY, state_value VARCHAR(255) NOT NULL)");
            migrateLegacyCounter(connection);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not initialize " + (mysql ? "MySQL" : "SQLite") + " storage", error);
        }
    }

    private void migrateLegacyCounter(Connection connection) throws SQLException {
        if (legacyPlayerCount <= 0) return;
        try (PreparedStatement rows = connection.prepareStatement("SELECT COUNT(*) FROM vchat_players");
             ResultSet result = rows.executeQuery()) {
            if (!result.next() || result.getLong(1) != 0L) return;
        }
        try (PreparedStatement marker = connection.prepareStatement(
                "INSERT INTO vchat_network_state(state_key,state_value) VALUES('migration.local-player-count',?)")) {
            marker.setString(1, String.valueOf(legacyPlayerCount));
            marker.executeUpdate();
        } catch (SQLException alreadyMigrated) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (mysql) {
                statement.executeUpdate("ALTER TABLE vchat_players AUTO_INCREMENT=" + (legacyPlayerCount + 1L));
            } else {
                statement.executeUpdate("INSERT OR REPLACE INTO sqlite_sequence(name,seq) VALUES('vchat_players'," + legacyPlayerCount + ")");
            }
        }
    }

    private PlayerState selectState(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT msg_enabled,social_spy,chat_muted,mentions_enabled,death_muted,notify_enabled FROM vchat_preferences WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new PlayerState(result.getBoolean(1), result.getBoolean(2), result.getBoolean(3),
                        result.getBoolean(4), result.getBoolean(5), result.getBoolean(6), selectIgnores(connection, uuid));
            }
        }
    }

    private Set<UUID> selectIgnores(Connection connection, UUID uuid) throws SQLException {
        Set<UUID> ignores = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ignored_uuid FROM vchat_ignores WHERE owner_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) ignores.add(UUID.fromString(result.getString(1)));
            }
        }
        return ignores;
    }

    private void replaceIgnores(Connection connection, UUID uuid, Set<UUID> ignores) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM vchat_ignores WHERE owner_uuid=?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vchat_ignores(owner_uuid,ignored_uuid) VALUES(?,?)")) {
            for (UUID ignored : ignores) {
                insert.setString(1, uuid.toString());
                insert.setString(2, ignored.toString());
                insert.addBatch();
            }
            if (!ignores.isEmpty()) insert.executeBatch();
        }
    }

    private static void bindState(PreparedStatement statement, int offset, PlayerState state) throws SQLException {
        statement.setBoolean(offset, state.msgEnabled());
        statement.setBoolean(offset + 1, state.socialSpy());
        statement.setBoolean(offset + 2, state.personalChatMuted());
        statement.setBoolean(offset + 3, state.mentionsEnabled());
        statement.setBoolean(offset + 4, state.deathMuted());
        statement.setBoolean(offset + 5, state.notifyEnabled());
    }

    private void updateName(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE vchat_preferences SET player_name=?,name_normalized=? WHERE player_uuid=?")) {
            statement.setString(1, name);
            statement.setString(2, normalizeName(name));
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    private <T> CompletableFuture<T> supply(Function<Connection, T> operation) {
        return ready.thenApplyAsync(ignored -> {
            try (Connection connection = connection()) {
                return operation.apply(connection);
            } catch (SQLException error) {
                throw new IllegalStateException(error);
            }
        }, io);
    }

    private Connection connection() throws SQLException {
        return mysql ? DriverManager.getConnection(url, user, password) : DriverManager.getConnection(url);
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    public static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static PlayerState defaults() {
        return new PlayerState(true, false, false, true, false, false, Set.of());
    }
}
