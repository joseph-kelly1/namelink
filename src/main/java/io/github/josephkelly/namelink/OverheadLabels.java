package io.github.josephkelly.namelink;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.*;
import io.github.josephkelly.namelink.mixin.TeamPacketAccessor;

/** Per-viewer, untracked vanilla displays. Never adds entities or teams to the server world. */
public final class OverheadLabels {
    private static final class Viewer {
        final Map<UUID,Label> labels=new HashMap<>();
        final Map<UUID,PlayerTeam> synthetic=new HashMap<>();
        final Set<String> hiddenTeams=new HashSet<>();
    }
    private record Label(Display.TextDisplay display,String text,int targetId) {}
    private boolean stopping;
    private final Map<UUID,Viewer> viewers=new HashMap<>();
    private final Map<UUID,Set<UUID>> tracking=new HashMap<>();
    private final Scoreboard privateScoreboard=new Scoreboard();
    public void join(ServerPlayer p) { viewers.computeIfAbsent(p.getUUID(),ignored->new Viewer()); }
    public void track(ServerPlayer viewer,ServerPlayer target) { tracking.computeIfAbsent(viewer.getUUID(),ignored->new HashSet<>()).add(target.getUUID()); }
    public void untrack(ServerPlayer viewer,ServerPlayer target) {
        var set=tracking.get(viewer.getUUID()); if(set!=null) set.remove(target.getUUID());
        var v=viewers.get(viewer.getUUID()); if(v!=null) remove(viewer,v,target.getUUID());
    }
    private boolean hidden(NameLink n,PlayerTeam team) {
        if(team==null) return false;
        for(var p:n.server.getPlayerList().getPlayers()) if(p.getTeam()==team && n.nickname(p.getUUID())!=null) return true;
        return false;
    }
    /** Copy only the visibility field. Membership, collision, friendly fire, color and prefix/suffix stay intact. */
    public Packet<?> project(NameLink n,Packet<?> packet) {
        if(stopping || !n.config.overhead() || !(packet instanceof ClientboundSetPlayerTeamPacket t) || t.getParameters().isEmpty()) return packet;
        var team=n.server.getScoreboard().getPlayerTeam(t.getName());
        if(!hidden(n,team)) return packet;
        var p=t.getParameters().orElseThrow();
        var replacement=new ClientboundSetPlayerTeamPacket.Parameters(p.displayName(),p.playerPrefix(),p.playerSuffix(),Team.Visibility.NEVER,p.collisionRule(),p.color(),p.options());
        return TeamPacketAccessor.namelink$create(t.getName(),((TeamPacketAccessor)t).namelink$method(),Optional.of(replacement),t.getPlayers());
    }
    public void tick(NameLink n) {
        if(!n.config.overhead()) return;
        var online=n.server.getPlayerList().getPlayers();
        Set<String> desiredTeams=new HashSet<>();
        for(var p:online) if(n.nickname(p.getUUID())!=null && p.getTeam()!=null) desiredTeams.add(p.getTeam().getName());
        for(var viewer:online) {
            Viewer v=viewers.computeIfAbsent(viewer.getUUID(),ignored->new Viewer());
            // Remove synthetic membership first, then reassert real membership if another mod assigned a team.
            for(var entry:new ArrayList<>(v.synthetic.entrySet())) {
                var target=n.server.getPlayerList().getPlayer(entry.getKey());
                if(target==null || target.getTeam()!=null || n.nickname(target.getUUID())==null) {
                    viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(entry.getValue()));
                    v.synthetic.remove(entry.getKey());
                    if(target!=null && target.getTeam()!=null) viewer.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(target.getTeam(),target.getScoreboardName(),ClientboundSetPlayerTeamPacket.Action.ADD));
                }
            }
            for(var target:online) if(target.getTeam()==null && n.nickname(target.getUUID())!=null && !v.synthetic.containsKey(target.getUUID())) {
                String name;
                do { name="nl$"+UUID.randomUUID().toString().replace("-","").substring(0,13); }
                while(n.server.getScoreboard().getPlayerTeam(name)!=null);
                PlayerTeam team=new PlayerTeam(privateScoreboard,name);
                team.setNameTagVisibility(Team.Visibility.NEVER);
                team.getPlayers().add(target.getScoreboardName());
                viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team,true));
                v.synthetic.put(target.getUUID(),team);
            }
            for(String name:new HashSet<>(v.hiddenTeams)) if(!desiredTeams.contains(name)) {
                var team=n.server.getScoreboard().getPlayerTeam(name);
                if(team!=null) viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team,false));
                v.hiddenTeams.remove(name);
            }
            for(String name:desiredTeams) if(v.hiddenTeams.add(name)) {
                var team=n.server.getScoreboard().getPlayerTeam(name);
                viewer.connection.send(project(n,ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team,false)));
            }
            Set<UUID> wanted=new HashSet<>();
            for(var target:online) {
                boolean virtual=n.nickname(target.getUUID())!=null || target.getTeam()!=null && desiredTeams.contains(target.getTeam().getName());
                if(!virtual || !visible(viewer,target)) continue;
                wanted.add(target.getUUID());
                String text=n.nickname(target.getUUID());
                if(text==null) text=target.getGameProfile().name();
                Label label=v.labels.get(target.getUUID());
                if(label==null || !label.text.equals(text) || label.targetId!=target.getId()) {
                    remove(viewer,v,target.getUUID());
                    Display.TextDisplay d=new Display.TextDisplay(EntityTypes.TEXT_DISPLAY,viewer.level());
                    position(d,target);
                    d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
                    d.setText(Component.literal(text));
                    d.setFlags(Display.TextDisplay.FLAG_SHADOW); // Deliberately no SEE_THROUGH flag.
                    d.setViewRange(.5f);
                    viewer.connection.send(new ClientboundAddEntityPacket(d.getId(),d.getUUID(),d.getX(),d.getY(),d.getZ(),0,0,EntityTypes.TEXT_DISPLAY,0,Vec3.ZERO,0));
                    var metadata=d.getEntityData().getNonDefaultValues();
                    if(metadata!=null) viewer.connection.send(new ClientboundSetEntityDataPacket(d.getId(),metadata));
                    v.labels.put(target.getUUID(),new Label(d,text,target.getId()));
                } else {
                    var d=label.display;
                    double x=d.getX(),y=d.getY(),z=d.getZ();
                    position(d,target);
                    if(x!=d.getX() || y!=d.getY() || z!=d.getZ()) viewer.connection.send(ClientboundEntityPositionSyncPacket.of(d));
                }
            }
            for(UUID id:new HashSet<>(v.labels.keySet())) if(!wanted.contains(id)) remove(viewer,v,id);
        }
    }
    private static void position(Display.TextDisplay d,ServerPlayer p) { d.setPos(p.getX(),p.getY()+p.getBbHeight()+.35,p.getZ()); }
    private boolean visible(ServerPlayer viewer,ServerPlayer target) {
        if(viewer==target || viewer.level()!=target.level() || !viewer.isAlive() || !target.isAlive() || target.isSpectator() || target.isInvisible()) return false;
        if(!tracking.getOrDefault(viewer.getUUID(),Set.of()).contains(target.getUUID())) return false;
        double limit=target.isCrouching()?16:32;
        if(viewer.distanceToSqr(target)>limit*limit || !viewer.hasLineOfSight(target)) return false;
        var team=target.getTeam();
        if(team==null) return true;
        boolean same=team.isAlliedTo(viewer.getTeam());
        return switch(team.getNameTagVisibility()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case HIDE_FOR_OTHER_TEAMS -> viewer.getTeam()==null || same;
            case HIDE_FOR_OWN_TEAM -> !same;
        };
    }
    private static void remove(ServerPlayer viewer,Viewer v,UUID target) {
        Label old=v.labels.remove(target);
        if(old!=null) viewer.connection.send(new ClientboundRemoveEntitiesPacket(old.display.getId()));
    }
    public void resetPlayer(ServerPlayer player) {
        Viewer own=viewers.get(player.getUUID());
        if(own!=null) for(UUID id:new HashSet<>(own.labels.keySet())) remove(player,own,id);
        var n=NameLink.active;
        if(n!=null) for(var viewer:n.server.getPlayerList().getPlayers()) {
            var v=viewers.get(viewer.getUUID()); if(v!=null) remove(viewer,v,player.getUUID());
        }
    }
    public void disconnect(ServerPlayer player) {
        resetPlayer(player); viewers.remove(player.getUUID()); tracking.remove(player.getUUID());
        tracking.values().forEach(set->set.remove(player.getUUID()));
    }
    public void stop(NameLink n) {
        stopping=true;
        for(var viewer:n.server.getPlayerList().getPlayers()) {
            var v=viewers.get(viewer.getUUID()); if(v==null) continue;
            for(UUID id:new HashSet<>(v.labels.keySet())) remove(viewer,v,id);
            for(var team:v.synthetic.values()) viewer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
            for(String name:v.hiddenTeams) {
                var team=n.server.getScoreboard().getPlayerTeam(name);
                if(team!=null) viewer.connection.send(TeamPacketAccessor.namelink$create(name,2,Optional.of(new ClientboundSetPlayerTeamPacket.Parameters(team)),List.of()));
            }
        }
        reset();
    }
    public void reset() { viewers.clear(); tracking.clear(); stopping=false; }
    public int labelCount() { return viewers.values().stream().mapToInt(v->v.labels.size()).sum(); }
}
