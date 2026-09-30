package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.managers.EconomyManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OrderCommand implements org.bukkit.command.CommandExecutor, Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID,Order> orders=new LinkedHashMap<>();
    private final AtomicBoolean locked=new AtomicBoolean(false);
    public OrderCommand(EmeraldSMP plugin){this.plugin=plugin;}
    @Override public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c,String l,String[] a){
        if(!(s instanceof Player p)){s.sendMessage("Only players can use /order.");return true;}
        if(a.length==1 && a[0].equalsIgnoreCase("cancel")){p.sendMessage("§cOrder creation cancelled.");return true;}
        if(a.length==4 && a[0].equalsIgnoreCase("create")){
            Material m=Material.matchMaterial(a[1]);
            int amount=parseInt(a[2]); long price=plugin.getEconomyManager().parseAmount(a[3]);
            if(m==null||amount<1||amount>2304||price<1){p.sendMessage("§cUsage: /order create <item> <amount> <priceEach>");return true;}
            create(p,m,amount,price); return true;
        }
        open(p); return true;
    }
    private int parseInt(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}
    private void open(Player p){
        Inventory inv=Bukkit.createInventory(new Holder("orders"),54,"§2§l📦 PLAYER ORDERS");
        int slot=0;
        for(Order o:orders.values()){
            if(slot>=45)break;
            if(o.owner.equals(p.getUniqueId()))continue;
            ItemStack it=new ItemStack(o.item,Math.min(64,o.amount));
            ItemMeta meta=it.getItemMeta();
            meta.setDisplayName("§a"+pretty(o.item));
            meta.setLore(List.of("§7Requested: §f"+o.amount,"§7Price each: §6$"+o.price,"§7Total: §6$"+(o.price*(long)o.amount),"§7Ordered by: §f"+o.name,"","§eClick to fulfill"));
            it.setItemMeta(meta); inv.setItem(slot++,it);
        }
        ItemStack create=new ItemStack(Material.CHEST);
        ItemMeta cm=create.getItemMeta();cm.setDisplayName("§a§l➕ CREATE ORDER");cm.setLore(List.of("§7Use: §f/order create <item> <amount> <priceEach>","§7Example: §f/order create diamond 64 500"));create.setItemMeta(cm);inv.setItem(49,create);
        p.openInventory(inv);
    }
    private void create(Player p,Material m,int amount,long price){
        long total;
        try{total=Math.multiplyExact(price,amount);}catch(ArithmeticException e){p.sendMessage("§cOrder value is too large.");return;}
        if(plugin.getEconomyManager().getBalance(p.getUniqueId())<total){p.sendMessage("§cYou need §f$"+total+" §cto create this order.");return;}
        // Reserve funds immediately so the creator cannot spend them elsewhere.
        if(!plugin.getEconomyManager().withdraw(p.getUniqueId(),total)){p.sendMessage("§cCould not reserve the order payment.");return;}
        UUID id=UUID.randomUUID(); orders.put(id,new Order(id,p.getUniqueId(),p.getName(),m,amount,price,total));
        p.sendMessage("§aOrder created! §7Other players can now fulfill it.");
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h)||!h.kind.equals("orders"))return;
        e.setCancelled(true);
        if(e.getRawSlot()<0||e.getRawSlot()>=45)return;
        List<Order> visible=orders.values().stream().filter(o->!o.owner.equals(p.getUniqueId())).toList();
        if(e.getRawSlot()>=visible.size())return;
        fulfill(p,visible.get(e.getRawSlot()));
    }
    private void fulfill(Player seller,Order o){
        if(!orders.containsKey(o.id)){seller.sendMessage("§cThat order is no longer active.");return;}
        if(o.owner.equals(seller.getUniqueId()))return;
        if(!hasItems(seller,o.item,o.amount)){seller.sendMessage("§cYou do not have enough "+pretty(o.item)+"§c.");return;}
        if(!locked.compareAndSet(false,true))return;
        try{
            Order current=orders.get(o.id); if(current==null)return;
            removeItems(seller,o.item,o.amount);
            if(!plugin.getEconomyManager().deposit(seller.getUniqueId(),o.total)){
                giveItems(seller,o.item,o.amount); seller.sendMessage("§cPayment failed; your items were returned."); return;
            }
            orders.remove(o.id);
            Player owner=Bukkit.getPlayer(o.owner);
            if(owner!=null)owner.sendMessage("§aYour order for §f"+o.amount+" "+pretty(o.item)+" §awas fulfilled by §f"+seller.getName()+"§a.");
            seller.sendMessage("§aOrder fulfilled! You received §6$"+o.total+"§a.");
            open(seller);
        }finally{locked.set(false);}
    }
    private boolean hasItems(Player p,Material m,int n){int left=n;for(ItemStack it:p.getInventory().getStorageContents())if(it!=null&&it.getType()==m)left-=it.getAmount();return left<=0;}
    private void removeItems(Player p,Material m,int n){for(int i=0;i<p.getInventory().getStorageContents().length&&n>0;i++){ItemStack it=p.getInventory().getItem(i);if(it==null||it.getType()!=m)continue;int take=Math.min(n,it.getAmount());it.setAmount(it.getAmount()-take);n-=take;}}
    private void giveItems(Player p,Material m,int n){ItemStack left=new ItemStack(m,n);Map<Integer,ItemStack> extra=p.getInventory().addItem(left);for(ItemStack x:extra.values())p.getWorld().dropItemNaturally(p.getLocation(),x);}
    private String pretty(Material m){return m.name().toLowerCase(Locale.ROOT).replace('_',' ');}
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID u=e.getPlayer().getUniqueId(); 
        List<UUID> refund=new ArrayList<>();
        for(Order o:orders.values()) if(o.owner.equals(u)) refund.add(o.id);
        for(UUID id:refund){Order o=orders.remove(id); if(o!=null) plugin.getEconomyManager().deposit(u,o.total);}
    }
    private record Order(UUID id,UUID owner,String name,Material item,int amount,long price,long total){}
    private static final class Holder implements InventoryHolder{final String kind;Holder(String k){kind=k;}public Inventory getInventory(){return null;}}
}
