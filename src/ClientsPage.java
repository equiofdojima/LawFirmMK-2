import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.swing.JTextField;

/** The people the firm represents. Clients are records only (they do not sign in). */
public class ClientsPage extends TablePage {

    public ClientsPage() {
        super("DIRECTORY", "Client Contacts",
                new String[]{"Client ID", "Client", "Email", "Phone", "Active Cases", "Total Cases", "Balance Due", "Assigned Lawyers"},
                new int[]{70, 140, 170, 120, 90, 90, 100, 190}, true);
        User me = Data.getCurrentUser();
        if (me.can("EDIT_CLIENT")) {
            actions.add(Ui.button("Delete", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deleteClient();
                }
            }));
            actions.add(Ui.button("Edit", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    editClient();
                }
            }));
            actions.add(Ui.button("+ Add Client", true, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    openClientForm(null);
                }
            }));
        }
    }

    @Override
    protected void load() {
        ArrayList<Case> mine = Data.visibleCases();
        ArrayList<Invoice> invs = Data.visibleInvoices();
        for (int i = 0; i < Data.getClients().size(); i++) {
            Client k = Data.getClients().get(i);
            int active = 0, total = 0;
            double due = 0;
            LinkedHashSet<String> lawyers = new LinkedHashSet<>();
            for (int j = 0; j < mine.size(); j++) {
                Case c = mine.get(j);
                if (c.getClient() != k) continue;
                total++;
                if (c.getStatus().equals(Case.ACTIVE)) active++;
                lawyers.add(c.getLawyer().shortName());
            }
            for (int j = 0; j < invs.size(); j++) {
                Invoice inv = invs.get(j);
                if (inv.getRelatedCase().getClient() == k) due += inv.balance();
            }
            addRow(k, k.code(), k.getName(), k.getEmail(), k.getPhone(), active, total, Ui.peso(due), String.join(", ", lawyers));
        }
    }

    private void editClient() {
        Client k = (Client) pick("a client");
        if (k != null) openClientForm(k);
    }

    private void deleteClient() {
        if (!Data.getCurrentUser().can("EDIT_CLIENT")) return;
        Client k = (Client) pick("a client");
        if (k == null) return;
        for (int i = 0; i < Data.getCases().size(); i++) {
            Case c = Data.getCases().get(i);
            if (c.getClient() == k) {
                warn(k.getName() + " is the client of case " + c.code() + ". Remove or reassign that case first.");
                return;
            }
        }
        if (!Ui.confirm(this, "Delete the client " + k.getName() + " permanently?")) return;
        Data.removeClient(k);
        Data.commit("Deleted client " + k.getName());
        refresh();
        status("Client deleted.");
    }

    private void openClientForm(Client existing) {
        if (!Data.getCurrentUser().can("EDIT_CLIENT")) return;
        boolean add = existing == null;
        JTextField name = Ui.field(add ? "" : existing.getName());
        JTextField email = Ui.field(add ? "" : existing.getEmail());
        JTextField phone = Ui.field(add ? "" : existing.getPhone());

        Form f = new Form(this, add ? "NEW CLIENT" : "EDIT CLIENT", add ? "Add Client" : "Edit " + existing.code(),
                add ? "Save Record" : "Save Changes", new Saver() {
            @Override
            public String save() {
                String n = name.getText().trim();
                String em = email.getText().trim();
                if (n.isEmpty()) return "Enter the client's name.";
                if (!em.isEmpty() && (!em.contains("@") || !em.contains("."))) return "Enter a valid email address (or leave it empty).";
                for (int i = 0; i < Data.getClients().size(); i++) {
                    Client other = Data.getClients().get(i);
                    if (other != existing && other.getName().equalsIgnoreCase(n)) return "A client with that name already exists.";
                }
                if (add) {
                    Client k = Data.newClient(n, em, phone.getText().trim());
                    Data.commit("Added client " + k.getName());
                    status("Client " + k.getName() + " added.");
                } else {
                    existing.setName(n);
                    existing.setEmail(em);
                    existing.setPhone(phone.getText().trim());
                    Data.commit("Edited client " + existing.getName());
                    status("Client updated.");
                }
                return null;
            }
        });
        f.section("Client information");
        f.add("Full name *", name, 2);
        f.add("Email address", email);
        f.add("Phone", phone);
        openForm(f);
    }
}
