package net.emeraldsmp.invest;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class InvestCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    private final InvestmentManager manager;

    public InvestCommand(EmeraldSMP plugin, InvestmentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use /invest.");
            return true;
        }
        if (args.length == 0) {
            manager.open(p);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("withdraw")) {
            manager.withdraw(p);
            return true;
        }
        if (args.length == 1) {
            long amount = plugin.getEconomyManager().parseAmount(args[0]);
            if (amount <= 0 || amount > InvestmentManager.MAX_INVESTMENT) {
                p.sendMessage("§cUsage: /invest [amount] or /invest withdraw. Maximum investment is $125,000,000.");
                return true;
            }
            manager.invest(p, amount);
            return true;
        }
        p.sendMessage("§cUsage: /invest [amount] | /invest withdraw");
        return true;
    }
}
