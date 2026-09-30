package net.emeraldsmp.auction;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.managers.EconomyManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class AuctionCommand implements org.bukkit.command.CommandExecutor, Listener {
    private final EmeraldSMP plugin;
    private final AuctionManager manager;
    private final Map<UUID, AuctionView> views = new HashMap<>();
    private final Map<UUID, SellSession> selling = new HashMap<>();
    private final Set<UUID> searchWaiting = new HashSet<>();
    private final Set<UUID> priceInput = new HashSet<>();
    private final Set<UUID> buying = new HashSet<>();

    public AuctionCommand(EmeraldSMP plugin, AuctionManager manager) {
        this.plugin = plugin; this.manager = manager;
    }

    @Override public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Only players can use /ah."); return true; }
        if (a.length > 0 && a[0].equalsIgnoreCase("sell")) { openSell(p); return true; }
        open(p, 0); return true;
    }

    private void open(Player p, int page) {
        AuctionView v=current(p);
        List<AuctionManager.Listing> ls=manager.find(v.query,v.category,v.sort);
        int pages=Math.max(1,(ls.size()+44)/45);
        v=new AuctionView(v.query,v.category,v.sort,Math.max(0,Math.min(page,pages-1)));
        views.put(p.getUniqueId(),v);
        Inventory inv=Bukkit.createInventory(new AhHolder(v),54,"§2§l🏪 AUCTION HOUSE");
        int from=v.page*45;
        for(int i=from;i<Math.min(from+45,ls.size());i++){
            AuctionManager.Listing x=ls.get(i);
            ItemStack display=x.item().clone();
            ItemMeta m=display.getItemMeta();
            m.setLore(List.of(
                "§7Amount: §f"+x.amount(),
                "§7Price: §6$"+fmt(x.price()),
                "§7Per Item: §6$"+fmt(x.perItem()),
                "§7Seller: §f"+x.sellerName(),"",
                x.seller().equals(p.getUniqueId())?"§cYou cannot buy your own listing":"§aClick to purchase"
            ));
            display.setItemMeta(m); inv.setItem(i-from,display);
        }
        inv.setItem(45,button(Material.NAME_TAG,"§e§lSEARCH",List.of(v.query.isBlank()?"§7Search item names":"§7Current: §f"+v.query,"§7Partial names supported")));
        inv.setItem(46,button(Material.HOPPER,"§b§lFILTER: "+v.category.label,List.of("§7Click to cycle categories")));
        inv.setItem(47,button(Material.COMPASS,"§d§lSORT: "+v.sort.label,List.of("§7Click to cycle sorting")));
        if(v.page>0)inv.setItem(48,button(Material.ARROW,"§a§lPREVIOUS",List.of("§7Page "+v.page+" / "+pages)));
        inv.setItem(49,button(Material.EMERALD,"§a§l➕ CREATE LISTING",List.of("§7Create a player marketplace listing")));
        if(v.page+1<pages)inv.setItem(50,button(Material.ARROW,"§a§lNEXT",List.of("§7Page "+(v.page+2)+" / "+pages)));
        inv.setItem(53,button(Material.BARRIER,"§c§lCLOSE",List.of("§7Close Auction House")));
        p.openInventory(inv);
    }

    private void openSell(Player p) {
        SellSession old=selling.get(p.getUniqueId());
        if(old==null) old=new SellSession(oldPrice(old));
        selling.put(p.getUniqueId(),old);
        Inventory inv=Bukkit.createInventory(new SellHolder(),27,"§2§l➕ CREATE LISTING");
        inv.setItem(13,button(Material.CHEST,"§e§lPLACE ITEM HERE",List.of("§7Place the exact stack you want to sell here","§7You can use a partial stack")));
        if(old.price>0)inv.setItem(22,button(Material.GOLD_INGOT,"§6§lPRICE: $"+fmt(old.price),List.of("§7Click to change total price")));
        else inv.setItem(22,button(Material.GOLD_INGOT,"§6§lSET PRICE",List.of("§7Enter the total price in chat")));
        inv.setItem(26,button(Material.EMERALD_BLOCK,"§a§lCREATE LISTING",List.of("§7Item + price required")));
        p.openInventory(inv);
    }

    private long oldPrice(SellSession s){return s==null?0:s.price;}

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        Inventory top=e.getView().getTopInventory();
        if(top.getHolder() instanceof AhHolder h){
            e.setCancelled(true); int slot=e.getRawSlot();
            if(slot==45){beginSearch(p);return;} if(slot==46){cycleCategory(p);return;} if(slot==47){cycleSort(p);return;}
            if(slot==48){if(h.view.page>0)open(p,h.view.page-1);return;}
            if(slot==49){openSell(p);return;}
            if(slot==50){List<AuctionManager.Listing> ls=manager.find(h.view.query,h.view.category,h.view.sort);if((h.view.page+1)*45<ls.size())open(p,h.view.page+1);return;}
            if(slot==53){p.closeInventory();return;}
            if(slot>=0&&slot<45){List<AuctionManager.Listing> ls=manager.find(h.view.query,h.view.category,h.view.sort);int idx=h.view.page*45+slot;if(idx<ls.size())buy(p,ls.get(idx).id());}
            return;
        }
        if(top.getHolder() instanceof SellHolder){
            int slot=e.getRawSlot();
            if(slot==22){e.setCancelled(true);beginPrice(p);return;}
            if(slot==26){e.setCancelled(true);createListing(p);return;}
            if(slot<top.getSize() && slot!=13)e.setCancelled(true);
        }
    }

    @EventHandler public void drag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player))return;
        if(e.getView().getTopInventory().getHolder() instanceof SellHolder){
            for(int slot:e.getRawSlots())if(slot!=13){e.setCancelled(true);return;}
        }
    }

    @EventHandler public void close(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof SellHolder))return;
        SellSession s=selling.remove(p.getUniqueId());
        ItemStack item=e.getView().getTopInventory().getItem(13);
        if(item!=null&&!item.getType().isAir())giveBack(p,item);
        if(s!=null)priceInput.remove(p.getUniqueId());
    }

    private void beginSearch(Player p){
        p.closeInventory();searchWaiting.add(p.getUniqueId());
        p.sendMessage("§a§lAUCTION HOUSE SEARCH §8» §fEnter an item name in chat.");
        p.sendMessage("§7Examples: §fdiamond§7, §fnetherite§7, §fredstone§7, §fstone");
        p.sendMessage("§7Type §ccancel §7to clear the search.");
    }

    private void beginPrice(Player p){
        p.closeInventory();priceInput.add(p.getUniqueId());
        p.sendMessage("§6§lAH PRICE §8» §fEnter the total listing price in chat.");
        p.sendMessage("§7Example: §f32000§7. Type §ccancel §7to cancel.");
    }

    @EventHandler public void chat(AsyncPlayerChatEvent e){
        Player p=e.getPlayer(); UUID u=p.getUniqueId();
        if(searchWaiting.remove(u)){e.setCancelled(true);String q=e.getMessage().trim();Bukkit.getScheduler().runTask(plugin,()->{AuctionView v=current(p);views.put(u,new AuctionView(q.equalsIgnoreCase("cancel")?"":q,v.category,v.sort,0));open(p,0);});return;}
        if(priceInput.remove(u)){e.setCancelled(true);String in=e.getMessage().trim();Bukkit.getScheduler().runTask(plugin,()->{
            if(in.equalsIgnoreCase("cancel")){p.sendMessage("§cPrice input cancelled.");openSell(p);return;}
            try{long price=Long.parseLong(in.replace(",",""));if(price<=0)throw new NumberFormatException();SellSession s=selling.get(u);if(s==null)s=new SellSession(0);selling.put(u,new SellSession(price));p.sendMessage("§aPrice set to §6$"+fmt(price)+"§a.");openSell(p);}
            catch(NumberFormatException ex){p.sendMessage("§cEnter a valid positive whole-number price, or §fcancel§c.");}
        });}
    }

    private void cycleCategory(Player p){AuctionView v=current(p);AuctionManager.Category[] a=AuctionManager.Category.values();views.put(p.getUniqueId(),new AuctionView(v.query,a[(v.category.ordinal()+1)%a.length],v.sort,0));open(p,0);}
    private void cycleSort(Player p){AuctionView v=current(p);AuctionManager.SortMode[] a=AuctionManager.SortMode.values();views.put(p.getUniqueId(),new AuctionView(v.query,v.category,a[(v.sort.ordinal()+1)%a.length],0));open(p,0);}

    private void createListing(Player p){
        UUID u=p.getUniqueId();SellSession s=selling.get(u);Inventory inv=p.getOpenInventory().getTopInventory();ItemStack item=inv.getItem(13);
        if(item==null||item.getType().isAir()){p.sendMessage("§cPlace an item in the listing slot first.");return;}
        if(s==null||s.price<=0){p.sendMessage("§cSet a price first.");return;}
        ItemStack secured=item.clone();
        inv.setItem(13,null);
        if(!manager.add(p,secured,s.price)){giveBack(p,secured);p.sendMessage("§cCould not save the listing. Your item was returned.");return;}
        selling.remove(u);p.closeInventory();p.sendMessage("§aListing created for §6$"+fmt(s.price)+"§a.");open(p,0);
    }

    private boolean removeExactFromInventory(Player p,ItemStack target){
        int need=target.getAmount();for(ItemStack it:p.getInventory().getStorageContents())if(it!=null&&it.isSimilar(target))need-=it.getAmount();if(need>0)return false;
        need=target.getAmount();for(int i=0;i<p.getInventory().getStorageContents().length&&need>0;i++){ItemStack it=p.getInventory().getItem(i);if(it==null||!it.isSimilar(target))continue;int take=Math.min(need,it.getAmount());it.setAmount(it.getAmount()-take);need-=take;}return need==0;
    }

    private void buy(Player buyer,UUID id){
        if(!buying.add(id)){buyer.sendMessage("§cThat listing is already being purchased.");return;}
        try{
            AuctionManager.Listing l=manager.get(id);
            if(l==null){buyer.sendMessage("§cThat listing is no longer available.");open(buyer,0);return;}
            if(l.seller().equals(buyer.getUniqueId())){buyer.sendMessage("§cYou cannot buy your own listing.");return;}
            if(!hasInventorySpaceFor(buyer,l.item())){buyer.sendMessage("§cYou need enough inventory space for the item.");return;}
            EconomyManager eco=plugin.getEconomyManager();
            if(eco.getBalance(buyer.getUniqueId())<l.price()){buyer.sendMessage("§cYou need §6$"+fmt(l.price())+"§c.");return;}
            if(!eco.withdraw(buyer.getUniqueId(),l.price())){buyer.sendMessage("§cPayment could not be reserved.");return;}
            AuctionManager.Listing removed=manager.remove(id);
            if(removed==null){eco.deposit(buyer.getUniqueId(),l.price());buyer.sendMessage("§cThe listing changed; your money was returned.");return;}
            Map<Integer,ItemStack> extra=buyer.getInventory().addItem(removed.item().clone());
            if(!extra.isEmpty()){
                removeFromInventory(buyer,removed.item());manager.restore(removed);eco.deposit(buyer.getUniqueId(),removed.price());
                buyer.sendMessage("§cDelivery failed; your money was returned.");return;
            }
            if(!eco.depositToUuid(removed.seller(),removed.price(),removed.sellerName())){
                removeFromInventory(buyer,removed.item());manager.restore(removed);eco.deposit(buyer.getUniqueId(),removed.price());
                buyer.sendMessage("§cSeller payment failed; the purchase was rolled back.");return;
            }
            buyer.sendMessage("§aPurchased §f"+removed.amount()+"x "+pretty(removed.item().getType())+" §afor §6$"+fmt(removed.price())+"§a.");
            Player seller=Bukkit.getPlayer(removed.seller());if(seller!=null)seller.sendMessage("§aYour AH listing sold for §6$"+fmt(removed.price())+"§a.");
            open(buyer,0);
        }finally{buying.remove(id);}
    }

    private boolean hasInventorySpaceFor(Player p,ItemStack item){
        int free=0;for(ItemStack it:p.getInventory().getStorageContents()){if(it==null||it.getType().isAir())free+=item.getMaxStackSize();else if(it.isSimilar(item))free+=item.getMaxStackSize()-it.getAmount();if(free>=item.getAmount())return true;}return false;
    }
    private void removeFromInventory(Player p,ItemStack item){int n=item.getAmount();for(int i=0;i<p.getInventory().getStorageContents().length&&n>0;i++){ItemStack it=p.getInventory().getItem(i);if(it==null||!it.isSimilar(item))continue;int take=Math.min(n,it.getAmount());it.setAmount(it.getAmount()-take);n-=take;}}
    private void giveBack(Player p,ItemStack item){Map<Integer,ItemStack> extra=p.getInventory().addItem(item.clone());for(ItemStack x:extra.values())p.getWorld().dropItemNaturally(p.getLocation(),x);}
    private AuctionView current(Player p){return views.getOrDefault(p.getUniqueId(),new AuctionView("",AuctionManager.Category.ALL,AuctionManager.SortMode.NEWEST,0));}
    private ItemStack button(Material m,String n,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(n);meta.setLore(lore);i.setItemMeta(meta);return i;}
    private String fmt(long n){return String.format("%,d",n);}
    private String pretty(Material m){return m.name().toLowerCase(Locale.ROOT).replace('_',' ');}

    private record AuctionView(String query,AuctionManager.Category category,AuctionManager.SortMode sort,int page){}
    private record SellSession(long price){}
    private static final class AhHolder implements InventoryHolder{final AuctionView view;AhHolder(AuctionView v){view=v;}public Inventory getInventory(){return null;}}
    private static final class SellHolder implements InventoryHolder{public Inventory getInventory(){return null;}}
}
