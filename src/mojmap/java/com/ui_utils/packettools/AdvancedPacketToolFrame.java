package com.ui_utils.packettools;

import com.ui_utils.packettools.AdvancedPacketTool.PacketDirection;
import com.ui_utils.packettools.AdvancedPacketTool.PacketMode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

final class AdvancedPacketToolFrame extends JFrame {
    private PacketMode mode = PacketMode.LOG;

    private final JLabel modeLabel = new JLabel();
    private final JLabel delayLabel = new JLabel();

    private final PacketListPanel s2cPanel;
    private final PacketListPanel c2sPanel;

    private AdvancedPacketToolFrame() {
        super("Advanced Packet Tool");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 560));
        setLayout(new BorderLayout(8, 8));

        // Top controls
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.gridy = 0; gc.gridx = 0; gc.anchor = GridBagConstraints.WEST;

        JCheckBox logging = new JCheckBox("Logging");
        logging.setSelected(AdvancedPacketTool.isLoggingEnabled());
        logging.addActionListener(e -> AdvancedPacketTool.setLoggingEnabled(logging.isSelected()));
        styleToggle(logging);
        top.add(logging, gc);
        gc.gridx++;

        JCheckBox deny = new JCheckBox("Deny");
        deny.setSelected(AdvancedPacketTool.isDenyEnabled());
        deny.addActionListener(e -> AdvancedPacketTool.setDenyEnabled(deny.isSelected()));
        styleToggle(deny);
        top.add(deny, gc);
        gc.gridx++;

        JCheckBox delay = new JCheckBox("Delay");
        delay.setSelected(AdvancedPacketTool.isDelayEnabled());
        delay.addActionListener(e -> AdvancedPacketTool.setDelayEnabled(delay.isSelected()));
        styleToggle(delay);
        top.add(delay, gc);
        gc.gridx++;

        JButton output = new JButton("Output: " + (AdvancedPacketTool.isFileOutput() ? "File" : "Chat"));
        output.addActionListener(e -> {
            boolean next = !AdvancedPacketTool.isFileOutput();
            AdvancedPacketTool.setFileOutput(next);
            output.setText("Output: " + (next ? "File" : "Chat"));
        });
        top.add(output, gc);
        gc.gridx++;

        JButton showUnknown = new JButton("Unknown: " + (AdvancedPacketTool.isShowUnknownPackets() ? "ON" : "OFF"));
        showUnknown.addActionListener(e -> {
            boolean v = !AdvancedPacketTool.isShowUnknownPackets();
            AdvancedPacketTool.setShowUnknownPackets(v);
            showUnknown.setText("Unknown: " + (v ? "ON" : "OFF"));
            refreshLists();
        });
        top.add(showUnknown, gc);
        gc.gridx++;

        JButton modeBtn = new JButton();
        modeBtn.addActionListener(e -> { mode = mode.next(); updateModeLabel(); refreshLists(); });
        modeLabel.setForeground(new Color(220, 220, 220));
        updateModeLabel();
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        modePanel.add(new JLabel("Editing:"));
        modePanel.add(modeLabel);
        modeBtn.setText("Change");
        top.add(modePanel, gc);
        gc.gridx++;
        top.add(modeBtn, gc);

        gc.gridy++; gc.gridx = 0;
        top.add(new JLabel("Delay:"), gc);
        gc.gridx++;
        JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(AdvancedPacketTool.getDelayTicks(), 0, 9999, 1));
        delaySpinner.addChangeListener(e -> {
            int v = ((Number)delaySpinner.getValue()).intValue();
            AdvancedPacketTool.setDelayTicks(v);
            updateDelayLabel();
        });
        top.add(delaySpinner, gc);
        gc.gridx++;
        updateDelayLabel();
        top.add(delayLabel, gc);

        add(top, BorderLayout.NORTH);

        // Center lists
        s2cPanel = new PacketListPanel(
            "S2C Packets (Server -> Client)",
            AdvancedPacketTool.getAvailablePackets(PacketDirection.S2C),
            AdvancedPacketTool.getSelection(mode, PacketDirection.S2C));
        c2sPanel = new PacketListPanel(
            "C2S Packets (Client -> Server)",
            AdvancedPacketTool.getAvailablePackets(PacketDirection.C2S),
            AdvancedPacketTool.getSelection(mode, PacketDirection.C2S));

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints cc = new GridBagConstraints();
        cc.insets = new Insets(6, 6, 6, 6);
        cc.gridy = 0; cc.gridx = 0; cc.fill = GridBagConstraints.BOTH; cc.weightx = 1; cc.weighty = 1;
        center.add(s2cPanel, cc);
        cc.gridx = 1;
        center.add(c2sPanel, cc);
        add(center, BorderLayout.CENTER);

        // Bottom
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        JButton s2cAll = new JButton("S2C All");
        s2cAll.addActionListener(e -> s2cPanel.selectAll());
        JButton s2cNone = new JButton("S2C None");
        s2cNone.addActionListener(e -> s2cPanel.clearAll());
        JButton c2sAll = new JButton("C2S All");
        c2sAll.addActionListener(e -> c2sPanel.selectAll());
        JButton c2sNone = new JButton("C2S None");
        c2sNone.addActionListener(e -> c2sPanel.clearAll());
        JButton save = new JButton("Save");
        save.addActionListener(e -> { saveSelections(); dispose(); });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        bottom.add(s2cAll); bottom.add(s2cNone);
        bottom.add(c2sAll); bottom.add(c2sNone);
        bottom.add(save); bottom.add(cancel);
        add(bottom, BorderLayout.SOUTH);
    }

    private static void styleToggle(JCheckBox box) {
        box.addChangeListener(e -> {
            boolean on = box.isSelected();
            box.setForeground(on ? new Color(0, 200, 0) : new Color(200, 40, 40));
        });
        box.setSelected(box.isSelected());
    }

    private void updateModeLabel() { modeLabel.setText(mode.getLabel()); }
    private void updateDelayLabel() { delayLabel.setText("ticks=" + AdvancedPacketTool.getDelayTicks()); }

    private void refreshLists() {
        s2cPanel.setPackets(AdvancedPacketTool.getAvailablePackets(PacketDirection.S2C));
        s2cPanel.setSelection(AdvancedPacketTool.getSelection(mode, PacketDirection.S2C));
        c2sPanel.setPackets(AdvancedPacketTool.getAvailablePackets(PacketDirection.C2S));
        c2sPanel.setSelection(AdvancedPacketTool.getSelection(mode, PacketDirection.C2S));
    }

    private void saveSelections() {
        AdvancedPacketTool.updateSelection(mode, PacketDirection.S2C, s2cPanel.getSelection());
        AdvancedPacketTool.updateSelection(mode, PacketDirection.C2S, c2sPanel.getSelection());
        AdvancedPacketTool.saveSelectionConfig();
    }

    static void open() {
        SwingUtilities.invokeLater(() -> {
            AdvancedPacketToolFrame f = new AdvancedPacketToolFrame();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    // Inner panel for dual-list control with search
    private static final class PacketListPanel extends JPanel {
        private final JTextField search = new JTextField();
        private final DefaultListModel<String> availableModel = new DefaultListModel<>();
        private final DefaultListModel<String> selectedModel = new DefaultListModel<>();
        private final JList<String> available = new JList<>(availableModel);
        private final JList<String> selected = new JList<>(selectedModel);

        private final List<String> allPackets = new ArrayList<>();

        PacketListPanel(String title, List<String> packets, Set<String> initial) {
            super(new GridBagLayout());
            setBorder(BorderFactory.createTitledBorder(title));
            setPreferredSize(new Dimension(420, 360));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            add(search, c);
            c.gridy = 1; c.gridwidth = 1; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
            add(new JScrollPane(available), c);
            c.gridx = 1;
            add(new JScrollPane(selected), c);

            MouseAdapter transfer = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getSource() == available) {
                        for (String v : available.getSelectedValuesList())
                            if (!contains(selectedModel, v))
                                selectedModel.addElement(v);
                    } else if (e.getSource() == selected) {
                        for (String v : selected.getSelectedValuesList())
                            removeValue(selectedModel, v);
                    }
                }
            };
            available.addMouseListener(transfer);
            selected.addMouseListener(transfer);

            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void ref() { refresh(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { ref(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { ref(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { ref(); }
            });

            setPackets(packets);
            setSelection(initial);
        }

        void setPackets(List<String> packets) {
            allPackets.clear();
            allPackets.addAll(packets);
            refresh();
        }

        void setSelection(Set<String> items) {
            selectedModel.clear();
            for (String s : items)
                selectedModel.addElement(s);
            refresh();
        }

        void selectAll() {
            selectedModel.clear();
            for (String s : allPackets) selectedModel.addElement(s);
            refresh();
        }

        void clearAll() {
            selectedModel.clear();
            refresh();
        }

        Set<String> getSelection() {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (int i = 0; i < selectedModel.size(); i++)
                set.add(selectedModel.getElementAt(i));
            return set;
        }

        private void refresh() {
            String q = search.getText() == null ? "" : search.getText().toLowerCase(Locale.ROOT);
            availableModel.clear();
            List<String> selectedList = new ArrayList<>();
            for (int i = 0; i < selectedModel.size(); i++) selectedList.add(selectedModel.getElementAt(i));

            for (String p : allPackets) {
                boolean match = q.isEmpty() || p.toLowerCase(Locale.ROOT).contains(q);
                if (!match) continue;
                if (!selectedList.contains(p))
                    availableModel.addElement(p);
            }
        }

        private static boolean contains(DefaultListModel<String> m, String v) {
            for (int i = 0; i < m.size(); i++) if (m.get(i).equals(v)) return true; return false;
        }
        private static void removeValue(DefaultListModel<String> m, String v) {
            for (int i = 0; i < m.size(); i++) if (m.get(i).equals(v)) { m.remove(i); return; }
        }
    }
}
