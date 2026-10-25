import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

// Admin-only screens: UsersPage, AssignmentsPage, ActivityPage.
// Top-level classes without "public": visible to the whole package by name.

/** Admin: create, edit, block / unblock, reset and delete accounts. */
// >>> OOP | INHERITANCE: Same parent as CasesPage: different rows and buttons, identical table behaviour.
class UsersPage extends TablePage {
    private final JComboBox<String> roleBox = Ui.combo("All roles", User.ADMIN, User.LAWYER);

    public UsersPage() {
        super("ADMINISTRATION", "User Accounts",
                new String[]{"Full Name", "User ID", "Username", "Email", "Phone", "Role", "Status"},
                new int[]{150, 70, 90, 180, 120, 80, 80}, true);
        roleBox.addActionListener(new RefreshListener(this));
        filters.add(roleBox, 0);
        actions.add(Ui.button("Delete", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                delete();
            }
        }));
        actions.add(Ui.button("Block / Unblock", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleBlock();
            }
        }));

        actions.add(Ui.button("Reset Password", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reset();
            }
        }));
        actions.add(Ui.button("Edit", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                edit();
            }
        }));
        actions.add(Ui.button("+ Add User", true, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openUserForm(null);
            }
        }));
    }

    @Override
    protected void load() {
        String r = (String) roleBox.getSelectedItem();
        for (int ix21 = 0; ix21 < Data.getUsers().size(); ix21++) {
            User u = Data.getUsers().get(ix21);
            if (!r.startsWith("All") && !u.getRole().equals(r)) continue;
            addRow(u, u.getName(), u.code(), u.getUsername(), u.getEmail(), u.getPhone(), u.getRole(), u.statusText());
        }
    }


    private boolean isSelf(User u) { return u == Data.getCurrentUser(); }

    private boolean lastAdmin(User u) { return u.getRole().equals(User.ADMIN) && u.isActive() && Data.countAdmins() <= 1; }

    private void edit() {
        User u = (User) pick("a user");
        if (u != null) openUserForm(u);
    }

    private void reset() {
        User u = (User) pick("a user");
        if (u == null) return;
        JPasswordField p1 = Ui.password(), p2 = Ui.password();
        JPanel f = Ui.dialogForm();
        Ui.row(f, "Account:", Ui.label(u.getName() + " (" + u.getUsername() + ")", Ui.PLAIN, Ui.TEXT));
        Ui.row(f, "New password (min 6):", p1);
        Ui.row(f, "Repeat password:", p2);
        if (!Ui.ask(this, "Reset password", f)) return;
        String a = new String(p1.getPassword());
        if (a.length() < 6) { warn("The password must be at least 6 characters."); return; }
        if (!a.equals(new String(p2.getPassword()))) { warn("The two passwords do not match."); return; }
        u.setPassword(a);
        Data.commit("Reset password of " + u.getUsername());
        status("Password reset for " + u.getUsername() + ".");
    }

    /**
     * Unblocks an account that was blocked after 3 wrong passwords (or clears an administrator's short lock);
     * on any other account it blocks it, so that it cannot sign in until it is unblocked again.
     */
    private void toggleBlock() {
        User u = (User) pick("a user");
        if (u == null) return;
        if (u.isBlocked() || u.lockSecondsLeft() > 0) {      // already blocked (or locked): let it in again
            u.unblock();
            Data.commit("Unblocked account " + u.getUsername());
            refresh();
            status(u.getUsername() + " unblocked. They can sign in again.");
            return;
        }
        if (isSelf(u)) { warn("You cannot block your own account."); return; }
        if (lastAdmin(u)) { warn("This is the last active administrator."); return; }
        if (!Ui.confirm(this, "Block " + u.getName() + "? They cannot sign in until you unblock the account.")) return;
        u.block();                                            // otherwise: block it
        Data.commit("Blocked account " + u.getUsername());
        refresh();
        status(u.getUsername() + " blocked.");
    }

    private void delete() {
        User u = (User) pick("a user");
        if (u == null) return;
        if (isSelf(u)) { warn("You cannot delete your own account."); return; }
        if (lastAdmin(u)) { warn("This is the last active administrator."); return; }
        for (int ix22 = 0; ix22 < Data.getCases().size(); ix22++) {
            Case c = Data.getCases().get(ix22);
            if (c.getLawyer() == u) {
                warn(u.getName() + " is linked to case " + c.code() + ". Reassign or remove the case first, or block the account instead.");
                return;
            }
        }
        if (!Ui.confirm(this, "Delete the account of " + u.getName() + " permanently?")) return;
        Data.removeUser(u);
        Data.commit("Deleted account " + u.getUsername());
        refresh();
        status("Account deleted.");
    }

    private void openUserForm(User existing) {
        if (!Data.getCurrentUser().can("MANAGE_USERS")) return;
        boolean add = existing == null;
        JTextField name = Ui.field(add ? "" : existing.getName());
        JTextField email = Ui.field(add ? "" : existing.getEmail());
        JTextField phone = Ui.field(add ? "" : existing.getPhone());
        JTextField username = Ui.field(add ? "" : existing.getUsername());
        JPasswordField pass = Ui.password();
        JComboBox<String> role = Ui.combo(User.LAWYER, User.ADMIN);
        JTextField id = Ui.field(add ? String.format("USR-%03d (assigned on save)", nextId()) : existing.code());
        id.setEditable(false);
        if (!add) {
            role.setSelectedItem(existing.getRole());
            username.setEnabled(false);
            role.setEnabled(!isSelf(existing));
        }

        Form f = new Form(this, add ? "NEW ACCOUNT" : "EDIT ACCOUNT", add ? "Add User / Create Account" : "Edit " + existing.getUsername(),
                add ? "Create Account" : "Save Changes", new Saver() {
            @Override
            public String save() {
                String n = name.getText().trim();
                String em = email.getText().trim();
                String un = username.getText().trim();
                String r = (String) role.getSelectedItem();
                if (n.isEmpty()) return "Enter the full name.";
                if (!User.isValidEmail(em)) return "Enter a valid email address.";
                if (add) {
                    if (!User.isValidUsername(un)) return "Username: at least 3 letters, digits or . _ -";
                    for (int ix23 = 0; ix23 < Data.getUsers().size(); ix23++) {
                        User o = Data.getUsers().get(ix23);
                        if (o.getUsername().equalsIgnoreCase(un)) return "That username is already taken.";
                    }
                    String p = new String(pass.getPassword());
                    if (p.length() < 6) return "The temporary password needs at least 6 characters.";
                    if (r.equals(User.LAWYER) && !n.startsWith("Atty.")) n = "Atty. " + n;
                    User u = Data.newUser(un, p, n, em, phone.getText().trim(), r);
                    Data.commit("Created " + r + " account " + u.getUsername());
                    status("Account " + u.getUsername() + " created.");
                } else {
                    if (!existing.getRole().equals(r) && lastAdmin(existing)) return "This is the last active administrator.";
                    if (!existing.getRole().equals(r) && r.equals(User.ADMIN)) {
                        // a lawyer who still handles cases must not be turned into an administrator
                        for (int ix24 = 0; ix24 < Data.getCases().size(); ix24++) {
                            Case c = Data.getCases().get(ix24);
                            if (c.getLawyer() == existing) return "They still handle case " + c.code() + ".";
                        }
                    }
                    if (r.equals(User.LAWYER) && !n.startsWith("Atty.")) n = "Atty. " + n;
                    existing.setName(n); existing.setEmail(em); existing.setPhone(phone.getText().trim()); existing.setRole(r);
                    Data.commit("Edited account " + existing.getUsername());
                    status("Account updated.");
                }
                return null;
            }
        });
        f.section("Account information");
        f.add("Full name *", name);
        f.add("Email address *", email);
        f.add("Phone", phone);
        f.add("Username *", username);
        if (add) f.add("Temporary password * (min 6)", pass);
        f.add("Role", role);
        f.add("User ID", id);
        openForm(f);
    }

    private static int nextId() {
        int m = 0;
        for (int ix26 = 0; ix26 < Data.getUsers().size(); ix26++) {
            User u = Data.getUsers().get(ix26);
            m = Math.max(m, u.getId());
        }
        return m + 1;
    }
}

