package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import java.text.NumberFormat;
import java.util.*;

public final class SellMenu implements Listener {
    private static final String TITLE = ChatColor.DARK_GREEN + "💚 EMERALD SELL";
    private static final int SIZE = 54;
    private static final int[] INPUT_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final int TOTAL_SLOT=47, SELL_SLOT=49, CLOSE_SLOT=53;
    private final EmeraldSMP plugin; private final Map<UUID,Session> sessions=new HashMap<>(); private final Set<UUID> locks=new HashSet<>();
    public SellMenu(EmeraldSMP plugin){this.plugin=plugin;}
    public void open(Player p){close(p); Session s=new Session(UUID.randomUUID()); Inventory inv=Bukkit.createInventory(s,SIZE,TITLE); s.inventory=inv;
        ItemStack dark=button(Material.BLACK_STAINED_GLASS_PANE," "), emerald=button(Material.GREEN_STAINED_GLASS_PANE,"§a💚");
        for(int i=0;i<SIZE;i++)inv.setItem(i,dark); for(int i=0;i<9;i++){inv.setItem(i,emerald);inv.setItem(45+i,emerald);}
        for(int row=1;row<=4;row++){inv.setItem(row*9,emerald);inv.setItem(row*9+8,emerald);}
        inv.setItem(TOTAL_SLOT,button(Material.EMERALD,"§a§l💵 TOTAL VALUE",List.of("","§7Current sell value","§f$0")));
        inv.setItem(SELL_SLOT,button(Material.EMERALD_BLOCK,"§a§l💰 SELL ALL",List.of("","§7Sell all valid items","§7Shulker contents included","","§e▶ Click to sell")));
        inv.setItem(CLOSE_SLOT,button(Material.BARRIER,"§f§l✖ CLOSE",List.of("§7Unsold items are returned")));
        sessions.put(p.getUniqueId(),s); p.openInventory(inv); refresh(p,s);}
    public void close(Player p){Session s=sessions.remove(p.getUniqueId());if(s==null||s.processing)return;if(p.getOpenInventory().getTopInventory()==s.inventory)p.closeInventory();returnItems(p,s.inventory);}
    private boolean input(int slot){for(int x:INPUT_SLOTS)if(x==slot)return true;return false;}
    private ItemStack button(Material m,String n){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();if(x!=null){x.setDisplayName(n);i.setItemMeta(x);}return i;}
    private ItemStack button(Material m,String n,List<String> lore){ItemStack i=button(m,n);ItemMeta x=i.getItemMeta();if(x!=null){x.setLore(lore);i.setItemMeta(x);}return i;}
    private long value(ItemStack i){if(i==null||i.getType().isAir())return 0;if(i.getType().name().endsWith("_SHULKER_BOX")&&i.getItemMeta() instanceof BlockStateMeta m&&m.hasBlockState()&&m.getBlockState() instanceof ShulkerBox b){long t=0;for(ItemStack in:b.getInventory().getContents())t=Math.addExact(t,value(in));return t;}return Math.multiplyExact(plugin.getWorthManager().sellValue(i.getType(),1),i.getAmount());}
    private long total(Inventory inv){long t=0;for(int s:INPUT_SLOTS){ItemStack i=inv.getItem(s);if(i!=null&&!i.getType().isAir())try{t=Math.addExact(t,value(i));}catch(ArithmeticException e){return Long.MAX_VALUE;}}return t;}
    private void refresh(Player p,Session s){if(sessions.get(p.getUniqueId())!=s||s.inventory==null||s.processing)return;long t=total(s.inventory);s.inventory.setItem(TOTAL_SLOT,button(Material.EMERALD,"§a§l💵 TOTAL VALUE",List.of("","§7Current sell value","§f$"+NumberFormat.getNumberInstance(Locale.US).format(t))));}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;Session s=sessions.get(p.getUniqueId());if(s==null||s.inventory!=e.getView().getTopInventory())return;int raw=e.getRawSlot();if(raw>=SIZE||raw<0){Bukkit.getScheduler().runTask(plugin,()->refresh(p,s));return;}if(raw==SELL_SLOT){e.setCancelled(true);sell(p,s);return;}if(raw==CLOSE_SLOT){e.setCancelled(true);p.closeInventory();return;}if(!input(raw)){e.setCancelled(true);return;}Bukkit.getScheduler().runTask(plugin,()->refresh(p,s));}
    @EventHandler public void drag(InventoryDragEvent e){if(!(e.getWhoClicked() instanceof Player p))return;Session s=sessions.get(p.getUniqueId());if(s==null||s.inventory!=e.getView().getTopInventory())return;for(int raw:e.getRawSlots())if(raw<SIZE&&!input(raw)){e.setCancelled(true);return;}Bukkit.getScheduler().runTask(plugin,()->refresh(p,s));}
    @EventHandler public void closeEvent(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p))return;Session s=sessions.get(p.getUniqueId());if(s==null||s.inventory!=e.getInventory()||s.processing)return;sessions.remove(p.getUniqueId());returnItems(p,s.inventory);}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e){Player p=e.getPlayer();Session s=sessions.remove(p.getUniqueId());if(s!=null&&!s.processing)returnItems(p,s.inventory);}
    private void sell(Player p,Session s){UUID u=p.getUniqueId();if(!locks.add(u))return;try{if(sessions.get(u)!=s||s.inventory!=p.getOpenInventory().getTopInventory()||s.processing)return;long total=0,count=0;List<Integer> slots=new ArrayList<>();List<ItemStack> originals=new ArrayList<>();for(int slot:INPUT_SLOTS){ItemStack i=s.inventory.getItem(slot);if(i==null||i.getType().isAir())continue;long v;try{v=value(i);total=Math.addExact(total,v);count=Math.addExact(count,i.getAmount());}catch(ArithmeticException ex){p.sendMessage("§cThe sale is too large to process safely.");return;}if(v>0){slots.add(slot);originals.add(i.clone());}}if(slots.isEmpty()||total<=0){p.sendMessage("§c❌ There are no sellable items in the sell area.");return;}s.processing=true;for(int slot:slots)s.inventory.setItem(slot,null);boolean ok;try{ok=plugin.getEconomyManager().deposit(u,total);}catch(RuntimeException ex){ok=false;}if(!ok){for(int i=0;i<slots.size();i++)s.inventory.setItem(slots.get(i),originals.get(i));s.processing=false;refresh(p,s);p.sendMessage("§cThe economy rejected the transaction. Your items were restored.");return;}sessions.remove(u);s.processing=false;p.sendMessage("§a💚 Sold §f"+String.format(Locale.US,"%,d",count)+"§a items for §f"+plugin.getEconomyManager().format(total)+"§a!");p.closeInventory();}finally{locks.remove(u);}}
    private void returnItems(Player p,Inventory inv){if(inv==null)return;for(int slot:INPUT_SLOTS){ItemStack i=inv.getItem(slot);if(i==null||i.getType().isAir())continue;inv.setItem(slot,null);Map<Integer,ItemStack> left=p.getInventory().addItem(i.clone());for(ItemStack x:left.values())p.getWorld().dropItemNaturally(p.getLocation(),x);}}
    public void disable(){for(Player p:Bukkit.getOnlinePlayers()){Session s=sessions.get(p.getUniqueId());if(s!=null&&!s.processing)p.closeInventory();}sessions.clear();}
    private static final class Session implements InventoryHolder{private final UUID id;private Inventory inventory;private boolean processing;Session(UUID id){this.id=id;}public Inventory getInventory(){return inventory;}}
}