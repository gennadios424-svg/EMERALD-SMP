package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class OrderCommand implements org.bukkit.command.CommandExecutor, Listener {
    private static final long INPUT_TIMEOUT_MS = 60_000L;
    private static final long ORDER_EXPIRY_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final int DELIVERY_SLOTS = 45;
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final Map<UUID, PendingOrder> pending = new HashMap<>();
    private final Set<UUID> searchWaiting = new HashSet<>();
    private final Map<UUID, DeliverySession> deliveries = new HashMap<>();
    private final Set<UUID> transactionLocks = new HashSet<>();
    private SortMode sortMode = SortMode.MOST_PAID;
    private final List<Material> requestable;

    public OrderCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        this.requestable = buildRequestable();
        load();
        expireOrders();
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireOrders, 20L * 60L, 20L * 60L);
    }

    private List<Material> buildRequestable() {
        Set<String> excluded = Set.of("AIR","CAVE_AIR","VOID_AIR","BARRIER","BEDROCK","COMMAND_BLOCK","CHAIN_COMMAND_BLOCK","REPEATING_COMMAND_BLOCK","COMMAND_BLOCK_MINECART","STRUCTURE_BLOCK","STRUCTURE_VOID","JIGSAW","LIGHT","KNOWLEDGE_BOOK","DEBUG_STICK","SPAWNER","END_PORTAL","END_GATEWAY","END_PORTAL_FRAME","REINFORCED_DEEPSLATE","POTION","SPLASH_POTION","LINGERING_POTION","TIPPED_ARROW","PLAYER_HEAD","PLAYER_WALL_HEAD","WITHER_SKELETON_SKULL","WITHER_SKELETON_WALL_HEAD");
        List<Material> result = new ArrayList<>();
        for (Material m : Material.values()) if (m.isItem() && !m.isAir() && !m.isLegacy() && !excluded.contains(m.name())) result.add(m);
        result.sort(Comparator.comparing(this::pretty, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(result);
    }

    @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can use /order."); return true; }
        openOrders(p, 0);
        return true;
    }

    private void openOrders(Player p, int page) {
        List<Order> visible = getVisible(p);
        int pages = Math.max(1, (visible.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(new OrderHolder(page), 54, "§2§l📦 PLAYER ORDERS");
        int from = page * 45, to = Math.min(from + 45, visible.size());
        for (int i = from; i < to; i++) {
            Order o = visible.get(i);
            ItemStack icon = new ItemStack(o.item, 1);
            ItemMeta meta = icon.getItemMeta();
            long remaining = o.remaining();
            meta.setDisplayName("§a§l📦 " + pretty(o.item));
            meta.setLore(o.owner.equals(p.getUniqueId())
                    ? List.of("§7Order #§f" + shortId(o.id), "§7Delivered: §f" + fmt(o.delivered) + " / " + fmt(o.required), "§7Stored: §f" + fmt(storedCount(o)) + " items", "", "§7Status: " + (o.status == Status.COMPLETED ? "§aREADY TO CLAIM" : o.status == Status.EXPIRED ? "§eEXPIRED • CLAIM AVAILABLE" : "§ePARTIALLY DELIVERED"), "", "§e📦 Click to claim")
                    : List.of("§7Order #§f" + shortId(o.id), "§7Required: §f" + fmt(o.required), "§7Delivered: §f" + fmt(o.delivered) + " / " + fmt(o.required), "§7Remaining: §f" + fmt(remaining), "", "§7Price: §6" + money(o.price) + " §7/ item", "§7Total: §6" + money(o.total), "§7Expires: §f" + remainingTime(o.expiresAt), "", "§e📦 Click to deliver"));
            icon.setItemMeta(meta);
            inv.setItem(i - from, icon);
        }
        inv.setItem(45, button(Material.HOPPER, "§e§lSORT: " + sortMode.label, List.of("§7Click to switch sorting")));
        if (page > 0) inv.setItem(48, button(Material.ARROW, "§a§lPREVIOUS PAGE", List.of("§7Page " + page + " / " + pages)));
        inv.setItem(49, button(Material.CHEST, "§a§lCREATE ORDER", List.of("§7Choose an item, amount and price", "§7Orders use logical quantities, not stacks")));
        if (page + 1 < pages) inv.setItem(50, button(Material.ARROW, "§a§lNEXT PAGE", List.of("§7Page " + (page + 2) + " / " + pages)));
        inv.setItem(53, button(Material.BARRIER, "§c§lCLOSE", List.of("§7Close this menu")));
        p.openInventory(inv);
    }

    private List<Order> getVisible(Player p) {
        List<Order> list = new ArrayList<>();
        for (Order o : orders.values()) {
            if (o.owner.equals(p.getUniqueId())) {
                if (!o.storage.isEmpty()) list.add(o);
            } else if (o.status == Status.OPEN && o.remaining() > 0 && !isExpired(o)) {
                list.add(o);
            }
        }
        Comparator<Order> cmp = sortMode == SortMode.MOST_PAID
                ? Comparator.comparingLong((Order o) -> o.total).reversed().thenComparingLong(o -> o.price).reversed()
                : Comparator.comparingLong((Order o) -> o.price).reversed().thenComparingLong(o -> o.total).reversed();
        list.sort(cmp);
        return list;
    }

    private void openItemSelection(Player p, int page, String query) {
        List<Material> filtered = filteredItems(query);
        int pages = Math.max(1, (filtered.size() + 35) / 36);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(new ItemHolder(page, query == null ? "" : query), 45, query == null || query.isBlank() ? "§2§l🛒 SELECT ITEM" : "§2§l🔎 " + trimTitle(query));
        int from = page * 36, to = Math.min(from + 36, filtered.size());
        for (int i = from; i < to; i++) inv.setItem(i - from, button(filtered.get(i), "§a" + pretty(filtered.get(i)), List.of("§7Click to request this item")));
        inv.setItem(40, button(Material.NAME_TAG, "§e§lSEARCH", List.of("§7Search the full item list", "§7Click and type an item name in chat")));
        if (page > 0) inv.setItem(36, button(Material.ARROW, "§a§lPREVIOUS", List.of("§7Page " + page + " / " + pages)));
        if (page + 1 < pages) inv.setItem(44, button(Material.ARROW, "§a§lNEXT", List.of("§7Page " + (page + 2) + " / " + pages)));
        if (filtered.isEmpty()) inv.setItem(22, button(Material.BARRIER, "§c§lNO ITEMS FOUND", List.of("§7Try another search")));
        p.openInventory(inv);
    }

    private List<Material> filteredItems(String query) {
        if (query == null || query.isBlank()) return requestable;
        String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        List<Material> result = new ArrayList<>();
        for (Material m : requestable) if (m.name().toLowerCase(Locale.ROOT).contains(q) || pretty(m).toLowerCase(Locale.ROOT).contains(q)) result.add(m);
        return result;
    }

    private void requestSearch(Player p) {
        searchWaiting.add(p.getUniqueId()); p.closeInventory();
        p.sendMessage("§a§l📦 ORDER SEARCH"); p.sendMessage("§fType an item name in chat."); p.sendMessage("§7Type §ccancel §7to return to the item list.");
    }

    private void beginChatOrder(Player p, Material item) {
        UUID u = p.getUniqueId(); searchWaiting.remove(u);
        pending.put(u, new PendingOrder(item, Stage.AMOUNT, 0, 0, System.currentTimeMillis() + INPUT_TIMEOUT_MS)); p.closeInventory();
        p.sendMessage("§a§l📦 CREATE ORDER"); p.sendMessage("§fHow many items do you want?"); p.sendMessage("§7Type the amount in chat. Type §ccancel §7to cancel."); scheduleTimeout(u);
    }

    private void scheduleTimeout(UUID uuid) { new BukkitRunnable() { @Override public void run() { PendingOrder s = pending.get(uuid); if (s != null && s.expiresAt <= System.currentTimeMillis()) { pending.remove(uuid); Player p = Bukkit.getPlayer(uuid); if (p != null) p.sendMessage("§e📦 Order input timed out. Nothing was charged."); } } }.runTaskLater(plugin, (INPUT_TIMEOUT_MS / 50L) + 1L); }

    @EventHandler public void chat(AsyncPlayerChatEvent e) {
        Player p=e.getPlayer(); UUID u=p.getUniqueId(); String msg=e.getMessage().trim();
        if (searchWaiting.remove(u)) { e.setCancelled(true); Bukkit.getScheduler().runTask(plugin, () -> { if (msg.equalsIgnoreCase("cancel")) openItemSelection(p,0,""); else openItemSelection(p,0,msg); }); return; }
        PendingOrder state=pending.get(u); if(state==null)return; e.setCancelled(true);
        if(msg.equalsIgnoreCase("cancel")){pending.remove(u);Bukkit.getScheduler().runTask(plugin,()->openItemSelection(p,0,""));return;}
        if(state.expiresAt<System.currentTimeMillis()){pending.remove(u);p.sendMessage("§e📦 Order input timed out. Nothing was charged.");return;}
        switch(state.stage){
            case AMOUNT -> {int amount=parseInt(msg);if(amount<1||amount>2_000_000){p.sendMessage("§cEnter a whole-number amount between 1 and 2,000,000.");return;}pending.put(u,new PendingOrder(state.item,Stage.PRICE,amount,0,state.expiresAt));p.sendMessage("§a§l💰 ORDER");p.sendMessage("§fHow much will you pay per item?");p.sendMessage("§7Type the price in chat. Type §ccancel §7to cancel.");}
            case PRICE -> {long price=plugin.getEconomyManager().parseAmount(msg);if(price<1||price>1_000_000_000_000L){p.sendMessage("§cEnter a positive whole-number price (maximum $1T per item).");return;}try{Math.multiplyExact(price,(long)state.amount);}catch(ArithmeticException ex){p.sendMessage("§cThat order value is too large.");return;}pending.put(u,new PendingOrder(state.item,Stage.CONFIRM,state.amount,price,state.expiresAt));p.sendMessage("§a§l📦 ORDER CONFIRMATION");p.sendMessage("§7Item: §f"+pretty(state.item));p.sendMessage("§7Amount: §f"+fmt(state.amount));p.sendMessage("§7Price per item: §6"+money(price));p.sendMessage("§7Total reserved: §6"+money(Math.multiplyExact(price,(long)state.amount)));p.sendMessage("§eType §aCONFIRM §eto create the order, or §cCANCEL §eto abort.");}
            case CONFIRM -> {if(!msg.equalsIgnoreCase("confirm")){p.sendMessage("§eType §aCONFIRM §eto create the order, or §cCANCEL §eto abort.");return;}createOrder(p,state);}
        }
    }

    private synchronized void createOrder(Player p, PendingOrder state) {
        UUID u=p.getUniqueId(); long total;
        try{total=Math.multiplyExact(state.price,(long)state.amount);}catch(ArithmeticException ex){pending.remove(u);p.sendMessage("§cOrder value is too large.");return;}
        if(plugin.getEconomyManager().getBalance(u)<total||!plugin.getEconomyManager().withdraw(u,total)){pending.remove(u);p.sendMessage("§cYou need §f"+money(total)+" §cto reserve this order.");return;}
        Order o=new Order(UUID.randomUUID(),u,p.getName(),state.item,state.amount,state.price,total,0,Status.OPEN,System.currentTimeMillis(),System.currentTimeMillis()+ORDER_EXPIRY_MS,new ArrayList<>(),null); orders.put(o.id,o);pending.remove(u);save();
        p.sendMessage("§a§l📦 ORDER CREATED");p.sendMessage("§7Item: §f"+pretty(o.item));p.sendMessage("§7Required: §f"+fmt(o.required));p.sendMessage("§7Price: §6"+money(o.price)+" §7/ item");p.sendMessage("§7Total reserved: §6"+money(o.total));refreshOrders();
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false) public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p))return;
        Inventory top=e.getView().getTopInventory(); InventoryHolder holder=top.getHolder(); int raw=e.getRawSlot();
        if(holder instanceof OrderHolder h){e.setCancelled(true);if(raw==45){sortMode=sortMode==SortMode.MOST_PAID?SortMode.MOST_PER_ITEM:SortMode.MOST_PAID;openOrders(p,h.page);return;}if(raw==48&&h.page>0){openOrders(p,h.page-1);return;}if(raw==49){openItemSelection(p,0,"");return;}if(raw==50){List<Order> v=getVisible(p);if((h.page+1)*45<v.size())openOrders(p,h.page+1);return;}if(raw==53){p.closeInventory();return;}if(raw>=0&&raw<45){List<Order> v=getVisible(p);int idx=h.page*45+raw;if(idx<v.size()){Order o=v.get(idx);if(o.owner.equals(p.getUniqueId()))openClaim(p,o);else openDelivery(p,o);}}return;}
        if(holder instanceof ItemHolder h){e.setCancelled(true);if(raw==36&&h.page>0){openItemSelection(p,h.page-1,h.query);return;}if(raw==40){requestSearch(p);return;}if(raw==44&&((h.page+1)*36<filteredItems(h.query).size())){openItemSelection(p,h.page+1,h.query);return;}if(raw>=0&&raw<36){List<Material> f=filteredItems(h.query);int idx=h.page*36+raw;if(idx<f.size())beginChatOrder(p,f.get(idx));}return;}
        if(holder instanceof ClaimHolder ch){e.setCancelled(true);if(raw==49)claimOrder(p,ch.orderId);else if(raw==53)p.closeInventory();return;}
        DeliverySession session=deliveries.get(p.getUniqueId()); if(session==null||top!=session.inventory)return;
        if(raw>=DELIVERY_SLOTS){e.setCancelled(true);if(raw==49)finalizeDelivery(p,session);else if(raw==53)p.closeInventory();return;}
        ItemStack cursor=e.getCursor(); ItemStack clicked=e.getCurrentItem();
        if(e.isShiftClick()){
            if(e.getClickedInventory()==top){e.setCancelled(false);} else if(e.getClickedInventory()==p.getInventory()){if(!isAllowed(cursorOrClicked(clicked,cursor),orders.get(session.orderId).item)){e.setCancelled(true);p.sendMessage("§c❌ You can only deliver "+pretty(orders.get(session.orderId).item)+" to this order.");}}
        } else if(e.getClickedInventory()==top){
            if(clicked!=null&&!clicked.getType().isAir()&&!isAllowed(clicked,orders.get(session.orderId).item)){e.setCancelled(true);p.sendMessage("§c❌ You can only deliver "+pretty(orders.get(session.orderId).item)+" to this order.");}
            if(cursor!=null&&!cursor.getType().isAir()&&!isAllowed(cursor,orders.get(session.orderId).item)){e.setCancelled(true);p.sendMessage("§c❌ You can only deliver "+pretty(orders.get(session.orderId).item)+" to this order.");}
        } else if(e.getClickedInventory()==p.getInventory() && cursor!=null&&!cursor.getType().isAir()&&!isAllowed(cursor,orders.get(session.orderId).item)){e.setCancelled(true);p.sendMessage("§c❌ You can only deliver "+pretty(orders.get(session.orderId).item)+" to this order.");}
        if(!e.isCancelled()) Bukkit.getScheduler().runTask(plugin,()->normalizeDelivery(p,session));
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false) public void drag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;DeliverySession s=deliveries.get(p.getUniqueId());if(s==null||e.getView().getTopInventory()!=s.inventory)return;
        boolean bad=false;for(int raw:e.getRawSlots())if(raw>=DELIVERY_SLOTS){bad=true;break;}if(bad){e.setCancelled(true);return;}
        for(ItemStack x:e.getNewItems().values())if(!isAllowed(x,orders.get(s.orderId).item)){e.setCancelled(true);p.sendMessage("§c❌ You can only deliver "+pretty(orders.get(s.orderId).item)+" to this order.");return;}
        Bukkit.getScheduler().runTask(plugin,()->normalizeDelivery(p,s));
    }

    private ItemStack cursorOrClicked(ItemStack clicked,ItemStack cursor){return clicked!=null&&!clicked.getType().isAir()?clicked:cursor;}
    private boolean isAllowed(ItemStack item,Material expected){return item==null||item.getType().isAir()||item.getType()==expected;}

    private void openDelivery(Player p,Order order){
        if(order.owner.equals(p.getUniqueId())){p.sendMessage("§cYou cannot fulfill your own order.");return;}if(isExpired(order)){p.sendMessage("§cThis order has expired.");return;}if(order.status!=Status.OPEN||order.remaining()<=0){p.sendMessage("§cThat order is already completed.");return;}
        Inventory inv=Bukkit.createInventory(new DeliveryHolder(order.id),54,"§2§l📦 DELIVER ORDER #"+shortId(order.id));
        for(int i=45;i<54;i++)inv.setItem(i,button(Material.GRAY_STAINED_GLASS_PANE,"§r",List.of()));
        inv.setItem(47,progressItem(order,0));inv.setItem(49,button(Material.EMERALD_BLOCK,"§a§l📦 DELIVER ITEMS",List.of("§7Place the items you want to deliver here","§eClick to submit this delivery")));inv.setItem(53,button(Material.BARRIER,"§c§l✕ CANCEL",List.of("§7Return all placed items")));
        DeliverySession s=new DeliverySession(order.id,inv);deliveries.put(p.getUniqueId(),s);p.openInventory(inv);updateDeliveryDisplay(p,s);
    }

    private void normalizeDelivery(Player p,DeliverySession s){
        if(deliveries.get(p.getUniqueId())!=s)return;Order order=orders.get(s.orderId);if(order==null){p.closeInventory();return;}
        long allowed=Math.max(0,order.remaining());long kept=0;
        for(int slot=0;slot<DELIVERY_SLOTS;slot++){
            ItemStack stack=s.inventory.getItem(slot);if(stack==null||stack.getType().isAir())continue;
            if(stack.getType()!=order.item){s.inventory.setItem(slot,null);giveOrDrop(p,stack);continue;}
            long take=Math.min(stack.getAmount(),Math.max(0,allowed-kept));int overflow=stack.getAmount()-(int)take;
            if(overflow>0){stack.setAmount((int)take);giveOrDrop(p,new ItemStack(order.item,overflow));}
            kept+=take;if(take<=0)s.inventory.setItem(slot,null);
        }
        updateDeliveryDisplay(p,s);
    }

    private ItemStack progressItem(Order o,long current){double pct=o.required<=0?1.0:(double)(o.delivered+current)/o.required;int bars=20;int filled=(int)Math.floor(Math.max(0,Math.min(1,pct))*bars);StringBuilder bar=new StringBuilder();for(int i=0;i<bars;i++)bar.append(i<filled?"§a█":"§8░");return button(Material.PAPER,"§a§l📦 DELIVERY",List.of("§7Your delivery: §f"+fmt(current)+" / "+fmt(o.remaining()),"§7Remaining after delivery: §f"+fmt(Math.max(0,o.remaining()-current)),"",bar+" §f"+String.format(Locale.US,"%.1f%%",pct*100)));}

    private long stagedCount(DeliverySession s,Material item){long n=0;for(int i=0;i<DELIVERY_SLOTS;i++){ItemStack x=s.inventory.getItem(i);if(x!=null&&x.getType()==item)n+=x.getAmount();}return n;}
    private void updateDeliveryDisplay(Player p,DeliverySession s){Order o=orders.get(s.orderId);if(o==null)return;long count=stagedCount(s,o.item);s.inventory.setItem(47,progressItem(o,count));s.inventory.setItem(49,button(Material.EMERALD_BLOCK,"§a§l📦 DELIVER ITEMS",List.of("§7Place the items you want to deliver here","§7Currently staged: §f"+fmt(count),"§7Remaining: §f"+fmt(Math.max(0,o.remaining()-count)),"","§eClick to submit this delivery")));}

    private synchronized void finalizeDelivery(Player p,DeliverySession session){
        UUID u=p.getUniqueId();if(!transactionLocks.add(u))return;try{
            Order o=orders.get(session.orderId);if(o==null||o.status!=Status.OPEN||isExpired(o)){p.sendMessage("§cThis order is no longer accepting deliveries.");return;}
            long accepted=Math.min(stagedCount(session,o.item),o.remaining());if(accepted<=0){p.sendMessage("§cPlace some "+pretty(o.item)+" in the delivery area first.");return;}
            long payment=safeMultiply(accepted,o.price);if(payment<=0){p.sendMessage("§cThis delivery could not be valued safely.");return;}
            List<ItemStack> snapshot=takeFromDelivery(session.inventory,o.item,accepted);if(snapshot.isEmpty()||snapshot.stream().mapToLong(ItemStack::getAmount).sum()!=accepted){restoreToDelivery(session.inventory,snapshot);return;}
            if(!plugin.getEconomyManager().deposit(u,payment)){restoreToDelivery(session.inventory,snapshot);p.sendMessage("§cPayment failed; nothing was completed.");return;}
            for(ItemStack x:snapshot)addToStorage(o.storage,x.clone());
            o.delivered+=accepted;o.status=o.remaining()==0?Status.COMPLETED:Status.OPEN;o.lastSeller=u;save();
            p.sendMessage("§a📦 Accepted §f"+fmt(accepted)+"x "+pretty(o.item)+" §afor §6"+money(payment)+"§a.");
            p.sendMessage("§7The items are stored in the order. The buyer must claim them from /order.");
            if(o.status==Status.COMPLETED)p.sendMessage("§a§l✅ ORDER COMPLETED §7("+fmt(o.required)+" / "+fmt(o.required)+")");
            deliveries.remove(u);p.closeInventory();refreshOrders();
        }finally{transactionLocks.remove(u);}
    }

    private List<ItemStack> takeFromDelivery(Inventory inv,Material material,long amount){List<ItemStack> out=new ArrayList<>();long left=amount;for(int slot=0;slot<DELIVERY_SLOTS&&left>0;slot++){ItemStack stack=inv.getItem(slot);if(stack==null||stack.getType()!=material)continue;int take=(int)Math.min((long)stack.getAmount(),left);out.add(new ItemStack(material,take));stack.setAmount(stack.getAmount()-take);left-=take;}return out;}
    private void restoreToDelivery(Inventory inv,List<ItemStack> items){for(ItemStack item:items){Map<Integer,ItemStack> extra=inv.addItem(item.clone());if(!extra.isEmpty()){Player owner=findDeliveryOwner(inv);if(owner!=null)for(ItemStack x:extra.values())giveOrDrop(owner,x);}}}
    private Player findDeliveryOwner(Inventory inv){for(Map.Entry<UUID,DeliverySession> e:deliveries.entrySet())if(e.getValue().inventory==inv)return Bukkit.getPlayer(e.getKey());return null;}


    @EventHandler public void close(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p))return;DeliverySession s=deliveries.get(p.getUniqueId());if(s==null||e.getInventory()!=s.inventory)return;deliveries.remove(p.getUniqueId());returnDeliveryItems(p,s);}
    @EventHandler public void quit(PlayerQuitEvent e){UUID u=e.getPlayer().getUniqueId();pending.remove(u);searchWaiting.remove(u);DeliverySession s=deliveries.remove(u);if(s!=null)returnDeliveryItems(e.getPlayer(),s);}

    private void returnDeliveryItems(Player p,DeliverySession s){for(int i=0;i<DELIVERY_SLOTS;i++){ItemStack x=s.inventory.getItem(i);if(x==null||x.getType().isAir())continue;s.inventory.setItem(i,null);giveOrDrop(p,x);}}
    private void giveOrDrop(Player p,ItemStack item){if(item==null||item.getType().isAir()||item.getAmount()<=0)return;for(ItemStack x:p.getInventory().addItem(item.clone()).values())p.getWorld().dropItemNaturally(p.getLocation(),x);}

    private void refreshOrders(){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof OrderHolder h)openOrders(p,h.page);}

    private synchronized void load(){orders.clear();if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);var root=y.getConfigurationSection("orders");if(root==null)return;for(String k:root.getKeys(false))try{String path="orders."+k;UUID id=UUID.fromString(k),owner=UUID.fromString(y.getString(path+".buyer"));Material item=Material.matchMaterial(y.getString(path+".item","AIR"));if(item==null||item.isAir())continue;long required=Math.max(1,y.getLong(path+".required"));long price=Math.max(1,y.getLong(path+".price-per-item"));long total=Math.max(1,y.getLong(path+".total-price"));long delivered=Math.max(0,Math.min(required,y.getLong(path+".delivered")));Status status=Status.valueOf(y.getString(path+".status","OPEN"));long created=y.getLong(path+".created-time",System.currentTimeMillis());long expires=y.getLong(path+".expiry-time",created+ORDER_EXPIRY_MS);List<ItemStack> storage=new ArrayList<>();List<?> raw=y.getList(path+".storage",List.of());for(Object x:raw)if(x instanceof ItemStack itemStack&&!itemStack.getType().isAir())storage.add(itemStack.clone());UUID seller=null;String sellerText=y.getString(path+".last-seller");if(sellerText!=null)try{seller=UUID.fromString(sellerText);}catch(Exception ignored){}orders.put(id,new Order(id,owner,y.getString(path+".buyer-name","Unknown"),item,required,price,total,delivered,status,created,expires,storage,seller));}catch(Exception ex){plugin.getLogger().warning("Skipped invalid order: "+k);}}
    private synchronized void save(){YamlConfiguration y=new YamlConfiguration();for(Order o:orders.values()){String p="orders."+o.id;y.set(p+".buyer",o.owner.toString());y.set(p+".buyer-name",o.ownerName);y.set(p+".item",o.item.name());y.set(p+".required",o.required);y.set(p+".price-per-item",o.price);y.set(p+".total-price",o.total);y.set(p+".delivered",o.delivered);y.set(p+".remaining",o.remaining());y.set(p+".status",o.status.name());y.set(p+".created-time",o.createdTime);y.set(p+".expiry-time",o.expiresAt);if(o.lastSeller!=null)y.set(p+".last-seller",o.lastSeller.toString());y.set(p+".storage",new ArrayList<>(o.storage));}try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save orders.yml: "+ex.getMessage());}}


    private boolean isExpired(Order o){return o.expiresAt>0 && System.currentTimeMillis()>=o.expiresAt && o.remaining()>0 && o.status==Status.OPEN;}
    private String remainingTime(long at){long s=Math.max(0,(at-System.currentTimeMillis())/1000);long d=s/86400;s%=86400;long h=s/3600;s%=3600;long m=s/60;return d+"d "+h+"h "+m+"m";}
    private long storedCount(Order o){long n=0;for(ItemStack x:o.storage)n+=x.getAmount();return n;}
    private void addToStorage(List<ItemStack> storage,ItemStack incoming){int max=Math.max(1,incoming.getMaxStackSize());for(ItemStack existing:storage){if(existing.isSimilar(incoming)&&existing.getAmount()<max){int move=Math.min(max-existing.getAmount(),incoming.getAmount());existing.setAmount(existing.getAmount()+move);incoming.setAmount(incoming.getAmount()-move);if(incoming.getAmount()<=0)return;}}while(incoming.getAmount()>0){int take=Math.min(max,incoming.getAmount());storage.add(new ItemStack(incoming.getType(),take));incoming.setAmount(incoming.getAmount()-take);}}
    private void expireOrders(){boolean changed=false;for(Order o:orders.values()){if(!isExpired(o))continue;long refund=safeMultiply(o.remaining(),o.price);if(refund>0)plugin.getEconomyManager().deposit(o.owner,refund);o.status=Status.EXPIRED;changed=true;plugin.getLogger().info("Order "+o.id+" expired; refunded "+refund+" to buyer.");}if(changed){save();refreshOrders();}}

    private void openClaim(Player p,Order o){
        if(!o.owner.equals(p.getUniqueId())||o.storage.isEmpty()){p.sendMessage("§cThere are no stored items to claim.");return;}
        Inventory inv=Bukkit.createInventory(new ClaimHolder(o.id),54,"§2§l📦 CLAIM ORDER #"+shortId(o.id));
        for(int i=0;i<45&&i<o.storage.size();i++)inv.setItem(i,o.storage.get(i).clone());
        inv.setItem(49,button(Material.CHEST,"§a§l📦 CLAIM ORDER",List.of("§7Stored: §f"+fmt(storedCount(o))+" items","§eClick to transfer to your inventory")));
        inv.setItem(53,button(Material.BARRIER,"§c§l✕ CLOSE",List.of("§7Leave the order stored")));
        p.openInventory(inv);
    }
    private synchronized void claimOrder(Player p,UUID id){Order o=orders.get(id);if(o==null||!o.owner.equals(p.getUniqueId())||o.storage.isEmpty())return;if(!hasSpaceForItems(p,o.storage)){p.sendMessage("§cYou do not have enough inventory space to claim this order.");return;}List<ItemStack> snapshot=new ArrayList<>();for(ItemStack x:o.storage)snapshot.add(x.clone());o.storage.clear();Map<Integer,ItemStack> extra=p.getInventory().addItem(snapshot.toArray(new ItemStack[0]));if(!extra.isEmpty()){o.storage.addAll(snapshot);for(ItemStack x:extra.values())giveOrDrop(p,x);p.sendMessage("§cClaim failed safely; the order remains stored.");return;}save();p.sendMessage("§a📦 Order #"+shortId(o.id)+" claimed successfully.");p.closeInventory();refreshOrders();}
    private boolean hasSpaceForItems(Player p,List<ItemStack> items){for(ItemStack x:items)if(!hasSpace(p,x.getType(),x.getAmount()))return false;return true;}
    private boolean hasSpace(Player p,Material material,long amount){long remaining=amount;for(ItemStack stack:p.getInventory().getStorageContents()){if(remaining<=0)return true;if(stack==null||stack.getType().isAir())remaining-=material.getMaxStackSize();else if(stack.getType()==material)remaining-=Math.max(0,stack.getMaxStackSize()-stack.getAmount());}return remaining<=0;}
    private ItemStack button(Material m,String n,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();if(meta!=null){meta.setDisplayName(n);meta.setLore(lore);i.setItemMeta(meta);}return i;}
    private ItemStack button(Material m,String n,List<String> lore,boolean unused){return button(m,n,lore);}
    private String money(long n){return plugin.getEconomyManager().format(n);}
    private String fmt(long n){return String.format(Locale.US,"%,d",Math.max(0,n));}
    private String pretty(Material m){String s=m.name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder b=new StringBuilder();for(String w:s.split(" "))if(!w.isEmpty())b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');return b.toString().trim();}
    private String trimTitle(String s){return s.length()>20?s.substring(0,20):s;}
    private int parseInt(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return -1;}}
    private long safeMultiply(long a,long b){if(a<=0||b<=0||a>Long.MAX_VALUE/b)return 0;return a*b;}
    private String shortId(UUID id){return id.toString().substring(0,8);}

    private enum Stage{AMOUNT,PRICE,CONFIRM}
    private enum SortMode{MOST_PAID("MOST PAID"),MOST_PER_ITEM("MOST PER ITEM");final String label;SortMode(String label){this.label=label;}}
    private enum Status{OPEN,COMPLETED,EXPIRED}
    private static final class PendingOrder{final Material item;final Stage stage;final int amount;final long price,expiresAt;PendingOrder(Material i,Stage s,int a,long p,long e){item=i;stage=s;amount=a;price=p;expiresAt=e;}}
    private static final class Order{final UUID id,owner;final String ownerName;final Material item;final long required,price,total,createdTime,expiresAt;long delivered;Status status;final List<ItemStack> storage;UUID lastSeller;Order(UUID i,UUID o,String n,Material m,long r,long p,long t,long d,Status s,long c,long e,List<ItemStack> st,UUID seller){id=i;owner=o;ownerName=n;item=m;required=r;price=p;total=t;delivered=d;status=s;createdTime=c;expiresAt=e;storage=st;lastSeller=seller;}long remaining(){return Math.max(0,required-delivered);}}
    private static final class DeliverySession{final UUID orderId;final Inventory inventory;DeliverySession(UUID id,Inventory i){orderId=id;inventory=i;}}
    private static final class OrderHolder implements InventoryHolder{final int page;OrderHolder(int p){page=p;}public Inventory getInventory(){return null;}}
    private static final class ItemHolder implements InventoryHolder{final int page;final String query;ItemHolder(int p,String q){page=p;query=q;}public Inventory getInventory(){return null;}}
    private static final class DeliveryHolder implements InventoryHolder{final UUID orderId;DeliveryHolder(UUID id){orderId=id;}public Inventory getInventory(){return null;}}
    private static final class ClaimHolder implements InventoryHolder{final UUID orderId;ClaimHolder(UUID id){orderId=id;}public Inventory getInventory(){return null;}}
}