/** Admin: how many cases each lawyer has, and moving a case to another lawyer. */
class AssignmentsPage extends TablePage {

    public AssignmentsPage() {
        super("TEAM", "Lawyer Assignments",
                new String[]{"Lawyer", "Username", "Email", "Active Cases", "Pending", "Handled", "Total Fees"},
                new int[]{150, 90, 190, 80, 70, 70, 110}, true);
        actions.add(Ui.button("Reassign Case", true, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reassign();
            }
        }));
    }

    @Override
    protected void load() {
        for (int i = 0; i < Data.getUsers().size(); i++) {
            User u = Data.getUsers().get(i);
            if (!u.getRole().equals(User.LAWYER) || !u.isActive()) continue;
            int active = 0, pending = 0, handled = 0;
            double fees = 0;
            for (int j = 0; j < Data.getCases().size(); j++) {
                Case c = Data.getCases().get(j);
                if (c.getLawyer() != u) continue;
                if (c.getStatus().equals(Case.ACTIVE)) active++;
                else if (c.getStatus().equals(Case.PENDING)) pending++;
                else handled++;
                fees += c.getFee();
            }
            addRow(u, u.getName(), u.getUsername(), u.getEmail(), active, pending, handled, Ui.peso(fees));
        }
    }

    private void reassign() {
        if (!Data.getCurrentUser().can("VIEW_ASSIGNMENTS")) return;
        if (Data.getCases().isEmpty()) { warn("There are no cases to reassign."); return; }
        ArrayList<User> lawyers = Data.usersByRole(User.LAWYER);
        if (lawyers.size() < 2) { warn("At least two lawyer accounts are needed to reassign a case."); return; }
        JComboBox<String> caseBox = Ui.combo(Ui.caseLabels(Data.getCases()));
        JComboBox<String> lawyerBox = Ui.combo(Ui.names(lawyers));
        JLabel current = Ui.label(" ", Ui.PLAIN, Ui.GRAY);
        ActionListener show = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                current.setText(Data.getCases().get(caseBox.getSelectedIndex()).getLawyer().getName());
            }
        };
        caseBox.addActionListener(show);
        show.actionPerformed(null);
        JPanel f = Ui.dialogForm();
        Ui.row(f, "Case:", caseBox);
        Ui.row(f, "Current lawyer:", current);
        Ui.row(f, "New lawyer:", lawyerBox);
        if (!Ui.ask(this, "Reassign case", f)) return;
        Case c = Data.getCases().get(caseBox.getSelectedIndex());
        User newLawyer = lawyers.get(lawyerBox.getSelectedIndex());
        if (newLawyer == c.getLawyer()) { warn("That lawyer already handles this case."); return; }
        // the new lawyer must be free at the times of the case's upcoming appointments
        for (int i = 0; i < Data.getAppointments().size(); i++) {
            Appointment a = Data.getAppointments().get(i);
            if (a.getRelatedCase() == c && a.isUpcoming()) {
                Appointment clash = Data.findConflict(newLawyer, a.getDate(), a.getTime(), a);
                if (clash != null) {
                    warn(newLawyer.shortName() + " already has \"" + clash.getEvent() + "\" on " + Ui.date(a.getDate()) + " at "
                            + Ui.time(a.getTime()) + ", the same time as \"" + a.getEvent() + "\".");
                    return;
                }
            }
        }
        User old = c.getLawyer();
        c.setLawyer(newLawyer);
        Data.commit("Reassigned " + c.code() + " from " + old.getName() + " to " + newLawyer.getName());
        refresh();
        status("Case " + c.code() + " reassigned to " + newLawyer.getName() + ".");
    }
}

/** Admin: who did what, newest first. */
class ActivityPage extends TablePage {
    private final DateTimeFormatter stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ActivityPage() {
        super("ADMINISTRATION", "Activity Log", new String[]{"Time", "User", "Activity"},
                new int[]{140, 150, 460}, true);
    }

    @Override
    protected void load() {
        for (int i = Data.getLog().size() - 1; i >= 0; i--) {
            String[] entry = Data.getLog().get(i); // {time, user, text}
            String when = entry[0];
            try {
                when = LocalDateTime.parse(entry[0]).format(stamp);
            } catch (RuntimeException e) {
                // keep the raw text if it is not a date
            }
            addRow(entry, when, entry[1], entry[2]);
        }
    }
}
