package dev.nocalhost.plugin.intellij.settings;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import dev.nocalhost.plugin.intellij.settings.data.DevModeService;
import dev.nocalhost.plugin.intellij.settings.data.NocalhostAccount;
import dev.nocalhost.plugin.intellij.settings.data.StandaloneCluster;
import dev.nocalhost.plugin.intellij.utils.DataUtils;
import lombok.Getter;
import lombok.Setter;

@State(
        name = "NocalhostSettings",
        storages = {@Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)}
)
@Getter
@Setter
public class NocalhostSettings implements PersistentStateComponent<NocalhostSettings> {

    private String standaloneClustersJson;
    private String nocalhostAccountsJson;

    private Map<String, String> hash = Maps.newHashMap();
    private Map<String, String> devModeProjectPathServiceMap = Maps.newHashMap();

    public synchronized void updateStandaloneCluster(StandaloneCluster standaloneCluster) {
        Set<StandaloneCluster> set = Sets.newHashSet();
        if (StringUtils.isNotEmpty(standaloneClustersJson)) {
            set = DataUtils.GSON.fromJson(standaloneClustersJson,
                    TypeToken.getParameterized(Set.class, StandaloneCluster.class).getType());
        }
        set.remove(standaloneCluster);
        set.add(standaloneCluster);
        standaloneClustersJson = DataUtils.GSON.toJson(set);
    }

    public synchronized void removeStandaloneCluster(StandaloneCluster standaloneCluster) {
        Set<StandaloneCluster> set = Sets.newHashSet();
        if (StringUtils.isNotEmpty(standaloneClustersJson)) {
            set = DataUtils.GSON.fromJson(standaloneClustersJson,
                    TypeToken.getParameterized(Set.class, StandaloneCluster.class).getType());
        }
        set.remove(standaloneCluster);
        standaloneClustersJson = DataUtils.GSON.toJson(set);
    }

    public synchronized void cleanStandaloneCluster() {
        standaloneClustersJson = "[]";
    }

    public synchronized Set<StandaloneCluster> getStandaloneClusters() {
        Set<StandaloneCluster> set = DataUtils.GSON.fromJson(standaloneClustersJson,
                TypeToken.getParameterized(Set.class, StandaloneCluster.class).getType());
        if (set == null) {
            return Sets.newHashSet();
        }
        return set;
    }

    public synchronized void updateNocalhostAccount(NocalhostAccount nocalhostAccount) {
        Set<NocalhostAccount> set = Sets.newHashSet();
        if (StringUtils.isNotEmpty(nocalhostAccountsJson)) {
            set = DataUtils.GSON.fromJson(nocalhostAccountsJson,
                    TypeToken.getParameterized(Set.class, NocalhostAccount.class).getType());
        }
        set.remove(nocalhostAccount);
        set.add(nocalhostAccount);
        nocalhostAccountsJson = DataUtils.GSON.toJson(set);
    }

    public synchronized void removeNocalhostAccount(NocalhostAccount nocalhostAccount) {
        Set<NocalhostAccount> set = Sets.newHashSet();
        if (StringUtils.isNotEmpty(nocalhostAccountsJson)) {
            set = DataUtils.GSON.fromJson(nocalhostAccountsJson,
                    TypeToken.getParameterized(Set.class, NocalhostAccount.class).getType());
        }
        set.remove(nocalhostAccount);
        nocalhostAccountsJson = DataUtils.GSON.toJson(set);
    }

    public synchronized void cleanNocalhostAccount() {
        nocalhostAccountsJson = "[]";
    }

    public synchronized Set<NocalhostAccount> getNocalhostAccounts() {
        Set<NocalhostAccount> set = DataUtils.GSON.fromJson(nocalhostAccountsJson,
                TypeToken.getParameterized(Set.class, NocalhostAccount.class).getType());
        if (set == null) {
            return Sets.newHashSet();
        }
        return set;
    }

    public synchronized DevModeService getDevModeServiceByProjectPath(String projectPath) {
        String json = devModeProjectPathServiceMap.get(projectPath);
        if (json == null) {
            return null;
        }
        return DataUtils.GSON.fromJson(json, DevModeService.class);
    }

    public synchronized void setDevModeServiceToProjectPath(DevModeService devModeService) {
        devModeProjectPathServiceMap.put(devModeService.getProjectPath(),
                DataUtils.GSON.toJson(devModeService));
    }

    public synchronized void removeDevModeServiceByProjectPath(String projectPath) {
        devModeProjectPathServiceMap.remove(projectPath);
    }

    @Override
    public @Nullable NocalhostSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull NocalhostSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public @NotNull Map<String, String> getKubeConfigMap() {
        var json = get("KubeConfigMap");
        if (StringUtils.isNotEmpty(json)) {
            var token = TypeToken.getParameterized(Map.class, String.class, String.class).getType();
            return DataUtils.GSON.fromJson(json, token);
        }
        return Maps.newHashMap();
    }

    public synchronized void setKubeConfigMap(@NotNull Map<String, String> map) {
        set("KubeConfigMap", DataUtils.GSON.toJson(map));
    }

    public String get(String key) {
        return hash.get(key);
    }

    public synchronized void set(String key, String val) {
        hash.put(key, val);
    }

    public synchronized void del(String key) {
        hash.remove(key);
    }
}
