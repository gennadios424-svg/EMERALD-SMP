package net.emeraldsmp.tpa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TpaCommand implements CommandExecutor {
    private final TpaManager manager;
    private final Mode mode;

    public TpaCommand(TpaManager manager, Mode mode) {
        this.manager = manager;
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        switch (mode) {
            case TPA, TPAHERE -> {
                if (args.length != 1) {
                    player.sendMessage("§cUsage: /" + command.getName() + " <player>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("§cThat player is not online.");
                    return true;
                }

                manager.request(player, target, mode == Mode.TPAHERE);
            }
            case ACCEPT -> manager.accept(player);
            case DENY -> manager.deny(player);
        }

        return true;
    }

    public enum Mode {
        TPA,
        TPAHERE,
        ACCEPT,
        DENY
    }
}
