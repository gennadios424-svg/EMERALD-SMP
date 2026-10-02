package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

import java.text.NumberFormat;
import java.util.*;

public final class SellMenu implements Listener {
    private static final String TITLE = ChatColor.DARK_GREEN + "💚 EMERALD SELL";
    private static final int SIZE = 54;
    private static final int[] INPUT_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final int TOTAL_SLOT = 47;
    private static final int SELL_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final EmeraldSMP plugin;
    private final NamespacedKey guiItemKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> transactionLocks = new HashSet<>();

    public SellMenu(EmeraldSMP plugin) { this.plugin=plugin; this.guiItemKey=new NamespacedKey(plugin,"sell_gui_item"); }

    public void open(Player player) {
        close(player);
        Session session=new Session(UUID.randomUUID(),player.getUniqueId());
        Inventory inventory=Bukkit.createInventory(session,SIZE,TITLE);
        session.inventory=inventory;

        // The selling container is intentionally empty. No decorative panes/items are placed in it.
        inventory.setItem(TOTAL_SLOT,button(Material.EMERALD,ChatColor.GREEN+"§l💵 TOTAL VALUE",List.of("",ChatColor.GRAY+"Current sell value",ChatColor.WHITE+"$0")));
        inventory.setItem(SELL_SLOT,button(Material.EMERALD,ChatColor.GREEN+"§l💚 SELL",List.of("",ChatColor.GRAY+"Sell all valid items",ChatColor.GRAY+"Shulker contents included","",ChatColor.YELLOW+"▶ Click to sell")));
        inventory.setItem(CLOSE_SLOT,button(Material.BARRIER,ChatColor.WHITE+"§l✖ CLOSE",List.of(ChatColor.GRAY+"Unsold items are returned")));

        sessions.put(player.getUniqueId(),session);
        player.openInventory(inventory);
        refresh(player,session);
    }

    private boolean isInputSlot(int rawSlot){for(int slot:INPUT_SLOTS)if(slot==rawSlot)return true;return false;}
    private boolean isSellInventory(Inventory inventory){
        return inventory!=null && inventory.getHolder() instanceof Session session
                && sessions.get(session.owner)==session && session.inventory==inventory;
    }

    private ItemStack button(Material material,String name){return button(material,name,Collections.emptyList());}
    private ItemStack button(Material material,String name,List<String> lore){
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();
        if(meta!=null){meta.setDisplayName(name);if(!lore.isEmpty())meta.setLore(lore);meta.getPersistentDataContainer().set(guiItemKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);}return item;
    }
    private boolean isGuiItem(ItemStack item){
        if(item==null||item.getType().isAir())return false;ItemMeta meta=item.getItemMeta();
        return meta!=null&&meta.getPersistentDataContainer().has(guiItemKey,PersistentDataType.BYTE);
    }
    private String money(long amount){return NumberFormat.getNumberInstance(Locale.US).format(amount);}

