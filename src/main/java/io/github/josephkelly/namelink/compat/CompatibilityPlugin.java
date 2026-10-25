package io.github.josephkelly.namelink.compat;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class CompatibilityPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) {
        if(!mixin.endsWith("QuickLockCommandsMixin")) return true;
        var mod=FabricLoader.getInstance().getModContainer("quicklock");
        if(mod.isEmpty()) return false;
        String version=mod.get().getMetadata().getVersion().getFriendlyString();
        if(!version.equals("0.1.0-alpha.1")) {
            org.slf4j.LoggerFactory.getLogger("namelink").warn("QuickLock {} is untested; nickname adapter disabled. Use Minecraft usernames.",version);
            return false;
        }
        return true;
    }
    public void acceptTargets(Set<String> mine,Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
