package dev.nocalhost.plugin.intellij.ui.dialog;

import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

import dev.nocalhost.plugin.intellij.utils.DataUtils;
import dev.nocalhost.plugin.intellij.utils.ErrorUtil;
import dev.nocalhost.plugin.intellij.utils.TextUiUtil;
import dev.nocalhost.plugin.intellij.utils.FileChooseUtil;
import dev.nocalhost.plugin.intellij.utils.KubeConfigUtil;
import dev.nocalhost.plugin.intellij.data.kubeconfig.KubeConfig;
import dev.nocalhost.plugin.intellij.data.kubeconfig.KubeContext;
import dev.nocalhost.plugin.intellij.task.AddStandaloneClusterTask;
import dev.nocalhost.plugin.intellij.nhctl.NhctlKubeConfigCheckCommand;
import lombok.SneakyThrows;

public class AddStandaloneClustersDialog extends DialogWrapper {
    private final AtomicReference<NhctlKubeConfigCheckCommand> command = new AtomicReference<>(null);
    private final Project project;

    private Timer timer;
    private JLabel lblMark;
    private JPanel dialogPanel;
    private JBTextArea txtHint;
    private JTabbedPane tabbedPane;
    private JBTextField txtNamespace;
    private JComboBox<KubeContext> cmbContexts;
    private JBTextArea kubeconfigFilePasteTextField;
    private TextFieldWithBrowseButton kubeconfigFileSelectTextField;

    public AddStandaloneClustersDialog(Project project) {
        super(project, true);
        this.project = project;

        setResizable(false);
        setTitle("Connect to Cluster");
        setOKButtonText("Add");

        txtNamespace.getEmptyText().setText("Enter a namespace if you don't have cluster-level role");
        cmbContexts.setRenderer(new KubeContextRender());
        cmbContexts.addItemListener(e -> {
            if (ItemEvent.SELECTED == e.getStateChange()) {
                var item = (KubeContext) e.getItem();
                txtNamespace.setText(item.getContext().getNamespace());
                doCheck(item);
            }
        });

        txtNamespace.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // do nothing
            }

