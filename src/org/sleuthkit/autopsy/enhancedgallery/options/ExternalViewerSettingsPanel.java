package org.sleuthkit.autopsy.enhancedgallery.options;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.enhancedgallery.options.ExternalViewerService.ViewerEntry;
import org.sleuthkit.autopsy.enhancedgallery.options.ExternalViewerSettings;

/**
 * Panel shown in Autopsy → Tools → Options → Enhanced Gallery → External Viewers.
 *
 * Layout:
 *  ┌─────────────────────────────────────────────────────┐
 *  │  Configured external viewers                        │
 *  │  ┌──────────────────────────────────┬───────────┐  │
 *  │  │ Name       │ Default │ Formats   │ Exe path  │  │
 *  │  ├────────────┼─────────┼───────────┼───────────┤  │
 *  │  │ IrfanView  │ ☑      │ jpg,png…  │ C:\...    │  │
 *  │  │ RawTherapee│ ☐      │ cr2,nef…  │ C:\...    │  │
 *  │  └──────────────────────────────────┴───────────┘  │
 *  │  [Add]  [Edit]  [Remove]          [Move up] [Down] │
 *  └─────────────────────────────────────────────────────┘
 */
public class ExternalViewerSettingsPanel extends JPanel {

    private final ViewerTableModel tableModel = new ViewerTableModel();
    private final JTable table = new JTable(tableModel);

    public ExternalViewerSettingsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        add(new JLabel(bundle("ExternalViewerSettingsPanel.title")),
                BorderLayout.NORTH);

        // Table
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(110); // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(55);  // Default
        table.getColumnModel().getColumn(2).setPreferredWidth(140); // Formats
        table.getColumnModel().getColumn(3).setPreferredWidth(220); // Exe
        table.getColumn(bundle("ExternalViewerSettingsPanel.col.default"))
             .setCellRenderer(new DefaultCellRenderer());
        table.getColumn(bundle("ExternalViewerSettingsPanel.col.default"))
             .setCellEditor(new DefaultCellEditor(new JCheckBox()));
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Button row
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton add    = new JButton(bundle("ExternalViewerSettingsPanel.btn.add"));
        JButton edit   = new JButton(bundle("ExternalViewerSettingsPanel.btn.edit"));
        JButton remove = new JButton(bundle("ExternalViewerSettingsPanel.btn.remove"));
        JButton up     = new JButton("▲");
        JButton down   = new JButton("▼");
        btns.add(add); btns.add(edit); btns.add(remove);
        btns.add(Box.createHorizontalStrut(20));
        btns.add(up); btns.add(down);
        add(btns, BorderLayout.SOUTH);

        // Load
        tableModel.setViewers(new ArrayList<>(ExternalViewerSettings.getInstance().loadAll()));

