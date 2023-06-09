package dev.nocalhost.plugin.intellij.nhctl;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Lists;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NhctlConfigEditCommand extends BaseCommand {
    private static final Logger LOG = Logger.getInstance(NhctlConfigEditCommand.class);

    private String yaml;
    private String application;
    private String controllerType;

    public NhctlConfigEditCommand(Project project) {
        super(project);
    }

    @Override
    protected List<String> compute() {
        List<String> args = Lists.newArrayList(getBinaryPath(), "config", "edit", application);
        if (StringUtils.isNotEmpty(controllerType)) {
            args.add("--controller-type");
            args.add(controllerType);
        }

        args.add("-f");
        args.add("-");
        return fulfill(args);
    }

    @Override
    protected void onInput(@NotNull Process process) {
        var stream = process.getOutputStream();
        try (stream) {
            stream.write(yaml.getBytes(StandardCharsets.UTF_8));
            stream.flush();
        } catch (Exception ex) {
            LOG.error("Failed to write dev config to stdin", ex);
        }
    }
}
