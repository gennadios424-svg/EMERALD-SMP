package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoinFlipCommand implements org.bukkit.command.CommandExecutor, Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Flip> flips = new LinkedHashMap<>();
    private final AtomicBoolean resolving = new AtomicBoolean(false);
    public CoinFlipCommand(EmeraldSMP plugin){this.plugin=plugin;}

    @Override public boolean onCommand(org.bukkit.command.CommandSender s,org.bukkit.command.Command c,String l,String[] a){
        if(!(s instanceof Player p)){s.sendMessage("Only players can use /cf.");return true;}
        if(a.length==2&&a[0].equalsIgnoreCase("create")){create(p,plugin.getEconomyManager().parseAmount(a[1]));return true;}
        open(p);return true;
    }
    private void open(Player p){
        Inventory inv=Bukkit.createInventory(new Holder(),54,"§2§l🪙 COIN FLIP");
        inv.setItem(4,button(Material.GOLD_INGOT,"§6§lCREATE COIN FLIP",List.of("§7Use: §f/cf create <amount>","§7Example: §f/cf create 1000")));
        int slot=10;
        for(Flip f:flips.values()){
            if(slot>43)break;
            if(f.owner.equals(p.getUniqueId()))continue;
            inv.setItem(slot++,button(Material.SUNFLOWER,"§e"+f.name,List.of("§7Wager: §6$"+f.amount,"","§aClick to JOIN")));
        }
        if(slot==10)inv.setItem(22,button(Material.GRAY_DYE,"§7No active coin flips",List.of("§8Create one with /cf create <amount>")));
        Flip mine=flips.values().stream().filter(f->f.owner.equals(p.getUniqueId())).findFirst().orElse(null);
        if(mine!=null)inv.setItem(49,button(Material.BARRIER,"§c§lCANCEL YOUR FLIP",List.of("§7Wager: §6$"+mine.amount,"§eClick to cancel and refund")));
        p.openInventory(inv);
    }
    private void create(Player p,long amount){
        if(amount<=0){p.sendMessage("§cUsage: /cf create <amount>");return;}
        if(plugin.getEconomyManager().getBalance(p.getUniqueId())<amount){p.sendMessage("§cYou do not have enough money.");return;}
        if(!plugin.getEconomyManager().withdraw(p.getUniqueId(),amount)){p.sendMessage("§cCould not hold your wager.");return;}
        UUID id=UUID.randomUUID();flips.put(id,new Flip(id,p.getUniqueId(),p.getName(),amount));
        p.sendMessage("§aCoin flip created for §6$"+amount+"§a.");refreshOpenMenus();
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder))return;
        e.setCancelled(true);int raw=e.getRawSlot();
        if(raw==4){p.closeInventory();p.sendMessage("§eCreate with: §f/cf create <amount>");return;}
        if(raw==49){
            Flip mine=flips.values().stream().filter(f->f.owner.equals(p.getUniqueId())).findFirst().orElse(null);
            if(mine!=null&&flips.remove(mine.id)!=null){plugin.getEconomyManager().deposit(p.getUniqueId(),mine.amount);p.sendMessage("§aYour coin flip was cancelled and §6$"+mine.amount+" §arefunded.");refreshOpenMenus();}
            return;
        }
        if(raw<10||raw>43)return;
        List<Flip> visible=flips.values().stream().filter(f->!f.owner.equals(p.getUniqueId())).toList();
        int idx=raw-10;if(idx>=0&&idx<visible.size())confirm(p,visible.get(idx));
    }
    private void confirm(Player p,Flip f){
        Inventory inv=Bukkit.createInventory(new ConfirmHolder(f.id),27,"§2§l🪙 JOIN COIN FLIP?");
        inv.setItem(11,button(Material.LIME_WOOL,"§a§lCONFIRM",List.of("§7Creator: §f"+f.name,"§7Your wager: §6$"+f.amount)));
        inv.setItem(15,button(Material.RED_WOOL,"§c§lCANCEL",List.of("§7Return to /cf")));p.openInventory(inv);
    }
    @EventHandler public void confirmClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof ConfirmHolder h))return;
        e.setCancelled(true);if(e.getRawSlot()==15){open(p);return;}if(e.getRawSlot()!=11)return;
        Flip f=flips.get(h.id);
        if(f==null||f.owner.equals(p.getUniqueId())){p.sendMessage("§cThat coin flip is no longer available.");open(p);return;}
        if(!resolving.compareAndSet(false,true))return;
        try{
            f=flips.remove(h.id);if(f==null)return;
            if(plugin.getEconomyManager().getBalance(p.getUniqueId())<f.amount||!plugin.getEconomyManager().withdraw(p.getUniqueId(),f.amount)){
                flips.put(f.id,f);p.sendMessage("§cYou no longer have enough money.");open(p);return;
            }
            boolean creatorWins=ThreadLocalRandom.current().nextBoolean();UUID winner=creatorWins?f.owner:p.getUniqueId();
            long payout=Math.multiplyExact(f.amount,2L);
            if(!plugin.getEconomyManager().deposit(winner,payout)){
                plugin.getEconomyManager().deposit(f.owner,f.amount);plugin.getEconomyManager().deposit(p.getUniqueId(),f.amount);
                flips.put(f.id,f);p.sendMessage("§cThe flip could not be completed; wagers were refunded.");return;
            }
            Player creator=Bukkit.getPlayer(f.owner);
            if(creator!=null)creator.sendMessage("§e🪙 Coin flip result: §f"+(winner.equals(creator.getUniqueId())?creator.getName():p.getName())+" §awon §6$"+payout+"§a!");
            p.sendMessage("§e🪙 Coin flip complete! §aWinner: §f"+(winner.equals(p.getUniqueId())?p.getName():f.name)+"§a.");
            open(p);refreshOpenMenus();
        }finally{resolving.set(false);}
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID u=e.getPlayer().getUniqueId();boolean changed=flips.values().removeIf(f->{if(!f.owner.equals(u))return false;plugin.getEconomyManager().deposit(u,f.amount);return true;});
        if(changed)refreshOpenMenus();
    }
    private void refreshOpenMenus(){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Holder)open(p);}
    private ItemStack button(Material m,String n,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(n);meta.setLore(lore);i.setItemMeta(meta);return i;}
    private static final class Holder implements InventoryHolder{public Inventory getInventory(){return null;}}
    private static final class ConfirmHolder implements InventoryHolder{final UUID id;ConfirmHolder(UUID id){this.id=id;}public Inventory getInventory(){return null;}}
    private record Flip(UUID id,UUID owner,String name,long amount){}
}