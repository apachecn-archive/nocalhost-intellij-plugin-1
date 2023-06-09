package dev.nocalhost.plugin.intellij.ui.action.workload;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;

import dev.nocalhost.plugin.intellij.task.ExecutionTask;
import dev.nocalhost.plugin.intellij.ui.tree.node.ResourceNode;

public class DebugAction extends StartDevelopAction {
    public DebugAction(Project project, ResourceNode node) {
        super("Remote Debug", project, node, AllIcons.Actions.StartDebugger, "", ExecutionTask.kDebug, false);
    }
}
