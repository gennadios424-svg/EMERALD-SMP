package net.emeraldsmp.teams;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TeamManager {
    public static final class TeamData {
        private final String id;
        private String name;
        private UUID owner;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        private TeamData(String id, String name, UUID owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.members.add(owner);
        }

        public String id() { return id; }
        public String name() { return name; }
        public UUID owner() { return owner; }
        public Set<UUID> members() { return Collections.unmodifiableSet(members); }
        private void add(UUID uuid) { members.add(uuid); }
        private void remove(UUID uuid) { members.remove(uuid); }
    }

    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String, TeamData> teams = new LinkedHashMap<>();
    private final Map<UUID, String> byMember = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>();

    public TeamManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    public synchronized void load() {
        teams.clear();
        byMember.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("teams");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            String name = root.getString(id + ".name", id);
            String ownerText = root.getString(id + ".owner");
            if (ownerText == null) continue;
            try {
                UUID owner = UUID.fromString(ownerText);
                TeamData team = new TeamData(id, name, owner);
                team.members.clear();
                for (String member : root.getStringList(id + ".members")) {
                    try { team.add(UUID.fromString(member)); } catch (IllegalArgumentException ignored) {}
                }
                if (!team.members.contains(owner)) team.add(owner);
                teams.put(id, team);
                for (UUID member : team.members) byMember.put(member, id);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TeamData team : teams.values()) {
            String path = "teams." + team.id();
            yaml.set(path + ".name", team.name());
            yaml.set(path + ".owner", team.owner().toString());
            yaml.set(path + ".members", team.members().stream().map(UUID::toString).toList());
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save teams.yml: " + ex.getMessage()); }
    }

    public synchronized TeamData getTeam(UUID uuid) {
        String id = byMember.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public synchronized TeamData getTeamById(String id) { return teams.get(id); }

    public synchronized boolean create(Player owner, String name) {
        String clean = name.trim();
        if (!clean.matches("[A-Za-z0-9_]{3,16}")) return false;
        for (TeamData team : teams.values()) if (team.name().equalsIgnoreCase(clean)) return false;
        if (getTeam(owner.getUniqueId()) != null) return false;
        String id = UUID.randomUUID().toString();
        TeamData team = new TeamData(id, clean, owner.getUniqueId());
        teams.put(id, team);
        byMember.put(owner.getUniqueId(), id);
        save();
        return true;
    }

    public synchronized boolean invite(Player inviter, Player target) {
        TeamData team = getTeam(inviter.getUniqueId());
        if (team == null || getTeam(target.getUniqueId()) != null) return false;
        if (!team.members.contains(inviter.getUniqueId())) return false;
        pendingInvites.put(target.getUniqueId(), team.id());
        return true;
    }

    public synchronized boolean accept(Player target) {
        String id = pendingInvites.remove(target.getUniqueId());
        if (id == null || getTeam(target.getUniqueId()) != null) return false;
        TeamData team = teams.get(id);
        if (team == null) return false;
        team.add(target.getUniqueId());
        byMember.put(target.getUniqueId(), id);
        save();
        return true;
    }

    public synchronized boolean leave(Player player) {
        TeamData team = getTeam(player.getUniqueId());
        if (team == null || team.owner().equals(player.getUniqueId())) return false;
        team.remove(player.getUniqueId());
        byMember.remove(player.getUniqueId());
        save();
        return true;
    }

    public synchronized boolean kick(Player owner, Player target) {
        TeamData team = getTeam(owner.getUniqueId());
        if (team == null || !team.owner().equals(owner.getUniqueId())) return false;
        if (target.getUniqueId().equals(owner.getUniqueId()) || !team.members.contains(target.getUniqueId())) return false;
        team.remove(target.getUniqueId());
        byMember.remove(target.getUniqueId());
        save();
        return true;
    }

    public synchronized List<String> memberNames(TeamData team) {
        List<String> result = new ArrayList<>();
        for (UUID uuid : team.members()) {
            Player online = Bukkit.getPlayer(uuid);
            result.add(online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName());
        }
        return result;
    }

    public synchronized String tag(UUID uuid) {
        TeamData team = getTeam(uuid);
        return team == null ? "" : team.name();
    }

    public synchronized boolean hasPendingInvite(UUID uuid) { return pendingInvites.containsKey(uuid); }
}
