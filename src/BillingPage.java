import java.awt.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Invoices and payments: add, edit and delete invoices, record payments, print receipts and a summary. */
public class BillingPage extends TablePage {
    private final JLabel total = new JLabel("0"), received = new JLabel("0"), balance = new JLabel("0");
    private final JComboBox<String> statusBox = Ui.combo("All statuses", "Unpaid", "Partial", "Paid", "Overdue");

    public BillingPage() {
        super("FINANCIAL RECORDS", "Billing & Payments",
                new String[]{"Invoice ID", "Client", "Related Case", "Description", "Total Fees", "Paid", "Balance", "Due Date", "Status", ""},
                new int[]{85, 120, 170, 110, 90, 90, 90, 90, 70, 36}, true);
        User me = Data.getCurrentUser();

        JPanel cards = new JPanel(new GridLayout(1, 3, 14, 0));
        cards.setOpaque(false);
        cards.add(Ui.statCard("Total fees", total, Ui.NAVY));
        cards.add(Ui.statCard("Payments received", received, Ui.GREEN));
        cards.add(Ui.statCard("Outstanding balances", balance, Ui.GOLD));
        JLabel[] figures = {total, received, balance};
        for (int i = 0; i < figures.length; i++) figures[i].setFont(Ui.BOLD.deriveFont(22));
        setTop(cards);

        statusBox.addActionListener(new RefreshListener(this));
        filters.add(statusBox, 0);

        actions.add(Ui.button("Print Summary", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                printSummary();
            }
        }));
        if (me.can("EDIT_INVOICE")) {
            actions.add(Ui.button("Delete", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deleteInvoice();
                }
            }));
            actions.add(Ui.button("Edit", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    editInvoice();
                }
            }));
        }
        actions.add(Ui.button("Payments & Receipts", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPayments();
            }
        }));
        if (me.can("RECORD_PAYMENT")) {
            actions.add(Ui.button("Record Payment", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    recordPayment();
                }
            }));
        }
        if (me.can("ADD_INVOICE")) {
            actions.add(Ui.button("+ Add Invoice", true, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    openInvoiceForm(null);
                }
            }));
        }
    }

    @Override
    protected void load() {
        String s = (String) statusBox.getSelectedItem();
        ArrayList<Invoice> list = Data.visibleInvoices();
        for (int i = 0; i < list.size(); i++) {
            Invoice inv = list.get(i);
            if (!s.startsWith("All") && !inv.status().equals(s)) continue;
            addRow(inv, inv.code(), inv.getRelatedCase().getClient().getName(), inv.getRelatedCase().code() + " " + inv.getRelatedCase().getTitle(), inv.getDescription(),
                    Ui.peso(inv.getAmount()), Ui.peso(inv.paid()), Ui.peso(inv.balance()), inv.getDue(), inv.status(), "");
        }
    }

    @Override
    protected void onRefreshed() {
        double t = 0, p = 0, b = 0;
        ArrayList<Invoice> list = Data.visibleInvoices();
        for (int i = 0; i < list.size(); i++) {
            Invoice inv = list.get(i);
            t += inv.getAmount();
            p += inv.paid();
            b += inv.balance();
        }
        total.setText(Ui.money(t));
        received.setText(Ui.money(p));
        balance.setText(Ui.money(b));
        summary.setText(" ");
    }

    /** Double-click on a row: show its payments. */
    @Override
    protected void onOpen(Object ref) {
        new PaymentsDialog(this, (Invoice) ref).setVisible(true);
    }

    private void showPayments() {
        Invoice inv = (Invoice) pick("an invoice");
        if (inv != null) onOpen(inv);
    }

    // ------------------------------------------------------------------ record payment
    private void recordPayment() {
        if (!Data.getCurrentUser().can("RECORD_PAYMENT")) return;
        Invoice inv = (Invoice) pick("an invoice");
        if (inv == null) return;
        if (inv.balance() <= 0) { warn(inv.code() + " is already fully paid."); return; }
        JTextField amount = Ui.field(String.format("%.2f", inv.balance()));
        JComboBox<String> method = Ui.combo(Invoice.METHODS);
        JSpinner date = Ui.dateSpinner(LocalDate.now());
        JTextField payer = Ui.field(inv.getRelatedCase().getClient().getName());
        JPanel f = Ui.dialogForm();
        Ui.row(f, "Invoice:", Ui.label(inv.code() + "  (balance " + Ui.money(inv.balance()) + ")", Ui.PLAIN, Ui.TEXT));
        Ui.row(f, "Amount (₱) *:", amount);
        Ui.row(f, "Method:", method);
        Ui.row(f, "Date received:", date);
        Ui.row(f, "Paid by *:", payer);
        if (!Ui.ask(this, "Record payment", f)) return;

        double a;
        try {
            a = Invoice.round(Double.parseDouble(amount.getText().trim().replace(",", "")));
        } catch (NumberFormatException ex) {
            warn("Please enter a valid amount.");
            return;
        }
        LocalDate d = Ui.dateOf(date);
        String problem = inv.checkPayment(a, d, null);
        if (problem != null) { warn(problem); return; }
        if (payer.getText().trim().isEmpty()) { warn("Enter who paid."); return; }

        Payment p = Data.newPayment(inv, d, a, (String) method.getSelectedItem(), Data.getCurrentUser().getName(), payer.getText().trim());
        Data.commit("Recorded payment " + p.code() + " of " + Ui.money(a) + " on " + inv.code());
        refresh();
        status("Payment " + p.code() + " of " + Ui.money(a) + " recorded on " + inv.code() + ".");
        if (Ui.confirm(this, "Payment recorded as " + p.code() + ".\nShow the official receipt now?")) {
            Receipt.show(this, inv, p);
        }
    }

    // ------------------------------------------------------------------ add / edit / delete invoice
    private void editInvoice() {
        Invoice inv = (Invoice) pick("an invoice");
        if (inv != null) openInvoiceForm(inv);
    }

    private void deleteInvoice() {
        if (!Data.getCurrentUser().can("EDIT_INVOICE")) return;
        Invoice inv = (Invoice) pick("an invoice");
        if (inv == null) return;
        String warning = "Delete invoice " + inv.code() + " (" + Ui.money(inv.getAmount()) + ")?";
        if (!inv.getPayments().isEmpty()) warning += "\nIts " + inv.getPayments().size() + " recorded payment(s) are deleted too.";
        if (!Ui.confirm(this, warning)) return;
        if (!Ui.askAdminPassword(this, "Delete invoice " + inv.code())) return;
        Data.removeInvoice(inv);
        Data.commit("Deleted invoice " + inv.code() + " (" + Ui.money(inv.getAmount()) + ") of " + inv.getRelatedCase().code());
        refresh();
        status("Invoice " + inv.code() + " deleted.");
    }

    private void openInvoiceForm(Invoice existing) {
        if (!Data.getCurrentUser().can(existing == null ? "ADD_INVOICE" : "EDIT_INVOICE")) return;
        boolean add = existing == null;
        ArrayList<Case> cs = Data.visibleCases();
        if (cs.isEmpty()) { warn("There are no cases to bill."); return; }
        JComboBox<String> caseBox = Ui.combo(Ui.caseLabels(cs));
        JTextField desc = Ui.field(add ? "Legal fees" : existing.getDescription());
        JTextField amount = Ui.field(add ? "" : String.format("%.2f", existing.getAmount()));
        JSpinner issued = Ui.dateSpinner(add ? LocalDate.now() : existing.getIssued());
        JSpinner due = Ui.dateSpinner(add ? LocalDate.now().plusDays(30) : existing.getDue());
        if (!add) {
            caseBox.setSelectedIndex(cs.indexOf(existing.getRelatedCase()));
            caseBox.setEnabled(false); // an invoice stays with its case
        }
        Form f = new Form(this, add ? "FINANCIAL RECORDS" : "EDIT RECORD", add ? "Add Invoice" : "Edit " + existing.code(),
                add ? "Save Record" : "Save Changes", new Saver() {
            @Override
            public String save() {
                if (desc.getText().trim().isEmpty()) return "Enter a description.";
                double a;
                try { a = Invoice.round(Double.parseDouble(amount.getText().trim().replace(",", ""))); }
                catch (NumberFormatException ex) { return "Please enter a valid amount."; }
                if (a <= 0) return "The amount must be greater than zero.";
                LocalDate i = Ui.dateOf(issued), d = Ui.dateOf(due);
                if (d.isBefore(i)) return "The due date cannot be before the issue date.";
                if (add) {
                    Case c = cs.get(caseBox.getSelectedIndex());
                    Invoice inv = Data.newInvoice(c, desc.getText().trim(), i, d, a);
                    Data.commit("Added invoice " + inv.code() + " (" + Ui.money(inv.getAmount()) + ") for " + c.code());
                    status("Invoice " + inv.code() + " added.");
                } else {
                    if (a < existing.paid() - 0.004) {
                        return "The total cannot be less than the " + Ui.money(existing.paid()) + " already paid.";
                    }
                    existing.setDescription(desc.getText().trim());
                    existing.setAmount(a);
                    existing.setIssued(i);
                    existing.setDue(d);
                    Data.commit("Edited invoice " + existing.code());
                    status("Invoice " + existing.code() + " updated.");
                }
                return null;
            }
        });
        f.section("Invoice");
        f.add("Related case *", caseBox, 2);
        f.add("Total fees (₱) *", amount);
        f.add("Description *", desc, 2);
        f.add("Issue date", issued);
        f.add("Due date", due);
        openForm(f);
    }

    // ------------------------------------------------------------------ print summary
    private void printSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(Case.getFirmName()).append("\nBilling summary — ").append(Ui.date(LocalDate.now()))
                .append("\nPrepared for: ").append(Data.getCurrentUser().getName()).append("\n\n");
        sb.append(String.format("%-10s %-18s %-26s %13s %13s %13s  %-8s%n", "Invoice", "Client", "Case", "Total", "Paid", "Balance", "Status"));
        double t = 0, p = 0, b = 0;
        ArrayList<Invoice> list = Data.visibleInvoices();
        for (int i = 0; i < list.size(); i++) {
            Invoice inv = list.get(i);
            sb.append(String.format("%-10s %-18s %-26s %13s %13s %13s  %-8s%n", inv.code(), cut(inv.getRelatedCase().getClient().getName(), 18),
                    cut(inv.getRelatedCase().getTitle(), 26), Ui.money(inv.getAmount()), Ui.money(inv.paid()), Ui.money(inv.balance()), inv.status()));
            t += inv.getAmount();
            p += inv.paid();
            b += inv.balance();
        }
        sb.append(String.format("%n%-55s %13s %13s %13s%n", "TOTAL", Ui.money(t), Ui.money(p), Ui.money(b)));
        Ui.showPrintable(this, "Billing summary", sb.toString(), "billing-summary.txt", 780, 360);
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }
}