        // Actions
        add.addActionListener(e -> showEditDialog(null));
        edit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) showEditDialog(tableModel.getViewer(row));
        });
        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) tableModel.remove(row);
        });
        up.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row > 0) { tableModel.move(row, row - 1); table.setRowSelectionInterval(row-1, row-1); }
        });
        down.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < tableModel.getRowCount() - 1) {
                tableModel.move(row, row + 1);
                table.setRowSelectionInterval(row+1, row+1);
            }
        });
    }

    /** Called by the Options panel when user clicks OK/Apply */
    public void save() {
        ExternalViewerSettings.getInstance().saveAll(tableModel.getViewers());
    }

    // -------------------------------------------------------------------------
    // Edit dialog
    // -------------------------------------------------------------------------

    private void showEditDialog(ViewerEntry existing) {
        JTextField nameField  = new JTextField(existing != null ? existing.getDisplayName() : "", 20);
        JTextField exeField   = new JTextField(existing != null ? existing.getExePath() : "", 30);
        JTextField extsField  = new JTextField(
                existing != null ? String.join(", ", existing.getExtensions()) : "", 25);
        JTextField mimesField = new JTextField(
                existing != null ? String.join(", ", existing.getMimeTypes()) : "", 25);
        JCheckBox  defBox     = new JCheckBox(
                bundle("ExternalViewerSettingsPanel.edit.default"),
                existing != null && existing.isDefault());
        JButton browse = new JButton("…");

        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Executable (*.exe)", "exe"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                exeField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel exePanel = new JPanel(new BorderLayout(4, 0));
        exePanel.add(exeField, BorderLayout.CENTER);
        exePanel.add(browse, BorderLayout.EAST);

        Object[] fields = {
            bundle("ExternalViewerSettingsPanel.edit.name"),   nameField,
            bundle("ExternalViewerSettingsPanel.edit.exe"),    exePanel,
            bundle("ExternalViewerSettingsPanel.edit.exts"),   extsField,
            bundle("ExternalViewerSettingsPanel.edit.mimes"),  mimesField,
            defBox
        };

        int result = JOptionPane.showConfirmDialog(this, fields,
                bundle("ExternalViewerSettingsPanel.edit.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        String exe  = exeField.getText().trim();
        if (name.isBlank() || exe.isBlank()) return;

        List<String> exts  = splitAndTrim(extsField.getText());
        List<String> mimes = splitAndTrim(mimesField.getText());

        ViewerEntry entry = new ViewerEntry(name, exe, List.of(),
                mimes, exts, defBox.isSelected());

        if (existing != null) {
            int row = tableModel.indexOf(existing);
            if (row >= 0) tableModel.replace(row, entry);
        } else {
            tableModel.add(entry);
        }
    }

    private static List<String> splitAndTrim(String raw) {
        List<String> result = new ArrayList<>();
        for (String s : raw.split("[,;\\s]+")) {
            String t = s.trim().toLowerCase();
            if (!t.isBlank()) result.add(t);
        }
        return result;
    }

    private static String bundle(String key) {
        return NbBundle.getMessage(ExternalViewerSettingsPanel.class, key);
    }

    // -------------------------------------------------------------------------
    // Table model
    // -------------------------------------------------------------------------

    private static class ViewerTableModel extends AbstractTableModel {

        private List<ViewerEntry> viewers = new ArrayList<>();

        private static final String[] COLS = {
            NbBundle.getMessage(ExternalViewerSettingsPanel.class,
                    "ExternalViewerSettingsPanel.col.name"),
            NbBundle.getMessage(ExternalViewerSettingsPanel.class,
                    "ExternalViewerSettingsPanel.col.default"),
            NbBundle.getMessage(ExternalViewerSettingsPanel.class,
                    "ExternalViewerSettingsPanel.col.formats"),
            NbBundle.getMessage(ExternalViewerSettingsPanel.class,
                    "ExternalViewerSettingsPanel.col.exe"),
        };

        void setViewers(List<ViewerEntry> v) { viewers = v; fireTableDataChanged(); }
        List<ViewerEntry> getViewers()        { return viewers; }
        ViewerEntry getViewer(int row)        { return viewers.get(row); }
        int indexOf(ViewerEntry v)            { return viewers.indexOf(v); }

        void add(ViewerEntry v)               { viewers.add(v); fireTableRowsInserted(viewers.size()-1, viewers.size()-1); }
        void remove(int row)                  { viewers.remove(row); fireTableRowsDeleted(row, row); }
        void replace(int row, ViewerEntry v)  { viewers.set(row, v); fireTableRowsUpdated(row, row); }
        void move(int from, int to) {
            ViewerEntry v = viewers.remove(from);
            viewers.add(to, v);
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return viewers.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int col) { return COLS[col]; }
        @Override public Class<?> getColumnClass(int col) {
            return col == 1 ? Boolean.class : String.class;
        }
        @Override public boolean isCellEditable(int row, int col) { return col == 1; }

        @Override
        public Object getValueAt(int row, int col) {
            ViewerEntry v = viewers.get(row);
            return switch (col) {
                case 0 -> v.getDisplayName();
                case 1 -> v.isDefault();
                case 2 -> String.join(", ", v.getExtensions());
                case 3 -> v.getExePath();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col != 1) return;
            ViewerEntry old = viewers.get(row);
            viewers.set(row, new ViewerEntry(
                    old.getDisplayName(), old.getExePath(), old.getExtraArgs(),
                    old.getMimeTypes(), old.getExtensions(), (Boolean) value));
            fireTableCellUpdated(row, col);
        }
    }

    private static class DefaultCellRenderer extends JCheckBox
            implements javax.swing.table.TableCellRenderer {
        DefaultCellRenderer() { setHorizontalAlignment(JLabel.CENTER); setOpaque(true); }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int row, int col) {
            setSelected(Boolean.TRUE.equals(v));
            setBackground(sel ? t.getSelectionBackground() : t.getBackground());
            return this;
        }
    }
}
