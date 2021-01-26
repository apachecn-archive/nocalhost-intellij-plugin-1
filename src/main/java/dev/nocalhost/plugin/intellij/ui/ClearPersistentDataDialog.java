package dev.nocalhost.plugin.intellij.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.*;

import dev.nocalhost.plugin.intellij.api.data.DevSpace;
import dev.nocalhost.plugin.intellij.commands.NhctlCommand;
import dev.nocalhost.plugin.intellij.commands.data.NhctlCleanPVCOptions;
import dev.nocalhost.plugin.intellij.commands.data.NhctlPVCItem;
import dev.nocalhost.plugin.intellij.utils.KubeConfigUtil;

public class ClearPersistentDataDialog extends DialogWrapper {
    private final DevSpace devSpace;

    private JPanel contentPane;
    private JButton selectAllButton;
    private JButton clearAllButton;
    private JList<NhctlPVCItem> pvcList;

    private boolean showSvcNames;

    public ClearPersistentDataDialog(DevSpace devSpace, List<NhctlPVCItem> nhctlPVCItems, boolean showSvcNames) {
        super(true);
        init();
        setTitle("Clear Persistent Data");

        this.devSpace = devSpace;
        this.showSvcNames = showSvcNames;

        this.setOKActionEnabled(false);

        selectAllButton.setFocusable(false);
        selectAllButton.addActionListener((e) -> {
            pvcList.setSelectedIndices(IntStream.range(0, nhctlPVCItems.size()).toArray());
        });

        clearAllButton.setFocusable(false);
        clearAllButton.setEnabled(false);
        clearAllButton.addActionListener((e) -> {
            pvcList.clearSelection();
        });

        pvcList.setCellRenderer(new ListItemCheckBox());
        pvcList.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (super.isSelectedIndex(index0)) {
                    super.removeSelectionInterval(index0, index1);
                } else {
                    super.addSelectionInterval(index0, index1);
                }
            }
        });
        pvcList.addListSelectionListener((e) -> {
            if (pvcList.getSelectedIndices().length == 0) {
                selectAllButton.setEnabled(true);
                clearAllButton.setEnabled(false);
                ClearPersistentDataDialog.this.setOKActionEnabled(false);
            } else if (pvcList.getSelectedIndices().length == nhctlPVCItems.size()) {
                selectAllButton.setEnabled(false);
                clearAllButton.setEnabled(true);
                ClearPersistentDataDialog.this.setOKActionEnabled(true);
            } else {
                selectAllButton.setEnabled(true);
                clearAllButton.setEnabled(true);
                ClearPersistentDataDialog.this.setOKActionEnabled(true);
            }
        });
        pvcList.setListData(nhctlPVCItems.toArray(new NhctlPVCItem[0]));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return contentPane;
    }

    @Override
    protected void doOKAction() {
        ProgressManager.getInstance().run(new Task.Backgroundable(null, "Clearing persistent data", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                final NhctlCommand nhctlCommand = ServiceManager.getService(NhctlCommand.class);
                for (NhctlPVCItem item : pvcList.getSelectedValuesList()) {
                    NhctlCleanPVCOptions opts = new NhctlCleanPVCOptions();
                    opts.setApp(item.getAppName());
                    opts.setSvc(item.getServiceName());
                    opts.setName(item.getName());
                    opts.setKubeconfig(KubeConfigUtil.kubeConfigPath(devSpace).toString());
                    try {
                        nhctlCommand.cleanPVC(opts);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                Notifications.Bus.notify(new Notification(
                        "Nocalhost.Notification",
                        "Persistent data cleared",
                        "",
                        NotificationType.INFORMATION));
            }
        });

        super.doOKAction();
    }

    private class ListItemCheckBox extends JCheckBox implements ListCellRenderer<NhctlPVCItem> {
        public ListItemCheckBox() {
            super();
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends NhctlPVCItem> list,
                NhctlPVCItem value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            if (showSvcNames) {
                this.setText(String.format("%s-%s:%s", value.getAppName(), value.getServiceName(), value.getMountPath()));
            } else {
                this.setText(value.getMountPath());
            }
            //setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            this.setSelected(isSelected);
            return this;
        }
    }
}