/** Lists the payments of one invoice. From here a payment can be printed as a receipt, edited or deleted. */
class PaymentsDialog extends JDialog {
    private final BillingPage page;
    private final Invoice invoice;
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel summary = Ui.label(" ", Ui.BOLD, Ui.NAVY);

    public PaymentsDialog(BillingPage page, Invoice invoice) {
        super(SwingUtilities.getWindowAncestor(page), "Payments of " + invoice.code(), ModalityType.APPLICATION_MODAL);
        this.page = page;
        this.invoice = invoice;

        String[] columns = {"Receipt No.", "Date", "Amount", "Method", "Paid by", "Recorded by"};
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 220));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(Ui.button("Receipt", false, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showReceipt();
            }
        }));
        if (Data.getCurrentUser().can("RECORD_PAYMENT")) {
            buttons.add(Ui.button("Edit", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    editPayment();
                }
            }));
            buttons.add(Ui.button("Delete", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deletePayment();
                }
            }));
        }
        buttons.add(Ui.button("Close", true, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        }));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(new EmptyBorder(14, 16, 14, 16));
        content.add(summary, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        reload();
        pack();
        setLocationRelativeTo(page);
    }

    private void reload() {
        model.setRowCount(0);
        for (int i = 0; i < invoice.getPayments().size(); i++) {
            Payment p = invoice.getPayments().get(i);
            model.addRow(new Object[]{p.code(), Ui.date(p.getDate()), Ui.money(p.getAmount()), p.getMethod(), p.getPayer(), p.getRecordedBy()});
        }
        summary.setText(invoice.code() + "   Total " + Ui.money(invoice.getAmount()) + "   Paid " + Ui.money(invoice.paid())
                + "   Balance " + Ui.money(invoice.balance()));
    }

    /** The payment on the highlighted row, or a warning and null. */
    private Payment selectedPayment() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= invoice.getPayments().size()) {
            Ui.warn(this, "Select a payment in the table first.");
            return null;
        }
        return invoice.getPayments().get(row);
    }

    private void showReceipt() {
        Payment p = selectedPayment();
        if (p != null) Receipt.show(this, invoice, p);
    }

    private void editPayment() {
        Payment p = selectedPayment();
        if (p == null) return;
        JTextField amount = Ui.field(String.format("%.2f", p.getAmount()));
        JComboBox<String> method = Ui.combo(Invoice.METHODS);
        method.setSelectedItem(p.getMethod());
        JSpinner date = Ui.dateSpinner(p.getDate());
        JTextField payer = Ui.field(p.getPayer());
        JPanel form = Ui.dialogForm();
        Ui.row(form, "Receipt:", Ui.label(p.code(), Ui.PLAIN, Ui.TEXT));
        Ui.row(form, "Amount (₱) *:", amount);
        Ui.row(form, "Method:", method);
        Ui.row(form, "Date received:", date);
        Ui.row(form, "Paid by *:", payer);
        if (!Ui.ask(this, "Edit payment", form)) return;

        double newAmount;
        try {
            newAmount = Invoice.round(Double.parseDouble(amount.getText().trim().replace(",", "")));
        } catch (NumberFormatException ex) {
            Ui.warn(this, "Please enter a valid amount.");
            return;
        }
        LocalDate newDate = Ui.dateOf(date);
        String problem = invoice.checkPayment(newAmount, newDate, p);
        if (problem != null) { Ui.warn(this, problem); return; }
        if (payer.getText().trim().isEmpty()) { Ui.warn(this, "Enter who paid."); return; }

        p.setAmount(newAmount);
        p.setDate(newDate);
        p.setMethod((String) method.getSelectedItem());
        p.setPayer(payer.getText().trim());
        Data.commit("Edited payment " + p.code() + " on " + invoice.code());
        reload();
        page.refresh();
    }

    private void deletePayment() {
        Payment p = selectedPayment();
        if (p == null) return;
        if (!Ui.confirm(this, "Delete payment " + p.code() + " of " + Ui.money(p.getAmount()) + "?")) return;
        if (!Ui.askAdminPassword(this, "Delete payment " + p.code())) return;
        invoice.removePayment(p);
        Data.commit("Deleted payment " + p.code() + " (" + Ui.money(p.getAmount()) + ") from " + invoice.code());
        reload();
        page.refresh();
    }
}

