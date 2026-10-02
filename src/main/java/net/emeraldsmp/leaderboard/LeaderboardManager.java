package net.emeraldsmp.leaderboard;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.data.PlayerData;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class LeaderboardManager implements CommandExecutor, Listener {
    private static final String MAIN="§2§l💚 EMERALD LEADERBOARDS";
    private static final String TOP="§2§l💚 TOP §8• §f";
    private final EmeraldSMP plugin;
    private enum Category { MONEY, KILLS, SHARDS, DEATHS, BLOCKS, PLAYTIME }
    public LeaderboardManager(EmeraldSMP plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(!(sender instanceof Player p)) return true;
        if(args.length>0){
            try{openTop(p,Category.valueOf(args[0].toUpperCase(Locale.ROOT)));}catch(Exception ignored){openMain(p);}
        }else openMain(p);
        return true;
    }

    public void openMain(Player p){
        Inventory inv=Bukkit.createInventory(null,27,MAIN);
        inv.setItem(4,icon(Material.EMERALD,"§a§l💚 EMERALD SMP",List.of("§7Persistent server statistics")));
        inv.setItem(10,icon(Material.GOLD_BLOCK,"§6§l💰 MONEY",List.of("§7Top balances")));
        inv.setItem(11,icon(Material.DIAMOND_SWORD,"§c§l⚔ KILLS",List.of("§7Actual player kills")));
        inv.setItem(12,icon(Material.EMERALD,"§a§l💎 EMERALD SHARDS",List.of("§7Actual shard balances")));
        inv.setItem(14,icon(Material.SKELETON_SKULL,"§7§l💀 DEATHS",List.of("§7Actual player deaths")));
        inv.setItem(15,icon(Material.DIAMOND_PICKAXE,"§b§l⛏ BLOCKS BROKEN",List.of("§7Actual blocks mined")));
        inv.setItem(16,icon(Material.CLOCK,"§e§l🕒 PLAYTIME",List.of("§7Actual playtime")));
        inv.setItem(22,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        p.openInventory(inv);
    }

    private void openTop(Player p,Category cat){
        List<Entry> entries=new ArrayList<>();
        for(var e:plugin.getPlayerDataManager().snapshotAll().entrySet()){
            UUID u=e.getKey(); PlayerData d=e.getValue(); OfflinePlayer op=Bukkit.getOfflinePlayer(u);
            long value=switch(cat){
                case MONEY -> d.getBalance();
                case SHARDS -> d.getEmeraldShards();
                case KILLS -> stat(op,Statistic.PLAYER_KILLS);
                case DEATHS -> stat(op,Statistic.DEATHS);
                case BLOCKS -> stat(op,Statistic.MINE_BLOCKS);
                case PLAYTIME -> stat(op,Statistic.PLAY_ONE_MINUTE);
            };
            entries.add(new Entry(d.getUsername()==null?u.toString():d.getUsername(),value));
        }
        entries.sort(Comparator.comparingLong(Entry::value).reversed().thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER));
        Inventory inv=Bukkit.createInventory(null,54,TOP+display(cat));
        inv.setItem(4,icon(Material.EMERALD,"§a§l"+display(cat),List.of("§7Top 10 persistent statistics")));
        for(int i=0;i<Math.min(10,entries.size());i++){
            Entry e=entries.get(i);
            Material mat=i==0?Material.GOLD_BLOCK:i==1?Material.IRON_BLOCK:i==2?Material.COPPER_BLOCK:Material.PAPER;
            inv.setItem(10+(i/5)*9+(i%5),icon(mat,"§f§l"+(i+1)+". §a"+e.name(),List.of("§7Value: §f"+format(cat,e.value()))));
        }
        inv.setItem(45,icon(Material.ARROW,"§e§l← BACK",List.of("§7Leaderboard categories")));
        inv.setItem(49,icon(Material.EMERALD,"§a§l"+display(cat),List.of("§7Showing top 10")));
        inv.setItem(53,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        p.openInventory(inv);
    }

    private long stat(OfflinePlayer p,Statistic s){try{return p.getStatistic(s);}catch(Exception e){return 0L;}}
    private String display(Category c){return switch(c){
        case MONEY->"💰 MONEY"; case KILLS->"⚔ KILLS"; case SHARDS->"💎 EMERALD SHARDS";
        case DEATHS->"💀 DEATHS"; case BLOCKS->"⛏ BLOCKS BROKEN"; case PLAYTIME->"🕒 PLAYTIME";
    };}
    private String format(Category c,long v){
        if(c==Category.MONEY) return plugin.getEconomyManager().format(v);
        if(c==Category.PLAYTIME){long totalMinutes=v/20L/60L;return totalMinutes/60L+"h "+totalMinutes%60L+"m";}
        return String.format(Locale.US,"%,d",Math.max(0L,v));
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String t=e.getView().getTitle();
        if(!t.equals(MAIN)&&!t.startsWith(TOP))return;
        e.setCancelled(true);
        if(t.equals(MAIN)){
            switch(e.getRawSlot()){
                case 10->openTop(p,Category.MONEY); case 11->openTop(p,Category.KILLS); case 12->openTop(p,Category.SHARDS);
                case 14->openTop(p,Category.DEATHS); case 15->openTop(p,Category.BLOCKS); case 16->openTop(p,Category.PLAYTIME);
                case 22->p.closeInventory();
            }
        }else if(e.getRawSlot()==45)openMain(p);else if(e.getRawSlot()==53)p.closeInventory();
    }
    private ItemStack icon(Material m,String n,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();if(x!=null){x.setDisplayName(n);x.setLore(lore);i.setItemMeta(x);}return i;}
    private record Entry(String name,long value){}
}