            @Override
            public void focusLost(FocusEvent e) {
                var ctx = (KubeContext) cmbContexts.getSelectedItem();
                if (ctx != null) {
                    doCheck(ctx);
                }
            }
        });

        tabbedPane.addChangeListener(e -> {
            lblMark.setIcon(AllIcons.Nodes.EmptyNode);
            switch (tabbedPane.getSelectedIndex()) {
                case 0:
                    setContextsFormKubeConfigFileSelectTextField();
                    break;
                case 1:
                    setContextsFormKubeConfigFilePasteTextField();
                    break;
                default:
                    break;
            }
        });

        kubeconfigFileSelectTextField.addBrowseFolderListener("Select KubeConfig File", "",
                null, FileChooseUtil.singleFileChooserDescriptor());
        kubeconfigFileSelectTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                setContextsFormKubeConfigFileSelectTextField();
            }
        });

        kubeconfigFilePasteTextField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                cancelCommand();
                timer = new Timer(100, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        setContextsFormKubeConfigFilePasteTextField();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });

        TextUiUtil.setCutCopyPastePopup(
                kubeconfigFileSelectTextField.getTextField(),
                kubeconfigFilePasteTextField);

        try {
            Path preset = Paths.get(System.getProperty("user.home"), ".kube/config");
            if (Files.exists(preset)) {
                kubeconfigFileSelectTextField.setText(preset.toString());
            }
        } catch (Exception ignore) {
        }

        init();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        switch (tabbedPane.getSelectedIndex()) {
            case 0:
                if (!StringUtils.isNotEmpty(kubeconfigFileSelectTextField.getText())) {
                    return new ValidationInfo("Please select KubeConfig file",
                            kubeconfigFileSelectTextField);
                }
                break;
            case 1:
                if (!StringUtils.isNotEmpty(kubeconfigFilePasteTextField.getText())) {
                    return new ValidationInfo("Please paste KubeConfig text",
                            kubeconfigFilePasteTextField);
                }
                break;
            default:
                break;
        }
        if (cmbContexts.getSelectedIndex() == -1) {
            return new ValidationInfo("Context is required", cmbContexts);
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return dialogPanel;
    }

    private String getRawKubeConfig(@NotNull KubeContext context) throws Exception {
        var raw = "";
        if (tabbedPane.getSelectedIndex() == 0) {
            raw = Files.readString(Paths.get(kubeconfigFileSelectTextField.getText()), StandardCharsets.UTF_8);
        } else {
            raw = kubeconfigFilePasteTextField.getText();
        }

        if (StringUtils.isNotEmpty(txtNamespace.getText())) {
            KubeConfig kubeConfig = DataUtils.YAML.loadAs(raw, KubeConfig.class);
            kubeConfig
                    .getContexts()
                    .stream()
                    .filter(x -> StringUtils.equals(x.getName(), context.getName()))
                    .findFirst()
                    .ifPresent(x -> x.getContext().setNamespace(txtNamespace.getText()));

            kubeConfig.setCurrentContext(context.getName());
            return DataUtils.toYaml(kubeConfig);
        }
        return raw;
    }

    private void cancelCommand() {
        if (timer != null) {
            timer.stop();
        }
        if (command.get() != null) {
            command.get().destroy();
        }
    }

    private void doCheck(@NotNull KubeContext context) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checking", true) {
            @Override
            public void onCancel() {
                cancelCommand();
            }

            @Override
            public void onThrowable(@NotNull Throwable ex) {
                ErrorUtil.dealWith(project, "Failed to check KubeConfig",
                        "Error occurred while checking KubeConfig", ex);
            }

            @Override
            @SneakyThrows
            public void run(@NotNull ProgressIndicator indicator) {
                cancelCommand();

                var cmd = new NhctlKubeConfigCheckCommand(project);
                cmd.setContext(context.getName());
                cmd.setKubeConfig(KubeConfigUtil.toPath(getRawKubeConfig(context)));

                command.set(cmd);
                txtHint.setText("");
                lblMark.setIcon(new AnimatedIcon.Default());
                setOKActionEnabled(false);

                var token = TypeToken.getParameterized(List.class, NhctlKubeConfigCheckCommand.Result.class).getType();
                List<NhctlKubeConfigCheckCommand.Result> results = DataUtils.GSON.fromJson(cmd.execute(), token);
                if (results == null || results.size() == 0) {
                    return;
                }
                for (var item : results) {
                    if ( ! StringUtils.equals("SUCCESS", item.getStatus())) {
                        txtHint.setText(item.getTips());
                        lblMark.setIcon(AllIcons.General.Warning);
                        return;
                    }
                }

                lblMark.setIcon(AllIcons.Actions.Commit);
                setOKActionEnabled(true);
            }
        });
    }

    @Override
    protected void doOKAction() {
        try {
            var ctx = (KubeContext) cmbContexts.getSelectedItem();
            if (ctx != null) {
                var raw = getRawKubeConfig(ctx);
                ProgressManager.getInstance().run(new AddStandaloneClusterTask(project, raw, ctx));
                super.doOKAction();
            }
        } catch (Exception ex) {
            ErrorUtil.dealWith(project, "Failed to add KubeConfig",
                    "Error occurred while adding KubeConfig", ex);
        }
    }

    @Override
    public void doCancelAction() {
        cancelCommand();
        super.doCancelAction();
    }

    @Override
    @NotNull
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }

    @Override
    protected void doHelpAction() {
        BrowserUtil.browse("https://nocalhost.dev");
    }

    private List<KubeContext> resolveContexts(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return resolveContexts(content);
        } catch (Exception ignore) {
        }
        return Lists.newArrayList();
    }

    private List<KubeContext> resolveContexts(String text) {
        try {
            KubeConfig kubeConfig = DataUtils.YAML.loadAs(text, KubeConfig.class);
            if (kubeConfig.getContexts() != null) {
                return kubeConfig.getContexts();
            }
        } catch (Exception ignore) {
        }
        return Lists.newArrayList();
    }

    private void setContextsFormKubeConfigFileSelectTextField() {
        txtHint.setText("");
        cmbContexts.setSelectedItem(null);
        cmbContexts.removeAllItems();
        String text = kubeconfigFileSelectTextField.getText();
        if (StringUtils.isNotEmpty(text)) {
            var contexts = resolveContexts(Paths.get(text));
            contexts.forEach(x -> cmbContexts.addItem(x));

            if (contexts.size() == 1) {
                cmbContexts.setSelectedIndex(0);
            }
        }
    }

    private void setContextsFormKubeConfigFilePasteTextField() {
        txtHint.setText("");
        cmbContexts.setSelectedItem(null);
        cmbContexts.removeAllItems();
        String text = kubeconfigFilePasteTextField.getText();
        if (StringUtils.isNotEmpty(text)) {
            var contexts = resolveContexts(text);
            contexts.forEach(x -> cmbContexts.addItem(x));

            if (contexts.size() == 1) {
                cmbContexts.setSelectedIndex(0);
            }
        }
    }

    private static class KubeContextRender extends JLabel implements ListCellRenderer<KubeContext> {

        public KubeContextRender() {
            super();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends KubeContext> list, KubeContext value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value.getName());
            return this;
        }
    }
}
