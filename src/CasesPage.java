import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import javax.swing.*;

/** Case Management: list, add, edit, change the status of and remove cases. */
// >>> OOP | INHERITANCE: Inherits the whole table screen; this class only adds what is special about cases.
public class CasesPage extends TablePage {
    private final JComboBox<String> statusBox = Ui.combo("All statuses", Case.ACTIVE, Case.PENDING, Case.HANDLED);
    private final JComboBox<String> typeBox;

    public CasesPage() {
        super("CASE RECORDS", "Case Management",
                new String[]{"Case ID", "Case Title", "Client", "Type", "Lawyer", "Next Hearing", "Fee", "Status", ""},
                new int[]{70, 200, 130, 80, 120, 100, 100, 80, 36}, true);
        User me = Data.getCurrentUser();

        String[] types = new String[Case.TYPES.length + 1];
        types[0] = "All types";
        System.arraycopy(Case.TYPES, 0, types, 1, Case.TYPES.length);
        typeBox = Ui.combo(types);

        if (me.can("UPDATE_STATUS")) actions.add(Ui.button("Change Status", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeStatus();
            }
        }));
        if (me.can("EDIT_CASE")) actions.add(Ui.button("Edit", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editCase();
            }
        }));
        if (me.can("REMOVE_CASE")) actions.add(Ui.button("Remove", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeCase();
            }
        }));
        if (me.can("ADD_CASE")) actions.add(Ui.button("+ Add Case", true, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addCase();
            }
        }));

        statusBox.addActionListener(new RefreshListener(this));
        typeBox.addActionListener(new RefreshListener(this));
        filters.add(typeBox, 0);
        filters.add(statusBox, 0);
    }

    @Override
    protected void load() {
        String s = (String) statusBox.getSelectedItem();
        String t = (String) typeBox.getSelectedItem();
        ArrayList<Case> ls8 = Data.visibleCases();
        for (int ix8 = 0; ix8 < ls8.size(); ix8++) {
            Case c = ls8.get(ix8);
            if (!s.startsWith("All") && !c.getStatus().equals(s)) continue;
            if (!t.startsWith("All") && !c.getType().equals(t)) continue;
            Appointment next = Data.nextHearing(c);
            addRow(c, c.code(), c.getTitle(), c.getClient().getName(), c.getType(), c.getLawyer().shortName(),
                    next == null ? null : next.getDate(), Ui.peso(c.getFee()), c.getStatus(), "");
        }
    }


    @Override
    protected void onOpen(Object ref) {
        showDetails((Case) ref);
    }

    // ------------------------------------------------------------------ add / edit
    void addCase() {
        openCaseForm(null);
    }

    void editCase() {
        Case c = (Case) pick("a case");
        if (c != null) openCaseForm(c);
    }

    private void openCaseForm(Case existing) {
        User me = Data.getCurrentUser();
        if (!me.can(existing == null ? "ADD_CASE" : "EDIT_CASE")) return;
        java.util.List<Client> clients = Data.getClients();
        ArrayList<User> lawyers = Data.usersByRole(User.LAWYER);
        if (lawyers.isEmpty() || clients.isEmpty()) {
            warn("There must be at least one lawyer account and one client first (add clients under Client Contacts).");
            return;
        }
        boolean add = existing == null;

        JTextField title = Ui.field(add ? "" : existing.getTitle());
        JComboBox<String> type = Ui.combo(Case.TYPES);
        JComboBox<String> clientBox = Ui.combo(Ui.clientNames(clients));
        JComboBox<String> lawyerBox = Ui.combo(Ui.names(lawyers));
        JTextField opposing = Ui.field(add ? "" : existing.getOpposing());
        JTextField court = Ui.field(add ? "" : existing.getCourt());
        JTextField filed = Ui.field(add || existing.getFiled() == null ? "" : existing.getFiled().toString());
        JTextField fee = Ui.field(add ? "" : String.format("%.2f", existing.getFee()));
        JCheckBox hearing = new JCheckBox("Schedule a first hearing");
        hearing.setOpaque(false);
        hearing.setFont(Ui.PLAIN);
        JSpinner hDate = Ui.dateSpinner(LocalDate.now().plusDays(7));
        JSpinner hTime = Ui.timeSpinner(LocalTime.of(9, 0));
        JTextField hPlace = Ui.field("");
        JComboBox<String> statusBox = Ui.combo(Case.STATUSES);
        JTextArea desc = Ui.area(3);
        if (!add) desc.setText(existing.getDescription());

        if (!add) {
            type.setSelectedItem(existing.getType());
            statusBox.setSelectedItem(existing.getStatus());
            if (!lawyers.contains(existing.getLawyer())) lawyerBox.addItem(existing.getLawyer().getName());
            clientBox.setSelectedItem(existing.getClient().getName());
            lawyerBox.setSelectedItem(existing.getLawyer().getName());
            clientBox.setEnabled(false);
            fee.setEnabled(false);
            lawyerBox.setEnabled(me.getRole().equals(User.ADMIN)); // only the admin reassigns
        } else if (me.getRole().equals(User.LAWYER)) {
            lawyerBox.setSelectedItem(me.getName());
            lawyerBox.setEnabled(false);
        }
        hDate.setEnabled(false);
        hTime.setEnabled(false);
        hPlace.setEnabled(false);
        hearing.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hDate.setEnabled(hearing.isSelected());
                hTime.setEnabled(hearing.isSelected());
                hPlace.setEnabled(hearing.isSelected());
            }
        });

        Form f = new Form(this, add ? "CREATE RECORD" : "EDIT RECORD", add ? "Add New Case" : "Edit " + existing.code(),
                // >>> OOP | POLYMORPHISM: An anonymous class implementing the Saver interface: the Form calls save() without knowing which page it belongs to.
                add ? "Save Record" : "Save Changes", new Saver() {
            @Override
            public String save() {
                String t = title.getText().trim();
                if (t.isEmpty()) return "The case title cannot be empty.";
                LocalDate fd = null;
                if (!filed.getText().trim().isEmpty()) {
                    try { fd = LocalDate.parse(filed.getText().trim()); }
                    catch (DateTimeParseException ex) { return "Filing date must look like 2026-03-15 (or be left empty)."; }
                }
                Client cl = add ? clients.get(clientBox.getSelectedIndex()) : existing.getClient();
                User lw = existing == null ? lawyers.get(lawyerBox.getSelectedIndex()) : existing.getLawyer();
                if (existing != null && me.getRole().equals(User.ADMIN) && lawyerBox.getSelectedIndex() < lawyers.size()) {
                    lw = lawyers.get(lawyerBox.getSelectedIndex());
                }
                for (int ix9 = 0; ix9 < Data.getCases().size(); ix9++) {
                    Case o = Data.getCases().get(ix9);
                    if (o != existing && o.getClient() == cl && o.getTitle().equalsIgnoreCase(t)) return "This client already has a case with that title.";
                }
                if (add) {
                    double amount;
                    try { amount = Double.parseDouble(fee.getText().trim().replace(",", "")); }
                    catch (NumberFormatException ex) { return "Please enter a valid fee (numbers only)."; }
                    if (amount < 0) return "The fee cannot be negative.";
                    String place = hPlace.getText().trim();
                    LocalDate hd = Ui.dateOf(hDate);
                    LocalTime ht = Ui.timeOf(hTime);
                    if (hearing.isSelected()) {
                        if (place.isEmpty()) return "Enter the location of the first hearing.";
                        if (hd.isBefore(LocalDate.now())) return "The hearing date cannot be in the past.";
                        Appointment clash = Data.findConflict(lw, hd, ht, null);
                        if (clash != null) return lw.shortName() + " already has \"" + clash.getEvent() + "\" at that time.";
                    }
                    Case c = Data.newCase(t, cl, lw, (String) type.getSelectedItem(), amount, (String) statusBox.getSelectedItem());
                    c.setOpposing(opposing.getText().trim());
                    c.setCourt(court.getText().trim());
                    c.setFiled(fd);
                    c.setDescription(desc.getText().trim());
                    if (amount > 0) {
                        Data.newInvoice(c, "Legal fees", LocalDate.now(), LocalDate.now().plusDays(30), amount);
                    }
                    if (hearing.isSelected()) {
                        Data.newAppointment("First hearing", c, hd, ht, place, Appointment.TENTATIVE);
                    }
                    Data.commit("Added case " + c.code() + " — " + c.getTitle());
                    status("Case " + c.code() + " saved.");
                } else {
                    if (lw != existing.getLawyer()) {
                        for (int ix10 = 0; ix10 < Data.getAppointments().size(); ix10++) {
                            Appointment a = Data.getAppointments().get(ix10);
                            if (a.getRelatedCase() == existing && a.isUpcoming() && Data.findConflict(lw, a.getDate(), a.getTime(), a) != null) {
                                return lw.shortName() + " is already booked at the time of \"" + a.getEvent() + "\" on " + Ui.date(a.getDate()) + ".";
                            }
                        }
                    }
                    existing.setTitle(t);
                    existing.setType((String) type.getSelectedItem());
                    existing.setLawyer(lw);
                    existing.setOpposing(opposing.getText().trim());
                    existing.setCourt(court.getText().trim());
                    existing.setFiled(fd);
                    existing.setStatus((String) statusBox.getSelectedItem());
                    existing.setDescription(desc.getText().trim());
                    Data.commit("Edited case " + existing.code());
                    status("Case " + existing.code() + " updated.");
                }
                return null;
            }
        });
        f.section("Case information");
        f.add("Case title", title, 2);
        f.add("Case type", type);
        f.add("Client", clientBox);
        f.add("Assigned lawyer", lawyerBox);
        f.add("Opposing party", opposing);
        f.add("Court or office", court);
        f.add("Filing date (yyyy-mm-dd)", filed);
        f.add("Agreed fee (₱)", fee);
        if (add) {
            f.section("First hearing (optional)");
            f.add("", hearing);
            f.add("Hearing date", hDate);
            f.add("Hearing time", hTime);
            f.add("Hearing location", hPlace, 3);
        }
        f.section("Status");
        f.add("Case status", statusBox);
        f.add("Case description or notes", desc, 2);
        openForm(f);
    }

    // ------------------------------------------------------------------ status / remove
    void changeStatus() {
        if (!Data.getCurrentUser().can("UPDATE_STATUS")) return;
        Case c = (Case) pick("a case");
        if (c == null) return;
        JComboBox<String> box = Ui.combo(Case.STATUSES);
        box.setSelectedItem(c.getStatus());
        JPanel f = Ui.dialogForm();
        Ui.row(f, "Case:", Ui.label(c.code() + " " + c.getTitle(), Ui.PLAIN, Ui.TEXT));
        Ui.row(f, "New status:", box);
        if (!Ui.ask(this, "Change status", f)) return;
        String ns = (String) box.getSelectedItem();
        if (ns.equals(c.getStatus())) return;
        c.setStatus(ns);
        Data.commit("Case " + c.code() + " is now " + ns);
        refresh();
        status("Case " + c.code() + " is now " + ns + ".");
    }

    void removeCase() {
        if (!Data.getCurrentUser().can("REMOVE_CASE")) return;
        Case c = (Case) pick("a case");
        if (c == null) return;
        if (!Ui.confirm(this, "Remove case " + c.code() + " (" + c.getTitle() + ")?\nIts appointments, invoices and document records are removed too.")) return;
        Data.removeCase(c);   // also removes its appointments, invoices and document records
        Data.commit("Removed case " + c.code() + " — " + c.getTitle());
        refresh();
        status("Case " + c.code() + " removed.");
    }

    // ------------------------------------------------------------------ details
    static void showDetails(Case c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.code()).append("  —  ").append(c.getTitle()).append("\n\n");
        sb.append("Type:            ").append(c.getType()).append("\n");
        sb.append("Status:          ").append(c.getStatus()).append("\n");
        sb.append("Client:          ").append(c.getClient().getName()).append("\n");
        sb.append("Lawyer:          ").append(c.getLawyer().getName()).append("\n");
        sb.append("Opposing party:  ").append(c.getOpposing().isEmpty() ? "-" : c.getOpposing()).append("\n");
        sb.append("Court / office:  ").append(c.getCourt().isEmpty() ? "-" : c.getCourt()).append("\n");
        sb.append("Filed:           ").append(c.getFiled() == null ? "-" : Ui.date(c.getFiled())).append("\n");
        sb.append("Opened:          ").append(Ui.date(c.getOpened())).append("\n");
        sb.append("Agreed fee:      ").append(Ui.money(c.getFee())).append("\n");
        Appointment next = Data.nextHearing(c);
        sb.append("Next hearing:    ").append(next == null ? "-" : next.getEvent() + " on " + Ui.date(next.getDate()) + " " + Ui.time(next.getTime())).append("\n");
        double paid = 0, bal = 0;
        ArrayList<Invoice> ls11 = Data.invoicesOf(c);
        for (int ix11 = 0; ix11 < ls11.size(); ix11++) {
            Invoice i = ls11.get(ix11);
            paid += i.paid(); bal += i.balance();
        }
        sb.append("Paid / balance:  ").append(Ui.money(paid)).append(" / ").append(Ui.money(bal)).append("\n");
        int docs = 0;
        for (int ix12 = 0; ix12 < Data.getDocuments().size(); ix12++) {
            Doc d = Data.getDocuments().get(ix12);
            if (d.getRelatedCase() == c) docs++;
        }
        sb.append("Documents:       ").append(docs).append("\n");
        if (!c.getDescription().isEmpty()) sb.append("\nNotes:\n").append(c.getDescription()).append("\n");
        JTextArea a = new JTextArea(sb.toString());
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setBorder(new javax.swing.border.EmptyBorder(8, 8, 8, 8));
        JScrollPane sp = new JScrollPane(a);
        sp.setPreferredSize(new java.awt.Dimension(520, 340));
        JOptionPane.showMessageDialog(Shell.current, sp, "Case details", JOptionPane.PLAIN_MESSAGE);
    }
}
