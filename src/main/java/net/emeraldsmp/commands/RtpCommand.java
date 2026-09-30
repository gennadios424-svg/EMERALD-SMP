package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpCommand implements org.bukkit.command.CommandExecutor, Listener {
    private static final String GUI_TITLE="§2💚 Emerald SMP §8» §aRTP";
    private static final int MAX_ATTEMPTS=80;
    private final EmeraldSMP plugin;
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private final Map<UUID,BukkitTask> pending=new HashMap<>();

    public RtpCommand(EmeraldSMP plugin){this.plugin=plugin;}
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender,org.bukkit.command.Command command,String label,String[] args){
        if(!(sender instanceof Player p)){plugin.getMessageService().send(sender,"&cOnly players can use /rtp.");return true;}
        if(pending.containsKey(p.getUniqueId())){plugin.getMessageService().send(p,"&eYou already have an RTP teleport pending.");return true;}
        long cooldown=Math.max(0L,plugin.getConfig().getLong("rtp.cooldown",60L));
        long last=cooldowns.getOrDefault(p.getUniqueId(),0L), remainingMs=last+cooldown*1000L-System.currentTimeMillis();
        if(!p.hasPermission("emerald.rtp.bypass")&&remainingMs>0){
            long sec=(remainingMs+999L)/1000L;
            plugin.getMessageService().send(p,plugin.getConfig().getString("messages.rtp.cooldown","&cYou must wait &f%time%&c.").replace("%time%",formatTime(sec)));
            return true;
        }
        openWorldGui(p); return true;
    }
    private void openWorldGui(Player p){
        Inventory inv=Bukkit.createInventory(new RtpHolder(),27,GUI_TITLE);
        ItemStack filler=item(Material.GRAY_STAINED_GLASS_PANE,"§8");
        for(int i=0;i<27;i++)inv.setItem(i,filler);
        addWorldOption(inv,11,Material.GRASS_BLOCK,"§a🌎 Overworld");
        addWorldOption(inv,13,Material.NETHERRACK,"§c🔥 Nether");
        addWorldOption(inv,15,Material.END_STONE,"§5🟣 End");
        p.openInventory(inv);
    }
    private void addWorldOption(Inventory inv,int slot,Material mat,String name){
        ItemStack i=item(mat,name);ItemMeta m=i.getItemMeta();
        if(m!=null)m.setLore(List.of("§7Click to choose this world"));i.setItemMeta(m);inv.setItem(slot,i);
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof RtpHolder))return;
        e.setCancelled(true);
        int slot=e.getRawSlot(); if(slot<0||slot>=27)return;
        String name=switch(slot){case 11->configuredWorld("world","world");case 13->configuredWorld("nether","world_nether");case 15->configuredWorld("end","world_the_end");default->null;};
        if(name==null)return;
        World w=Bukkit.getWorld(name);
        if(w==null){plugin.getMessageService().send(p,"&cThat RTP world is not loaded.");return;}
        if(!isWorldAllowed(w)){plugin.getMessageService().send(p,"&cRTP is not enabled for that world.");return;}
        p.closeInventory(); startTeleport(p,w);
    }
    private String configuredWorld(String key,String fallback){
        String v=plugin.getConfig().getString("rtp.worlds."+key,"");
        if(v!=null&&!v.isBlank())return v;
        List<String> list=plugin.getConfig().getStringList("rtp.worlds");
        if(!list.isEmpty()){int idx=key.equals("world")?0:key.equals("nether")?1:2;if(idx<list.size())return list.get(idx);}
        return fallback;
    }
    private boolean isWorldAllowed(World w){
        List<String> list=plugin.getConfig().getStringList("rtp.worlds");
        if(list.isEmpty())return true;
        return list.stream().anyMatch(s->s.equalsIgnoreCase(w.getName()));
    }
    private void startTeleport(Player p,World world){
        UUID u=p.getUniqueId();
        int seconds=Math.max(1,plugin.getConfig().getInt("rtp.countdown",5));
        cancelPending(u,false);

        // IMPORTANT: never synchronously search/generate chunks on the server thread.
        // Paper's async chunk API lets world generation/loading happen off-thread,
        // while the actual Bukkit block inspection stays on the main thread.
        BukkitTask[] searchHolder=new BukkitTask[1];
        BukkitTask searchTask=Bukkit.getScheduler().runTaskTimer(plugin,new Runnable(){
            private int attempts=0;
            private boolean finished=false;

            @Override public void run(){
                Player player=Bukkit.getPlayer(u);
                if(player==null||!player.isOnline()){
                    finished=true;
                    cancelPending(u,false);
                    return;
                }
                if(finished){
                    return;
                }
                if(attempts++>=MAX_ATTEMPTS){
                    finished=true;
                    cancelPending(u,false);
                    plugin.getMessageService().send(player,plugin.getConfig().getString("messages.rtp.failed","&cCould not find a safe RTP location."));
                    return;
                }

                Location candidate=pickCandidate(world,player.getLocation());
                if(candidate==null){
                    if(attempts>=MAX_ATTEMPTS){
                        finished=true;
                        cancelPending(u,false);
                        plugin.getMessageService().send(player,plugin.getConfig().getString("messages.rtp.failed","&cCould not find a safe RTP location."));
                    }
                    return;
                }

                finished=true;
                preloadForCandidate(world,candidate).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{
                    if(error!=null){
                        startAnotherSearch(player,world,u);
                        return;
                    }
                    if(!pending.containsKey(u))return;
                    Location destination=findSafeAt(world,candidate.getBlockX(),candidate.getBlockZ());
                    if(destination==null){
                        startAnotherSearch(player,world,u);
                        return;
                    }
                    beginCountdown(player,u,destination,seconds);
                }));
                if(searchHolder[0]!=null)searchHolder[0].cancel();
            }
        },0L,1L);
        searchHolder[0]=searchTask;
        pending.put(u,searchTask);
    }

    private void startAnotherSearch(Player player,World world,UUID u){
        if(!pending.containsKey(u))return;
        if(player==null||!player.isOnline()){
            cancelPending(u,false);
            return;
        }
        cancelPending(u,false);
        startTeleport(player,world);
    }

    private Location pickCandidate(World w,Location center){
        int min=Math.max(0,plugin.getConfig().getInt("rtp.min-distance",500));
        int max=Math.max(min+1,plugin.getConfig().getInt("rtp.max-distance",5000));
        int border=(int)Math.max(1,Math.floor(w.getWorldBorder().getSize()/2.0)-32);
        max=Math.min(max,border);
        if(max<=min)min=Math.max(0,max/2);

        ThreadLocalRandom r=ThreadLocalRandom.current();
        double angle=r.nextDouble(0,Math.PI*2);
        double dist=Math.sqrt(r.nextDouble((double)min*min,(double)max*max));
        int x=(int)Math.round(center.getX()+Math.cos(angle)*dist);
        int z=(int)Math.round(center.getZ()+Math.sin(angle)*dist);
        if(!w.getWorldBorder().isInside(new Location(w,x,64,z)))return null;
        return new Location(w,x,0,z);
    }

    private java.util.concurrent.CompletableFuture<Void> preloadForCandidate(World world,Location candidate){
        int cx=candidate.getBlockX()>>4, cz=candidate.getBlockZ()>>4;
        java.util.List<java.util.concurrent.CompletableFuture<Chunk>> futures=new ArrayList<>();
        for(int dx=-1;dx<=1;dx++){
            for(int dz=-1;dz<=1;dz++){
                futures.add(world.getChunkAtAsync(cx+dx,cz+dz,true));
            }
        }
        return java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]));
    }

    private void beginCountdown(Player p,UUID u,Location destination,int seconds){
        cancelPending(u,false);
        BukkitTask task=new org.bukkit.scheduler.BukkitRunnable(){
            int remaining=seconds;
            @Override public void run(){
                Player player=Bukkit.getPlayer(u);
                if(player==null||!player.isOnline()){cancelPending(u,false);cancel();return;}
                if(remaining<=0){
                    pending.remove(u);cancel();
                    if(!isStillSafe(destination)){
                        plugin.getMessageService().send(player,"&cRTP location became unsafe. Please try again.");return;
                    }
                    if(!player.teleport(destination,PlayerTeleportEvent.TeleportCause.PLUGIN)){
                        plugin.getMessageService().send(player,plugin.getConfig().getString("messages.rtp.failed","&cCould not teleport you."));return;
                    }
                    if(!player.hasPermission("emerald.rtp.bypass")&&plugin.getConfig().getLong("rtp.cooldown",60L)>0)cooldowns.put(u,System.currentTimeMillis());
                    player.playSound(player.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,1f,1f);
                    plugin.getMessageService().send(player,plugin.getConfig().getString("messages.rtp.success","&aRTP complete! You have been safely teleported."));
                    return;
                }
                String msg=plugin.getConfig().getString("messages.rtp.countdown","&aRTP starting in &f%time%&a...").replace("%time%",String.valueOf(remaining));
                player.sendActionBar(msg);
                if(remaining==seconds)plugin.getMessageService().send(player,"&aRTP starting in &f"+remaining+"&a...");
                player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.7f,1f+remaining*.05f);
                remaining--;
            }
        }.runTaskTimer(plugin,0L,20L);
        pending.put(u,task);
    }

    private void cancelPending(UUID u,boolean notify){
        BukkitTask t=pending.remove(u);if(t!=null)t.cancel();
        Player p=Bukkit.getPlayer(u);if(notify&&p!=null)plugin.getMessageService().send(p,plugin.getConfig().getString("messages.rtp.cancelled","&cRTP cancelled because you moved."));
    }
    @EventHandler public void onClose(InventoryCloseEvent e){}
    @EventHandler public void onQuit(PlayerQuitEvent e){cancelPending(e.getPlayer().getUniqueId(),false);cooldowns.remove(e.getPlayer().getUniqueId());}
    private Location findSafeLocation(World w,Location center){
        int min=Math.max(0,plugin.getConfig().getInt("rtp.min-distance",500)),max=Math.max(min+1,plugin.getConfig().getInt("rtp.max-distance",5000));
        int border=(int)Math.max(1,Math.floor(w.getWorldBorder().getSize()/2.0)-32);max=Math.min(max,border);if(max<=min)min=Math.max(0,max/2);
        ThreadLocalRandom r=ThreadLocalRandom.current();
        for(int a=0;a<MAX_ATTEMPTS;a++){
            double angle=r.nextDouble(0,Math.PI*2),dist=Math.sqrt(r.nextDouble((double)min*min,(double)max*max));
            int x=(int)Math.round(center.getX()+Math.cos(angle)*dist),z=(int)Math.round(center.getZ()+Math.sin(angle)*dist);
            if(!w.getWorldBorder().isInside(new Location(w,x,64,z)))continue;
            Location safe=findSafeAt(w,x,z);if(safe!=null)return safe;
        }
        return null;
    }
    private Location findSafeAt(World w,int x,int z){
        int minY=w.getMinHeight()+2;
        int maxY;
        if(w.getEnvironment()==World.Environment.NETHER){
            // Never search the roof. Playable terrain is deliberately capped well below the roof bedrock.
            maxY=Math.min(120,w.getMaxHeight()-4);
        }else maxY=w.getMaxHeight()-4;
        int highest=w.getHighestBlockYAt(x,z);
        int start=Math.min(maxY,highest);
        for(int y=start;y>=minY&&y>=start-128;y--){
            Block floor=w.getBlockAt(x,y,z),feet=w.getBlockAt(x,y+1,z),head=w.getBlockAt(x,y+2,z);
            if(!isSafeFloor(floor)||!feet.isPassable()||!head.isPassable()||feet.isLiquid()||head.isLiquid())continue;
            if(w.getEnvironment()==World.Environment.NETHER&&y>=120)continue;
            if(!isTerrainLocation(w,x,y,z))continue;
            if(!hasSafeNeighbors(w,x,y,z))continue;
            Location loc=new Location(w,x+.5,y+1,z+.5);loc.setYaw(ThreadLocalRandom.current().nextFloat()*360f);loc.setPitch(0);return loc;
        }
        return null;
    }
    private boolean isTerrainLocation(World w,int x,int y,int z){
        Material floor=w.getBlockAt(x,y,z).getType();
        if(floor==Material.BEDROCK||floor==Material.DEEPSLATE||floor==Material.REINFORCED_DEEPSLATE)return false;
        // Require the selected floor to be the actual surface, preventing cave interiors.
        int surface=w.getHighestBlockYAt(x,z);
        if(surface!=y)return false;
        if(w.getEnvironment()==World.Environment.NETHER){
            for(int yy=y+1;yy<=Math.min(119,w.getMaxHeight()-1);yy++){
                if(!w.getBlockAt(x,yy,z).isPassable())return false;
            }
        }else{
            if(!w.getBlockAt(x,y+1,z).isPassable()||!w.getBlockAt(x,y+2,z).isPassable())return false;
        }
        return true;
    }
    private boolean hasSafeNeighbors(World w,int x,int y,int z){
        int[][] o={{1,0},{-1,0},{0,1},{0,-1}};
        for(int[] d:o){
            Block f=w.getBlockAt(x+d[0],y,z+d[1]),feet=w.getBlockAt(x+d[0],y+1,z+d[1]);
            if(!isSafeFloor(f)||!feet.isPassable()||feet.isLiquid())return false;
            if(w.getHighestBlockYAt(x+d[0],z+d[1])!=y)return false;
        }
        return true;
    }
    private boolean isStillSafe(Location l){
        World w=l.getWorld();if(w==null)return false;
        int x=l.getBlockX(),y=l.getBlockY()-1,z=l.getBlockZ();
        return isTerrainLocation(w,x,y,z)&&hasSafeNeighbors(w,x,y,z);
    }
    private boolean isSafeFloor(Block b){
        Material m=b.getType();if(!m.isSolid()||b.isLiquid())return false;
        return switch(m){
            case BEDROCK,DEEPSLATE,REINFORCED_DEEPSLATE,LAVA,MAGMA_BLOCK,CACTUS,FIRE,SOUL_FIRE,CAMPFIRE,SOUL_CAMPFIRE,
                 POWDER_SNOW,SWEET_BERRY_BUSH,POINTED_DRIPSTONE,WITHER_ROSE,TNT,END_PORTAL,END_GATEWAY,NETHER_PORTAL,WATER->false;
            default->true;
        };
    }
    private ItemStack item(Material m,String name){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();if(meta!=null){meta.setDisplayName(name);i.setItemMeta(meta);}return i;}
    private String formatTime(long s){if(s<60)return s+"s";long m=s/60,r=s%60;return r==0?m+"m":m+"m "+r+"s";}
    private static final class RtpHolder implements InventoryHolder{public Inventory getInventory(){return null;}}
}
