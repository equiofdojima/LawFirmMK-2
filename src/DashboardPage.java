import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/** Overview with figures, the next hearings and the tasks that need attention. */
// >>> OOP | INHERITANCE: Extends Page directly (it is not a table) and writes its own refresh().
public class DashboardPage extends Page {
    private final JLabel[] values = new JLabel[6];
    private final String[] captions;
    private final DefaultTableModel upcoming;
    private final JPanel tasks = new JPanel();
    private final boolean admin = Data.getCurrentUser().getRole().equals(User.ADMIN);

    public DashboardPage() {
        User me = Data.getCurrentUser();
        captions = admin
                ? new String[]{"Total Cases", "Active Cases", "Total Users", "Lawyers", "Clients", "Outstanding Balance"}
                : new String[]{"Active Cases", "My Clients", "Pending Cases", "Upcoming Hearings"};

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        east.setOpaque(false);
        if (me.can("ADD_CASE")) {
            Btn b = Ui.primary("+ New case");
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Shell.current.navigate("Case Management");
                    Page p = Shell.current.page("Case Management");
                    if (p instanceof CasesPage) ((CasesPage) p).addCase();
                }
            });
            east.add(b);
        }
        root.add(Ui.heading("OVERVIEW", admin ? "Administrator Dashboard" : "Dashboard", east), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);

        int n = captions.length;
        JPanel stats = new JPanel(new GridLayout(admin ? 2 : 1, admin ? 3 : n, 14, 14));
        stats.setOpaque(false);
        for (int i = 0; i < n; i++) {
            values[i] = new JLabel("0");
            stats.add(Ui.statCard(captions[i], values[i], admin ? Ui.GOLD : Ui.NAVY));
        }
        body.add(stats, BorderLayout.NORTH);

        String[] cols = {"Date & Time", "Event", "Case", "Lawyer", "Status"};
        upcoming = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(upcoming);
        t.setRowHeight(30);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setDefaultRenderer(new HeaderRenderer(null));
        t.getTableHeader().setPreferredSize(new Dimension(0, 28));
        for (int i = 0; i < cols.length; i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(cols[i].equals("Status") ? new CellRenderer(CellRenderer.BADGE) : new CellRenderer(CellRenderer.TEXT));
        }
        int[] w = {170, 130, 150, 110, 80};
        for (int i = 0; i < w.length; i++) t.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(new MatteBorder(1, 0, 0, 0, Ui.LINE));
        sp.getViewport().setBackground(Color.WHITE);
        JPanel hearings = Ui.section("Upcoming hearings",
                Ui.link("View schedule  >", "Schedule"), sp);

        tasks.setLayout(new BoxLayout(tasks, BoxLayout.Y_AXIS));
        tasks.setOpaque(false);
        tasks.setBorder(new EmptyBorder(0, 12, 10, 12));
        JScrollPane tsp = new JScrollPane(tasks);
        tsp.setBorder(null);
        tsp.setOpaque(false);
        tsp.getViewport().setOpaque(false);
        JPanel pending = Ui.section("Pending tasks", Ui.link("View cases  >", "Case Management"), tsp);

        JPanel lower = new JPanel(new GridBagLayout());
        lower.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH; g.weighty = 1;
        g.gridx = 0; g.weightx = 2; g.insets = new Insets(0, 0, 0, 14);
        lower.add(hearings, g);
        g.gridx = 1; g.weightx = 1; g.insets = new Insets(0, 0, 0, 0);
        lower.add(pending, g);
        body.add(lower, BorderLayout.CENTER);

        root.add(body, BorderLayout.CENTER);
        setList(root);
    }

    @Override
    public void refresh() {
        ArrayList<Case> mine = Data.visibleCases();
        int active = 0, pend = 0;
        LinkedHashSet<Client> clients = new LinkedHashSet<>();
        for (int ix1 = 0; ix1 < mine.size(); ix1++) {
            Case c = mine.get(ix1);
            if (c.getStatus().equals(Case.ACTIVE)) active++;
            if (c.getStatus().equals(Case.PENDING)) pend++;
            clients.add(c.getClient());
        }
        int hearings = 0;
        double outstanding = 0;
        ArrayList<Invoice> ls2 = Data.visibleInvoices();
        for (int ix2 = 0; ix2 < ls2.size(); ix2++) {
            Invoice i = ls2.get(ix2);
            outstanding += i.balance();
        }

        upcoming.setRowCount(0);
        int listed = 0;
        ArrayList<Appointment> ls3 = Data.visibleAppointments();
        for (int ix3 = 0; ix3 < ls3.size(); ix3++) {
            Appointment a = ls3.get(ix3);
            if (!a.isUpcoming()) continue;
            hearings++;
            if (listed++ < 6) {
                upcoming.addRow(new Object[]{Ui.date(a.getDate()) + "  " + Ui.time(a.getTime()), a.getEvent(),
                        a.getRelatedCase().getTitle(), a.getLawyer().shortName(), a.getStatus()});
            }
        }

        if (admin) {
            int lawyers = Data.usersByRole(User.LAWYER).size(), cl = Data.getClients().size();
            set(0, mine.size()); set(1, active); set(2, Data.getUsers().size()); set(3, lawyers); set(4, cl);
            values[5].setText(Ui.money(outstanding));
        } else {
            set(0, active); set(1, clients.size()); set(2, pend); set(3, hearings);
        }
        buildTasks(mine);
    }

    private void set(int i, int v) { values[i].setText(String.valueOf(v)); }

    private void buildTasks(ArrayList<Case> mine) {
        tasks.removeAll();
        int shown = 0;
        for (int ix4 = 0; ix4 < mine.size(); ix4++) {
            Case c = mine.get(ix4);
            if (c.getStatus().equals(Case.PENDING)) {
                shown += task(tasks, "Pending case: " + c.getTitle(), c.code() + " · not yet activated");
            }
        }
        ArrayList<Invoice> ls5 = Data.visibleInvoices();
        for (int ix5 = 0; ix5 < ls5.size(); ix5++) {
            Invoice i = ls5.get(ix5);
            if (i.status().equals("Overdue")) {
                shown += task(tasks, "Collect " + i.code() + " — " + Ui.money(i.balance()),
                        "Overdue since " + Ui.date(i.getDue()));
            }
        }
        ArrayList<Appointment> ls6 = Data.visibleAppointments();
        for (int ix6 = 0; ix6 < ls6.size(); ix6++) {
            Appointment a = ls6.get(ix6);
            if (a.isUpcoming() && a.getStatus().equals(Appointment.TENTATIVE)) {
                shown += task(tasks, "Confirm: " + a.getEvent(), Ui.date(a.getDate()) + " · " + a.getRelatedCase().getTitle());
            }
        }
        LocalDate soon = LocalDate.now().plusDays(2);
        ArrayList<Appointment> ls7 = Data.visibleAppointments();
        for (int ix7 = 0; ix7 < ls7.size(); ix7++) {
            Appointment a = ls7.get(ix7);
            if (a.isUpcoming() && !a.getDate().isAfter(soon)) {
                shown += task(tasks, "Coming up: " + a.getEvent(), Ui.date(a.getDate()) + " " + Ui.time(a.getTime()));
            }
        }
        if (shown == 0) {
            JLabel l = Ui.label("Nothing needs your attention.", Ui.PLAIN, Ui.GRAY);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            tasks.add(l);
        }
        tasks.revalidate();
        tasks.repaint();
    }

    private static int task(JPanel into, String title, String detail) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Ui.HEAD), new EmptyBorder(6, 0, 6, 0)));
        JLabel a = Ui.label(title, Ui.BOLD, Ui.TEXT);
        JLabel b = Ui.label(detail, Ui.SMALL, Ui.GRAY);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(a);
        p.add(b);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        into.add(p);
        return 1;
    }
}