/** Builds and shows the official receipt of one payment. */
class Receipt {
    private Receipt() {}

    public static String text(Invoice inv, Payment p) {
        String line = "----------------------------------------------\n";
        StringBuilder sb = new StringBuilder();
        sb.append(Case.getFirmName().toUpperCase()).append("\n");
        sb.append("OFFICIAL RECEIPT\n").append(line);
        sb.append(String.format("Receipt No.   : %s%n", p.code()));
        sb.append(String.format("Date          : %s%n", Ui.date(p.getDate())));
        sb.append(String.format("Received from : %s%n", p.getPayer()));
        sb.append(String.format("Amount        : %s%n", Ui.money(p.getAmount())));
        sb.append(String.format("Payment method: %s%n", p.getMethod())).append(line);
        sb.append(String.format("Invoice       : %s (%s)%n", inv.code(), inv.getDescription()));
        sb.append(String.format("Case          : %s %s%n", inv.getRelatedCase().code(), inv.getRelatedCase().getTitle()));
        sb.append(String.format("Total fees    : %s%n", Ui.money(inv.getAmount())));
        sb.append(String.format("Balance after : %s%n", Ui.money(inv.balanceAfter(p)))).append(line);
        sb.append(String.format("Received by   : %s%n", p.getRecordedBy()));
        sb.append("Thank you.\n");
        return sb.toString();
    }

    public static void show(Component parent, Invoice inv, Payment p) {
        Ui.showPrintable(parent, "Receipt " + p.code(), text(inv, p), "receipt-" + p.code() + ".txt", 440, 330);
    }
}
