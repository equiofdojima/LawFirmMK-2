import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.*;

/** Document records of the cases; the actual files are copied into the firm's data folder. */
public class DocumentsPage extends TablePage {
    private final JComboBox<String> typeBox;

    public DocumentsPage() {
        super("LOCAL FILE RECORDS", "Documents",
                new String[]{"Document Name", "Type", "Related Case", "Date Added", "Added By", "File Location", "Status"},
                new int[]{170, 90, 160, 90, 120, 220, 70}, true);
        User me = Data.getCurrentUser();
        String[] types = new String[Doc.TYPES.length + 1];
        types[0] = "All types";
        System.arraycopy(Doc.TYPES, 0, types, 1, Doc.TYPES.length);
        typeBox = Ui.combo(types);
        typeBox.addActionListener(new RefreshListener(this));
        filters.add(typeBox, 0);

        actions.add(Ui.button("Open File", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openFile();
            }
        }));
        if (me.can("UPDATE_STATUS")) actions.add(Ui.button("Change Status", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeStatus();
            }
        }));
        if (me.can("REMOVE_DOCUMENT")) actions.add(Ui.button("Remove", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                remove();
            }
        }));
        if (me.can("ADD_DOCUMENT")) actions.add(Ui.button("+ Add Document", true, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addDocument();
            }
        }));
    }

    @Override
    protected void load() {
        String t = (String) typeBox.getSelectedItem();
        ArrayList<Doc> ls16 = Data.visibleDocuments();
        for (int ix16 = 0; ix16 < ls16.size(); ix16++) {
            Doc d = ls16.get(ix16);
            if (!t.startsWith("All") && !d.getType().equals(t)) continue;
            addRow(d, d.getName(), d.getType(), d.getRelatedCase().code() + " " + d.getRelatedCase().getTitle(), d.getAdded(), d.getAddedBy(), d.getLocation(), d.getStatus());
        }
    }


    @Override
    protected void onOpen(Object ref) {
        openFile();
    }

    private void openFile() {
        Doc d = (Doc) pick("a document");
        if (d == null) return;
        File f = new File(d.getLocation());
        if (!f.isFile()) {
            Ui.info(this, "This record has no stored file on this computer.\nLocation noted: " + d.getLocation(), "Open file");
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) throw new IOException("Opening files is not supported here.");
            Desktop.getDesktop().open(f);
        } catch (IOException | RuntimeException ex) {
            warn("Could not open the file: " + ex.getMessage());
        }
    }

    private void changeStatus() {
        Doc d = (Doc) pick("a document");
        if (d == null) return;
        JComboBox<String> box = Ui.combo(Doc.STATUSES);
        box.setSelectedItem(d.getStatus());
        JPanel f = Ui.dialogForm();
        Ui.row(f, "Document:", Ui.label(d.getName(), Ui.PLAIN, Ui.TEXT));
        Ui.row(f, "New status:", box);
        if (!Ui.ask(this, "Change status", f)) return;
        d.setStatus((String) box.getSelectedItem());
        Data.commit("Document " + d.code() + " is now " + d.getStatus());
        refresh();
    }

    private void remove() {
        Doc d = (Doc) pick("a document");
        if (d == null) return;
        if (!Ui.confirm(this, "Remove the record \"" + d.getName() + "\"?\nThe stored copy of the file is deleted too.")) return;
        File p = new File(d.getLocation());
        // only copies kept in the portal's own "files" folder are deleted, never the user's original file
        if (p.getAbsolutePath().startsWith(Data.filesDir().getAbsolutePath())) p.delete();
        Data.removeDocument(d);
        Data.commit("Removed document " + d.code() + " — " + d.getName());
        refresh();
        status("Document removed.");
    }

    private void addDocument() {
        if (!Data.getCurrentUser().can("ADD_DOCUMENT")) return;
        ArrayList<Case> cs = Data.visibleCases();
        if (cs.isEmpty()) { warn("There are no cases to attach a document to."); return; }

        JTextField name = Ui.field("");
        JComboBox<String> type = Ui.combo(Doc.TYPES);
        JComboBox<String> caseBox = Ui.combo(Ui.caseLabels(cs));
        JComboBox<String> statusBox = Ui.combo(Doc.STATUSES);
        JTextField path = Ui.field("");
        path.setEditable(false);
        final File[] chosen = {null};
        Btn browse = Ui.secondary("Browse...");
        browse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(DocumentsPage.this) == JFileChooser.APPROVE_OPTION) {
                    chosen[0] = fc.getSelectedFile();
                    path.setText(chosen[0].getAbsolutePath());
                    if (name.getText().trim().isEmpty()) name.setText(chosen[0].getName());
                }
            }
        });
        JPanel fileRow = new JPanel(new BorderLayout(6, 0));
        fileRow.setOpaque(false);
        fileRow.add(path, BorderLayout.CENTER);
        fileRow.add(browse, BorderLayout.EAST);

        Form f = new Form(this, "CREATE RECORD", "Add Document", "Save Record", new Saver() {
            @Override
            public String save() {
                String n = name.getText().trim();
                if (n.isEmpty()) return "Enter the document name.";
                if (chosen[0] == null || !chosen[0].isFile()) return "Choose the file to attach.";
                if (chosen[0].length() > 50L * 1024 * 1024) return "The file is larger than 50 MB.";
                Case c = cs.get(caseBox.getSelectedIndex());
                File target;
                try {
                    File dir = Data.filesDir();
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
                    String safe = chosen[0].getName().replaceAll("[^A-Za-z0-9._-]", "_");
                    target = new File(dir, System.currentTimeMillis() + "-" + safe);
                    Data.copyFile(chosen[0], target);
                } catch (IOException ex) {
                    return "Could not store the file: " + ex.getMessage();
                }
                Doc d = Data.newDoc(n, (String) type.getSelectedItem(), c, target.getAbsolutePath(),
                        (String) statusBox.getSelectedItem(), Data.getCurrentUser().getName());
                Data.commit("Added document " + d.code() + " — " + n + " (" + c.code() + ")");
                status("Document added.");
                return null;
            }
        });
        f.section("Document information");
        f.add("Document name", name, 2);
        f.add("Document type", type);
        f.add("Related case", caseBox, 2);
        f.add("Status", statusBox);
        f.add("File", fileRow, 3);
        openForm(f);
    }
}
