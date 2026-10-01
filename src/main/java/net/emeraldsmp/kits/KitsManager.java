package net.emeraldsmp.kits;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class KitsManager implements Listener {
    public static final String TITLE="§2§l🎁 KIT COLLECTION";
    private static final String PREVIEW_PREFIX="§2§l🎁 ";
    private final EmeraldSMP plugin;
    private final Map<UUID,Map<String,Long>> cooldowns=new ConcurrentHashMap<>();

    public KitsManager(EmeraldSMP plugin){this.plugin=plugin;}

    public void loadPersisted(){
        cooldowns.clear();
        ConfigurationSection root=plugin.getConfig().getConfigurationSection("kit-data");
        if(root==null)return;
        for(String uuid:root.getKeys(false))try{
            UUID u=UUID.fromString(uuid);
            ConfigurationSection sec=root.getConfigurationSection(uuid);
            if(sec==null)continue;
            Map<String,Long> map=new HashMap<>();
            for(String kit:sec.getKeys(false))map.put(kit,sec.getLong(kit));
            cooldowns.put(u,map);
        }catch(Exception ignored){}
    }

    public void load(){loadPersisted();}

    public synchronized void save(){
        for(var e:cooldowns.entrySet())
            for(var k:e.getValue().entrySet())
                plugin.getConfig().set("kit-data."+e.getKey()+"."+k.getKey(),k.getValue());
        plugin.saveConfig();
    }

    public void open(Player p){
        Inventory inv=Bukkit.createInventory(null,45,TITLE);
        RoleManager.Role[] roles=RoleManager.Role.values();
        int[] slots={10,12,14,19,21,23,28,30};
        for(int i=0;i<roles.length;i++){
            RoleManager.Role r=roles[i];
            ItemStack item=new ItemStack(material(r));
            ItemMeta m=item.getItemMeta();
            m.setDisplayName(r.color()+"§l✦ "+r.label()+" KIT ✦");
            List<String> lore=new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━");
            lore.add("§7A premium "+r.color()+r.label()+" §7reward package");
            lore.add("");
            if(canClaim(p,r)){
                if(isReady(p,r.label().toLowerCase(Locale.ROOT))) lore.add("§a✔ READY — Click to preview");
                else lore.add("§c⏳ COOLDOWN: §f"+formatRemaining(remaining(p,r.label().toLowerCase(Locale.ROOT))));
            }else lore.add("§c🔒 Requires "+r.color()+"§l✦ "+r.label()+" ✦");
            if(plugin.getRoleManager().get(p)==r)lore.add("§e★ YOUR CURRENT RANK");
            lore.add("");
            lore.add("§e▶ Click to preview");
            m.setLore(lore);
            item.setItemMeta(m);
            inv.setItem(slots[i],item);
        }
        fillBorders(inv);
        p.openInventory(inv);
    }

    private void fillBorders(Inventory inv){
        ItemStack pane=new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m=pane.getItemMeta();m.setDisplayName("§r");pane.setItemMeta(m);
        for(int i=0;i<45;i++)if(inv.getItem(i)==null)inv.setItem(i,pane.clone());
    }

    private Material material(RoleManager.Role r){
        return switch(r){
            case OWNER->Material.NETHER_STAR; case DEV->Material.COMMAND_BLOCK; case MOD->Material.SHIELD;
            case MEDIA->Material.SPYGLASS; case EMERALD->Material.EMERALD_BLOCK; case MVP->Material.DIAMOND;
            case VIP->Material.GOLD_BLOCK; case MEMBER->Material.IRON_INGOT;
        };
    }

    public boolean canClaim(Player p,RoleManager.Role required){
        return required==RoleManager.Role.MEMBER || plugin.getRoleManager().get(p)==required;
    }
    public boolean isReady(Player p,String kit){return remaining(p,kit)<=0;}
    public long remaining(Player p,String kit){
        long until=cooldowns.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(kit,0L);
        return Math.max(0L,until-System.currentTimeMillis());
    }

    private ConfigurationSection section(String kit){
        return plugin.getConfig().getConfigurationSection("kits."+kit.toLowerCase(Locale.ROOT));
    }

    public void preview(Player p,RoleManager.Role role){
        String kit=role.label().toLowerCase(Locale.ROOT);
        Inventory inv=Bukkit.createInventory(null,36,PREVIEW_PREFIX+role.color()+"§l✦ "+role.label()+" KIT ✦");
        ConfigurationSection sec=section(kit);
        List<String> specs=sec==null?List.of():sec.getStringList("items");
        int slot=10;
        for(String spec:specs){
            ItemStack reward=parseItem(spec);
            if(reward==null)continue;
            ItemMeta m=reward.getItemMeta();
            if(m!=null){
                List<String> lore=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();
                lore.add("");
                lore.add("§7Kit reward");
                m.setLore(lore);reward.setItemMeta(m);
            }
            inv.setItem(slot,reward);
            slot++;
            if(slot==17)slot=19;
            if(slot>=26)break;
        }
        inv.setItem(30,button(Material.EMERALD,"§a§l🎁 CLAIM KIT",List.of(
            "§7Claim the "+role.color()+"§l✦ "+role.label()+" ✦ §7kit",
            "",
            canClaim(p,role)?"§a✔ Your rank allows this kit":"§c✖ You do not have this rank",
            isReady(p,kit)?"§a✔ Ready now":"§c⏳ "+formatRemaining(remaining(p,kit))
        )));
        inv.setItem(32,button(Material.ARROW,"§e← BACK",List.of("§7Return to Kit Collection")));
        p.openInventory(inv);
    }

    private ItemStack parseItem(String spec){
        if(spec==null)return null;
        String[] parts=spec.split(":",2);
        if(parts.length!=2)return null;
        Material mat=Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
        if(mat==null||mat.isAir())return null;
        try{
            int amount=Integer.parseInt(parts[1].trim());
            if(amount<=0)return null;
            return new ItemStack(mat,Math.min(amount,mat.getMaxStackSize()));
        }catch(NumberFormatException e){return null;}
    }

    public boolean claim(Player p,String kit){
        RoleManager.Role required=RoleManager.Role.parse(kit);
        if(required==null||!canClaim(p,required)||!isReady(p,kit))return false;
        ConfigurationSection sec=section(kit);
        if(sec==null)return false;
        List<ItemStack> rewards=new ArrayList<>();
        for(String spec:sec.getStringList("items")){
            ItemStack item=parseItem(spec);
            if(item!=null)rewards.add(item);
        }
        if(rewards.isEmpty())return false;
        // Give first, and never delete overflow: leftovers are dropped safely at the player's location.
        for(ItemStack reward:rewards){
            HashMap<Integer,ItemStack> leftovers=p.getInventory().addItem(reward);
            for(ItemStack left:leftovers.values())p.getWorld().dropItemNaturally(p.getLocation(),left);
        }
        long cd=parseDuration(sec.getString("cooldown","24h"));
        cooldowns.computeIfAbsent(p.getUniqueId(),x->new HashMap<>()).put(kit,System.currentTimeMillis()+Math.max(0L,cd));
        save();
        return true;
    }

    private long parseDuration(String s){
        if(s==null)return 86400000L;
        String v=s.trim().toLowerCase(Locale.ROOT);
        try{
            if(v.endsWith("d"))return Long.parseLong(v.substring(0,v.length()-1))*86400000L;
            if(v.endsWith("h"))return Long.parseLong(v.substring(0,v.length()-1))*3600000L;
            if(v.endsWith("m"))return Long.parseLong(v.substring(0,v.length()-1))*60000L;
            if(v.endsWith("s"))return Long.parseLong(v.substring(0,v.length()-1))*1000L;
            return Long.parseLong(v)*1000L;
        }catch(Exception e){return 86400000L;}
    }

    private String formatRemaining(long ms){
        long sec=ms/1000,d=sec/86400;sec%=86400;long h=sec/3600;sec%=3600;long m=sec/60;sec%=60;
        if(d>0)return d+"d "+h+"h";if(h>0)return h+"h "+m+"m";return m+"m "+sec+"s";
    }

    private ItemStack button(Material mat,String name,List<String> lore){
        ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(name);m.setLore(lore);i.setItemMeta(m);return i;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        if(TITLE.equals(title)){
            e.setCancelled(true);
            if(e.getClickedInventory()!=e.getView().getTopInventory())return;
            int[] slots={10,12,14,19,21,23,28,30};
            for(int i=0;i<slots.length;i++)if(e.getRawSlot()==slots[i]){
                RoleManager.Role r=RoleManager.Role.values()[i];
                preview(p,r);return;
            }
            return;
        }
        if(!title.startsWith(PREVIEW_PREFIX))return;
        e.setCancelled(true);
        if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        RoleManager.Role role=null;
        for(RoleManager.Role r:RoleManager.Role.values())if(title.contains(r.label())){role=r;break;}
        if(role==null)return;
        if(e.getRawSlot()==32){open(p);return;}
        if(e.getRawSlot()==30){
            String kit=role.label().toLowerCase(Locale.ROOT);
            if(!canClaim(p,role)){p.sendMessage("§c🔒 You do not have the "+role.label()+" role.");return;}
            if(!isReady(p,kit)){p.sendMessage("§c⏳ Kit cooldown: "+formatRemaining(remaining(p,kit)));return;}
            if(claim(p,kit))p.sendMessage("§a§l🎁 "+role.label()+" KIT CLAIMED!");
            else p.sendMessage("§cThe kit could not be claimed.");
            open(p);
        }
    }

    @EventHandler public void drag(InventoryDragEvent e){
        String t=e.getView().getTitle();
        if(TITLE.equals(t)||t.startsWith(PREVIEW_PREFIX))e.setCancelled(true);
    }
}
