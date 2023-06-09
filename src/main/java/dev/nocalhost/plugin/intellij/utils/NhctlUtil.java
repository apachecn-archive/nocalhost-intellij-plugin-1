package dev.nocalhost.plugin.intellij.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import dev.nocalhost.plugin.intellij.nhctl.NhctlGetCommand;
import dev.nocalhost.plugin.intellij.commands.NhctlCommand;
import dev.nocalhost.plugin.intellij.data.NocalhostContext;
import dev.nocalhost.plugin.intellij.nhctl.NhctlDevPodCommand;
import dev.nocalhost.plugin.intellij.commands.data.NhctlRawConfig;
import dev.nocalhost.plugin.intellij.commands.data.NhctlGetResource;
import dev.nocalhost.plugin.intellij.commands.data.NhctlConfigOptions;
import dev.nocalhost.plugin.intellij.commands.data.NhctlDescribeService;

public final class NhctlUtil {
    private static final Path NOCALHOST_BIN_DIR = Paths.get(System.getProperty("user.home"), ".nh", "bin");

    public static String binaryDir() {
        return NOCALHOST_BIN_DIR.toString();
    }

    public static String getName() {
        if (SystemInfo.isWindows) {
            return "nhctl.exe";
        } else {
            return "nhctl";
        }
    }

    public static String binaryPath() {
        return NOCALHOST_BIN_DIR.resolve(getName()).toAbsolutePath().toString();
    }

    public static NhctlRawConfig getDevConfig(NocalhostContext context) throws ExecutionException {
        try {
            var opts = new NhctlConfigOptions(context.getKubeConfigPath(), context.getNamespace());
            opts.setDeployment(context.getServiceName());
            opts.setControllerType(context.getServiceType());

            return ApplicationManager
                    .getApplication()
                    .getService(NhctlCommand.class)
                    .getConfig(context.getApplicationName(), opts, NhctlRawConfig.class);
        } catch (Exception ex) {
            throw new ExecutionException(ex);
        }
    }

    public static @NotNull NhctlDescribeService getDescribeService(@NotNull Project project, @NotNull String name, String kind, String namespace, String application, Path kubeConfig) throws ExecutionException {
        try {
            var cmd = new NhctlGetCommand(project);
            cmd.setName(name);
            cmd.setKind(kind);
            cmd.setNamespace(namespace);
            cmd.setKubeConfig(kubeConfig);
            cmd.setApplication(application);
            var resource = cmd.execute(NhctlGetResource.class);
            if (resource != null) {
                return resource.getNhctlDescribeService();
            }
        } catch (Exception ex) {
            throw new ExecutionException(ex);
        }
        throw new ExecutionException("Failed to get resource.");
    }

    public static @NotNull NhctlDescribeService getDescribeService(@NotNull Project project, @NotNull NocalhostContext context) throws ExecutionException {
        try {
            var cmd = new NhctlGetCommand(project);
            cmd.setName(context.getServiceName());
            cmd.setKind(context.getServiceType());
            cmd.setNamespace(context.getNamespace());
            cmd.setKubeConfig(context.getKubeConfigPath());
            cmd.setApplication(context.getApplicationName());
            var resource = cmd.execute(NhctlGetResource.class);
            if (resource != null) {
                return resource.getNhctlDescribeService();
            }
        } catch (Exception ex) {
            throw new ExecutionException(ex);
        }
        throw new ExecutionException("Failed to get resource.");
    }

    public static @NotNull String getDevPodName(Project project, @NotNull NocalhostContext context) throws ExecutionException {
        var cmd = new NhctlDevPodCommand(project);
        cmd.setNamespace(context.getNamespace());
        cmd.setDeployment(context.getServiceName());
        cmd.setKubeConfig(context.getKubeConfigPath());
        cmd.setControllerType(context.getServiceType());
        cmd.setApplication(context.getApplicationName());

        try {
            var pod = cmd.execute();
            if (StringUtils.isEmpty(pod)) {
                throw new ExecutionException("Pod not found");
            }
            return pod;
        } catch (Exception ex) {
            throw new ExecutionException(ex);
        }
    }
}
