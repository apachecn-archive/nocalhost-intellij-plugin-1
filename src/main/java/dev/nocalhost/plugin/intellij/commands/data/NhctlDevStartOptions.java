package dev.nocalhost.plugin.intellij.commands.data;

import com.intellij.openapi.progress.Task;

import java.nio.file.Path;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NhctlDevStartOptions extends NhctlGlobalOptions {
    private String mode;
    private String deployment;
    private String image;
    private String header;
    private String localSync;
    private String sidecarImage;
    private String storageClass;
    private String syncthingVersion;
    private String workDir;
    private String container;
    private String controllerType;
    private boolean withoutTerminal;
    private boolean authCheck;

    public NhctlDevStartOptions(Path kubeConfigPath, String namespace) {
        super(kubeConfigPath, namespace);
    }

    public NhctlDevStartOptions(Path kubeConfigPath, String namespace, Task task) {
        super(kubeConfigPath, namespace, task);
    }
}
