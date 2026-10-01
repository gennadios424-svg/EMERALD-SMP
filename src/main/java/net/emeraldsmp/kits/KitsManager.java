package net.emeraldsmp.kits;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class KitsManager implements Listener {
    public static final String TITLE="§2§l🎁 EMERALD KITS";
    private final EmeraldSMP plugin;
    private final Map<UUID,Map<String,Long>> cooldowns=new ConcurrentHashMap<>();
    private final Map<UUID,String> openKit=new HashMap<>();

    public KitsManager(EmeraldSMP plugin){this.plugin=plugin;}

    public void load(){
        cooldowns.clear();
        ConfigurationSection root=plugin.getConfig().getConfigurationSection("kits");
        if(root==null)return;
        for(String role:root.getKeys(false)){
            if(role.equalsIgnoreCase("cooldowns"))continue;
        }
    }

    public void save(){
        for(var e:cooldowns.entrySet())for(var k:e.getValue().entrySet())plugin.getConfig().set("kit-data."+e.getKey()+"."+k.getKey(),k.getValue());
        plugin.saveConfig();
    }

    public void loadPersisted(){
        ConfigurationSection root=plugin.getConfig().getConfigurationSection("kit-data");
        if(root==null)return;
        for(String uuid:root.getKeys(false))try{
            UUID u=UUID.fromString(uuid);Map<String,Long> map=new HashMap<>();
            for(String kit:Objects.requireNonNull(root.getConfigurationSection(uuid)).getKeys(false))map.put(kit,root.getLong(uuid+"."+kit));
            cooldowns.put(u,map);
        }catch(Exception ignored){}
    }

    public void open(Player p){
        Inventory inv=Bukkit.createInventory(null,27,TITLE);
        RoleManager.Role current=plugin.getRoleManager().get(p);
        int slot=0;
        for(RoleManager.Role r:RoleManager.Role.values()){
            String key=r.label().toLowerCase(Locale.ROOT);
            Material mat=material(r);
            ItemStack item=new ItemStack(mat);ItemMeta m=item.getItemMeta();
            m.setDisplayName(r.color()+"🎁 "+r.label()+" KIT");
            List<String> lore=new ArrayList<>();
            if(canClaim(p,r)){
                if(isReady(p,key))lore.add("§a✔ READY TO CLAIM");
                else lore.add("§cAvailable in: §f"+formatRemaining(remaining(p,key)));
            }else lore.add("§8Requires role: "+r.color()+r.label());
            if(r==current)lore.add("§7Your role: "+r.color()+r.label());
            m.setLore(lore);item.setItemMeta(m);inv.setItem(slot++,item);
        }
        p.openInventory(inv);
    }

    public boolean canClaim(Player p,RoleManager.Role required){
        return required==RoleManager.Role.MEMBER || plugin.getRoleManager().get(p)==required;
    }

    public boolean isReady(Player p,String kit){return remaining(p,kit)<=0;}
    public long remaining(Player p,String kit){long until=cooldowns.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(kit,0L);return Math.max(0L,until-System.currentTimeMillis());}

    public boolean claim(Player p,String kit){
        RoleManager.Role required=RoleManager.Role.parse(kit);
        if(required==null||!canClaim(p,required))return false;
        if(!isReady(p,kit))return false;
        ConfigurationSection sec=plugin.getConfig().getConfigurationSection("kits."+kit.toLowerCase(Locale.ROOT));
        if(sec==null)return false;
        List<String> specs=sec.getStringList("items");
        List<ItemStack> rewards=new ArrayList<>();
        for(String spec:specs){
            String[] parts=spec.split(":",2);if(parts.length!=2)continue;
            Material mat=Material.matchMaterial(parts[0]);if(mat==null)continue;
            int amount;try{amount=Integer.parseInt(parts[1]);}catch(NumberFormatException e){continue;}
            if(amount>0)rewards.add(new ItemStack(mat,Math.min(amount,mat.getMaxStackSize())));
        }
        if(rewards.isEmpty())return false;
        for(ItemStack reward:rewards){
            HashMap<Integer,ItemStack> leftovers=p.getInventory().addItem(reward);
            for(ItemStack left:leftovers.values())p.getWorld().dropItemNaturally(p.getLocation(),left);
        }
        long cd=parseDuration(plugin.getConfig().getString("kits."+kit.toLowerCase(Locale.ROOT)+".cooldown","24h"));
        cooldowns.computeIfAbsent(p.getUniqueId(),x->new HashMap<>()).put(kit,Long.MAX_VALUE-cd+System.currentTimeMillis());
        // Correct overflow-safe absolute expiry:
        long expiry=System.currentTimeMillis()+Math.max(0L,cd);
        cooldowns.get(p.getUniqueId()).put(kit,expiry);
        save();
        return true;
    }

    private long parseDuration(String s){
        if(s==null)return 86400000L;String v=s.trim().toLowerCase(Locale.ROOT);
        try{
            if(v.endsWith("d"))return Long.parseLong(v.substring(0,v.length()-1))*86400000L;
            if(v.endsWith("h"))return Long.parseLong(v.substring(0,v.length()-1))*3600000L;
            if(v.endsWith("m"))return Long.parseLong(v.substring(0,v.length()-1))*60000L;
            if(v.endsWith("s"))return Long.parseLong(v.substring(0,v.length()-1))*1000L;
            return Long.parseLong(v)*1000L;
        }catch(Exception e){return 86400000L;}
    }

    private String formatRemaining(long ms){long sec=ms/1000;long d=sec/86400;sec%=86400;long h=sec/3600;sec%=3600;long m=sec/60;sec%=60;if(d>0)return d+"d "+h+"h";if(h>0)return h+"h "+m+"m";return m+"m "+sec+"s";}

    private Material material(RoleManager.Role r){return switch(r){case OWNER->Material.REDSTONE_BLOCK;case DEV->Material.DIAMOND;case MOD->Material.IRON_BLOCK;case MEDIA->Material.PINK_WOOL;case EMERALD->Material.EMERALD;case MVP->Material.GOLD_BLOCK;case VIP->Material.GOLD_INGOT;case MEMBER->Material.IRON_INGOT;};}

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!TITLE.equals(e.getView().getTitle()))return;
        e.setCancelled(true);
        if(e.getClickedInventory()!=e.getView().getTopInventory()||e.getRawSlot()<0||e.getRawSlot()>=RoleManager.Role.values().length)return;
        RoleManager.Role r=RoleManager.Role.values()[e.getRawSlot()];
        String kit=r.label().toLowerCase(Locale.ROOT);
        if(!canClaim(p,r)){p.sendMessage("§cYou do not have the "+r.label()+" role.");return;}
        if(!isReady(p,kit)){p.sendMessage("§cKit is on cooldown. §7"+formatRemaining(remaining(p,kit)));return;}
        if(claim(p,kit))p.sendMessage("§a🎁 "+r.label()+" Kit claimed!");
        else p.sendMessage("§cThe kit could not be claimed.");
        open(p);
    }

    @EventHandler public void drag(org.bukkit.event.inventory.InventoryDragEvent e){if(TITLE.equals(e.getView().getTitle()))e.setCancelled(true);}
    @EventHandler public void close(org.bukkit.event.inventory.InventoryCloseEvent e){openKit.remove(e.getPlayer().getUniqueId());}
}
