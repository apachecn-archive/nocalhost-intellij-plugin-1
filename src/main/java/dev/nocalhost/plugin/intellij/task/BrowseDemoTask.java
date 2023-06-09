package dev.nocalhost.plugin.intellij.task;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.nocalhost.plugin.intellij.commands.NhctlCommand;
import dev.nocalhost.plugin.intellij.commands.data.NhctlAppPortForward;
import dev.nocalhost.plugin.intellij.commands.data.NhctlGetOptions;
import dev.nocalhost.plugin.intellij.commands.data.NhctlGetResource;
import dev.nocalhost.plugin.intellij.commands.data.NhctlPortForwardListOptions;
import dev.nocalhost.plugin.intellij.commands.data.kuberesource.Condition;
import dev.nocalhost.plugin.intellij.utils.Constants;
import dev.nocalhost.plugin.intellij.utils.ErrorUtil;
import lombok.SneakyThrows;

public class BrowseDemoTask extends BaseBackgroundTask {
    private final NhctlCommand nhctlCommand = ApplicationManager.getApplication().getService(NhctlCommand.class);

    private final Path kubeConfigPath;
    private final String namespace;

    private final AtomicReference<String> productPagePort = new AtomicReference<>(null);

    public BrowseDemoTask(Project project, Path kubeConfigPath, String namespace) {
        super(project, "Browse demo", true);
        this.kubeConfigPath = kubeConfigPath;
        this.namespace = namespace;
    }

    @SneakyThrows
    @Override
    public void runTask(@NotNull ProgressIndicator indicator) {
        NhctlPortForwardListOptions nhctlPortForwardListOptions = new NhctlPortForwardListOptions(
                kubeConfigPath, namespace, this);
        List<NhctlAppPortForward> portForwards = nhctlCommand.listPortForward(Constants.DEMO_NAME,
                nhctlPortForwardListOptions);
        for (NhctlAppPortForward portForward : portForwards) {
            if (portForward.getPort().endsWith(":9080")) {
                int pos = portForward.getPort().lastIndexOf(":9080");
                productPagePort.set(portForward.getPort().substring(0, pos));
                break;
            }
        }

        int total;
        int ready;
        do {
            Thread.sleep(3000);

            NhctlGetOptions nhctlGetOptions = new NhctlGetOptions(kubeConfigPath, namespace, this);
            nhctlGetOptions.setApplication(Constants.DEMO_NAME);
            List<NhctlGetResource> resources = nhctlCommand.getResources("Deployment", nhctlGetOptions);
            total = resources.size();
            ready = 0;

            for (NhctlGetResource resource : resources) {
                List<Condition> conditions = resource.getKubeResource().getStatus().getConditions();
                if (conditions == null) {
                    continue;
                }
                for (Condition condition : conditions) {
                    if (StringUtils.equals(condition.getType(), "Available")
                            && StringUtils.equals(condition.getStatus(), "True")) {
                        ready++;
                        break;
                    } else if (StringUtils.equals(condition.getType(), "Progressing")
                            && StringUtils.equals(condition.getStatus(), "False")) {
                        // Stop checking status when deployments failed to start.
                        return;
                    }
                }
            }
        } while (total != ready);
    }

    @Override
    public void onSuccess() {
        super.onSuccess();
        if (StringUtils.isNotEmpty(productPagePort.get())) {
            BrowserUtil.browse("http://127.0.0.1:" + productPagePort.get() + "/productpage");
        }
    }

    @Override
    public void onThrowable(@NotNull Throwable e) {
        ErrorUtil.dealWith(this.getProject(), "Demo browse error",
                "Error occurred while browsing demo", e);
    }
}