    private long value(ItemStack item){
        if(item==null||item.getType().isAir()||isGuiItem(item))return 0L;
        if(item.getType().name().endsWith("_SHULKER_BOX")&&item.getItemMeta() instanceof BlockStateMeta meta&&meta.hasBlockState()&&meta.getBlockState() instanceof ShulkerBox box){
            long total=0L;for(ItemStack inside:box.getInventory().getContents())total=Math.addExact(total,value(inside));return total;
        }
        return Math.multiplyExact(plugin.getWorthManager().sellValue(item.getType(),1),item.getAmount());
    }
    private long total(Inventory inventory){
        long total=0L;for(int slot:INPUT_SLOTS){ItemStack item=inventory.getItem(slot);if(item==null||item.getType().isAir())continue;try{total=Math.addExact(total,value(item));}catch(ArithmeticException ex){return Long.MAX_VALUE;}}return total;
    }
    private void refresh(Player player,Session session){
        if(sessions.get(player.getUniqueId())!=session||session.inventory==null||session.processing)return;
        session.inventory.setItem(TOTAL_SLOT,button(Material.EMERALD,ChatColor.GREEN+"§l💵 TOTAL VALUE",List.of("",ChatColor.GRAY+"Current sell value",ChatColor.WHITE+"$"+money(total(session.inventory)))));
    }

    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        Inventory active=event.getView().getTopInventory();
        if(!isSellInventory(active))return;
        Session session=(Session)active.getHolder();
        if(session.processing){event.setCancelled(true);return;}
        int raw=event.getRawSlot();
        if(raw>=SIZE||raw<0){Bukkit.getScheduler().runTask(plugin,()->refresh(player,session));return;}
        if(raw==SELL_SLOT){event.setCancelled(true);sell(player,active,session);return;}
        if(raw==CLOSE_SLOT){event.setCancelled(true);player.closeInventory();return;}
        if(!isInputSlot(raw)){event.setCancelled(true);return;}
        Bukkit.getScheduler().runTask(plugin,()->refresh(player,session));
    }

    @EventHandler public void drag(InventoryDragEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        Inventory active=event.getView().getTopInventory();
        if(!isSellInventory(active))return;
        Session session=(Session)active.getHolder();
        if(session.processing){event.setCancelled(true);return;}
        for(int raw:event.getRawSlots())if(raw<SIZE&&!isInputSlot(raw)){event.setCancelled(true);return;}
        Bukkit.getScheduler().runTask(plugin,()->refresh(player,session));
    }

    public void close(Player player){
        if(player==null)return;
        Session session=sessions.remove(player.getUniqueId());
        if(session==null||session.inventory==null||session.processing)return;
        if(player.getOpenInventory().getTopInventory()==session.inventory) player.closeInventory();
        List<ItemStack> items=captureAndClear(session.inventory);
        Bukkit.getScheduler().runTask(plugin,()->returnItems(player,items));
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event){
        if(!(event.getPlayer() instanceof Player player))return;Inventory inventory=event.getInventory();
        if(!isSellInventory(inventory))return;Session session=(Session)inventory.getHolder();if(session.processing)return;
        sessions.remove(player.getUniqueId(),session);List<ItemStack> items=captureAndClear(inventory);
        Bukkit.getScheduler().runTask(plugin,()->returnItems(player,items));
    }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){
        Player player=event.getPlayer();Session session=sessions.remove(player.getUniqueId());
        if(session!=null&&!session.processing){List<ItemStack> items=captureAndClear(session.inventory);Bukkit.getScheduler().runTask(plugin,()->returnItems(player,items));}
    }
    private List<ItemStack> captureAndClear(Inventory inventory){
        List<ItemStack> items=new ArrayList<>();if(inventory==null)return items;
        for(int slot:INPUT_SLOTS){ItemStack item=inventory.getItem(slot);if(item==null||item.getType().isAir()||isGuiItem(item))continue;items.add(item.clone());inventory.setItem(slot,null);}
        return items;
    }
    private void returnItems(Player player,List<ItemStack> items){
        for(ItemStack item:items){if(item==null||item.getType().isAir())continue;Map<Integer,ItemStack> leftovers=player.getInventory().addItem(item.clone());for(ItemStack remaining:leftovers.values())player.getWorld().dropItemNaturally(player.getLocation(),remaining);}
    }

    private void sell(Player player,Inventory active,Session session){
        UUID uuid=player.getUniqueId();if(!transactionLocks.add(uuid)){player.sendMessage(ChatColor.YELLOW+"💰 Sell transaction is already processing.");return;}
        try{
            if(sessions.get(uuid)!=session||active!=player.getOpenInventory().getTopInventory()||session.processing)return;
            long total=0L,itemCount=0L;List<Integer> sellSlots=new ArrayList<>();List<ItemStack> soldItems=new ArrayList<>();
            for(int slot:INPUT_SLOTS){ItemStack item=active.getItem(slot);if(item==null||item.getType().isAir())continue;long value;
                try{value=value(item);total=Math.addExact(total,value);itemCount=Math.addExact(itemCount,item.getAmount());}
                catch(ArithmeticException ex){player.sendMessage(ChatColor.RED+"The sale is too large to process safely. Nothing was sold.");return;}
                if(value>0){sellSlots.add(slot);soldItems.add(item.clone());}
            }
            if(sellSlots.isEmpty()||total<=0L){player.sendMessage(ChatColor.RED+"❌ There are no sellable items in the sell area.");return;}
            session.processing=true;for(int slot:sellSlots)active.setItem(slot,null);
            boolean deposited;try{deposited=plugin.getEconomyManager().deposit(uuid,total);}catch(RuntimeException ex){deposited=false;plugin.getLogger().warning("Sell transaction failed for "+player.getName()+": "+ex.getMessage());}
            if(!deposited){
                for(int i=0;i<sellSlots.size();i++){int slot=sellSlots.get(i);ItemStack existing=active.getItem(slot),original=soldItems.get(i).clone();
                    if(existing==null||existing.getType().isAir())active.setItem(slot,original);else{Map<Integer,ItemStack> leftovers=active.addItem(original);for(ItemStack leftover:leftovers.values())player.getWorld().dropItemNaturally(player.getLocation(),leftover);}}
                session.processing=false;refresh(player,session);player.sendMessage(ChatColor.RED+"The economy rejected the transaction. Your items were restored.");return;
            }
            sessions.remove(uuid);session.processing=false;player.sendMessage(ChatColor.GREEN+"💚 Sold "+ChatColor.WHITE+String.format(Locale.US,"%,d",itemCount)+ChatColor.GREEN+" items for "+ChatColor.WHITE+plugin.getEconomyManager().format(total)+ChatColor.GREEN+"!");player.playSound(player.getLocation(),org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,1f,1.25f);player.closeInventory();
        }finally{transactionLocks.remove(uuid);}
    }
    public void disable(){for(Player player:Bukkit.getOnlinePlayers()){Session session=sessions.get(player.getUniqueId());if(session!=null&&!session.processing)player.closeInventory();}sessions.clear();}
    private static final class Session implements InventoryHolder{private final UUID sessionId;private final UUID owner;private Inventory inventory;private boolean processing;private Session(UUID sessionId,UUID owner){this.sessionId=sessionId;this.owner=owner;}@Override public Inventory getInventory(){return inventory;}}
}
