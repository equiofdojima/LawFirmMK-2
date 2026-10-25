import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.*;

/** The firm calendar: appointments and hearings. */
public class SchedulePage extends TablePage {
    private final JLabel bDay = new JLabel("--", SwingConstants.CENTER);
    private final JLabel bKicker = Ui.label(" ", Ui.SMALL, Ui.SOFT);
    private final JLabel bTitle = Ui.label(" ", Ui.SUB, Color.WHITE);
    private final JLabel bPlace = Ui.label(" ", Ui.SMALL, Ui.SOFT);
    private final JLabel bWeek = new JLabel("0", SwingConstants.CENTER);
    private final JLabel bFort = new JLabel("0", SwingConstants.CENTER);
    private final ArrayList<User> lawyers = Data.usersByRole(User.LAWYER);
    private final JComboBox<String> lawyerBox;
    private final JComboBox<String> periodBox = Ui.combo("Upcoming", "Past", "All");

    public SchedulePage() {
        super("FIRM CALENDAR", "Schedule",
                new String[]{"Date", "Time", "Event", "Related Case", "Location", "Assigned Lawyer", "Status", ""},
                new int[]{90, 70, 140, 170, 140, 120, 80, 36}, true);
        setTop(buildBanner());
        User me = Data.getCurrentUser();

        String[] names = new String[lawyers.size() + 1];
        names[0] = "All lawyers";
        for (int i = 0; i < lawyers.size(); i++) names[i + 1] = lawyers.get(i).shortName();
        lawyerBox = Ui.combo(names);
        lawyerBox.addActionListener(new RefreshListener(this));
        periodBox.addActionListener(new RefreshListener(this));
        if (me.getRole().equals(User.ADMIN)) filters.add(lawyerBox, 0);
        filters.add(periodBox, 0);

        if (me.can("EDIT_APPOINTMENT")) {
            actions.add(Ui.button("Cancel Appointment", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    cancelSelected();
                }
            }));
            actions.add(Ui.button("Delete", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    deleteSelected();
                }
            }));
            actions.add(Ui.button("Edit", false, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    editSelected();
                }
            }));
            actions.add(Ui.button("+ Add Appointment", true, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    openAppointmentForm(null);
                }
            }));
        }
    }

    @Override
    protected void load() {
        LocalDate today = LocalDate.now();
        int li = lawyerBox.getSelectedIndex();
        String period = (String) periodBox.getSelectedItem();
        ArrayList<Appointment> ls13 = Data.visibleAppointments();
        for (int ix13 = 0; ix13 < ls13.size(); ix13++) {
            Appointment a = ls13.get(ix13);
            if (li > 0 && Data.getCurrentUser().getRole().equals(User.ADMIN) && a.getLawyer() != lawyers.get(li - 1)) continue;
            boolean upcoming = !a.getDate().isBefore(today);
            if (period.equals("Upcoming") && !upcoming) continue;
            if (period.equals("Past") && upcoming) continue;
            addRow(a, a.getDate(), a.getTime(), a.getEvent(), a.getRelatedCase().getTitle(), a.getLocation(), a.getLawyer().shortName(), a.getStatus(), "");
        }
    }

    @Override
    protected void onRefreshed() {
        updateBanner();
    }

    @Override
    protected void onOpen(Object ref) {
        showDetails((Appointment) ref);
    }

    // ------------------------------------------------------------------ actions
    private void editSelected() {
        Appointment a = (Appointment) pick("an appointment");
        if (a != null) openAppointmentForm(a);
    }

    private void cancelSelected() {
        Appointment a = (Appointment) pick("an appointment");
        if (a == null) return;
        if (a.getStatus().equals(Appointment.CANCELLED)) { warn("That appointment is already cancelled."); return; }
        if (!Ui.confirm(this, "Cancel \"" + a.getEvent() + "\" on " + Ui.date(a.getDate()) + "?")) return;
        a.setStatus(Appointment.CANCELLED);
        Data.commit("Cancelled appointment " + a.getEvent() + " (" + a.getRelatedCase().code() + ")");
        refresh();
        status(a.getEvent() + " cancelled.");
    }

    private void deleteSelected() {
        Appointment a = (Appointment) pick("an appointment");
        if (a == null) return;
        if (!Ui.confirm(this, "Delete \"" + a.getEvent() + "\" permanently?")) return;
        Data.removeAppointment(a);
        Data.commit("Deleted appointment " + a.getEvent() + " (" + a.getRelatedCase().code() + ")");
        refresh();
        status("Appointment deleted.");
    }

    void openAppointmentForm(Appointment existing) {
        if (!Data.getCurrentUser().can("EDIT_APPOINTMENT")) return;
        boolean add = existing == null;
        ArrayList<Case> cs = new ArrayList<>();
        ArrayList<Case> ls14 = Data.visibleCases();
        for (int ix14 = 0; ix14 < ls14.size(); ix14++) {
            Case c = ls14.get(ix14);
            if (!c.getStatus().equals(Case.HANDLED) || (!add && c == existing.getRelatedCase())) cs.add(c);
        }
        if (cs.isEmpty()) { warn("There are no open cases to schedule an appointment for."); return; }

        JComboBox<String> event = Ui.combo("Hearing", "Pre-trial Conference", "Client Consultation", "Mediation Session",
                "Evidence Submission", "Mandatory Conference", "Other");
        event.setEditable(true);
        JComboBox<String> caseBox = Ui.combo(Ui.caseLabels(cs));
        JSpinner date = Ui.dateSpinner(add ? LocalDate.now().plusDays(1) : existing.getDate());
        JSpinner time = Ui.timeSpinner(add ? LocalTime.of(9, 0) : existing.getTime());
        JTextField place = Ui.field(add ? "" : existing.getLocation());
        JComboBox<String> statusBox = Ui.combo(Appointment.CONFIRMED, Appointment.TENTATIVE, Appointment.CANCELLED);
        JLabel lawyerLabel = Ui.label(" ", Ui.PLAIN, Ui.TEXT);
        lawyerLabel.setBorder(Ui.inputBorder());
        if (!add) {
            event.setSelectedItem(existing.getEvent());
            caseBox.setSelectedIndex(cs.indexOf(existing.getRelatedCase()));
            statusBox.setSelectedItem(existing.getStatus());
        }
        ActionListener showLawyer = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                lawyerLabel.setText(cs.get(caseBox.getSelectedIndex()).getLawyer().getName());
            }
        };
        caseBox.addActionListener(showLawyer);
        showLawyer.actionPerformed(null);

        Form f = new Form(this, "CREATE RECORD", add ? "Add Appointment" : "Edit Appointment", "Save Record", new Saver() {
            @Override
            public String save() {
                String ev = String.valueOf(event.getEditor().getItem()).trim();
                if (ev.isEmpty()) return "Enter the event type.";
                if (place.getText().trim().isEmpty()) return "Enter the location.";
                LocalDate d = Ui.dateOf(date);
                LocalTime t = Ui.timeOf(time);
                Case c = cs.get(caseBox.getSelectedIndex());
                String st = (String) statusBox.getSelectedItem();
                boolean moved = add || !d.equals(existing.getDate()) || !t.equals(existing.getTime());
                if (moved && d.isBefore(LocalDate.now()) && !st.equals(Appointment.CANCELLED)) return "The date cannot be in the past.";
                if (!st.equals(Appointment.CANCELLED)) {
                    Appointment clash = Data.findConflict(c.getLawyer(), d, t, existing);
                    if (clash != null) return c.getLawyer().shortName() + " already has \"" + clash.getEvent() + "\" at that date and time.";
                }
                if (add) {
                    Appointment a = Data.newAppointment(ev, c, d, t, place.getText().trim(), st);
                    Data.commit("Added appointment " + a.getEvent() + " (" + c.code() + ")");
                    status("Appointment added: " + a.getEvent() + ".");
                } else {
                    existing.setEvent(ev); existing.setRelatedCase(c); existing.setDate(d); existing.setTime(t);
                    existing.setLocation(place.getText().trim()); existing.setStatus(st);
                    Data.commit("Edited appointment " + ev + " (" + c.code() + ")");
                    status("Appointment updated.");
                }
                return null;
            }
        });
        f.section("Appointment details");
        f.add("Event type", event);
        f.add("Related case", caseBox, 2);
        f.add("Date", date);
        f.add("Time (24-hour)", time);
        f.add("Status", statusBox);
        f.add("Location", place, 2);
        f.add("Assigned lawyer (follows the case)", lawyerLabel);
        openForm(f);
    }

    private void showDetails(Appointment a) {
        String msg = a.getEvent() + "\n" + a.getRelatedCase().getTitle() + " (" + a.getRelatedCase().code() + ")\n"
                + Ui.date(a.getDate()) + ", " + Ui.time(a.getTime()) + "\n" + a.getLocation() + "\n"
                + a.getLawyer().getName() + "\nStatus: " + a.getStatus();
        if (!Data.getCurrentUser().can("EDIT_APPOINTMENT")) { Ui.info(this, msg, "Appointment"); return; }
        Object[] options = {"Confirm", "Mark Tentative", "Edit...", "Close"};
        int r = JOptionPane.showOptionDialog(this, msg, "Appointment", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[3]);
        if (r == 0) a.setStatus(Appointment.CONFIRMED);
        else if (r == 1) a.setStatus(Appointment.TENTATIVE);
        else if (r == 2) { openAppointmentForm(a); return; }
        else return;
        Data.commit("Appointment " + a.getEvent() + " is now " + a.getStatus());
        refresh();
    }

    // ------------------------------------------------------------------ dark "next event" banner
    private JPanel buildBanner() {
        JPanel b = new JPanel(new BorderLayout());
        b.setBackground(Ui.NAVY);
        b.setBorder(new EmptyBorder(14, 18, 14, 8));
        b.setPreferredSize(new Dimension(100, 84));
        bDay.setFont(Ui.BOLD.deriveFont(20));
        bDay.setForeground(Ui.NAVY);
        bDay.setOpaque(true);
        bDay.setBackground(Color.WHITE);
        bDay.setPreferredSize(new Dimension(56, 54));
        JPanel text = new JPanel(new GridLayout(3, 1));
        text.setOpaque(false);
        text.setBorder(new EmptyBorder(0, 14, 0, 0));
        text.add(bKicker);
        text.add(bTitle);
        text.add(bPlace);
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(bDay, BorderLayout.WEST);
        left.add(text, BorderLayout.CENTER);
        JPanel stats = new JPanel(new GridLayout(1, 2));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(240, 10));
        stats.add(bannerStat(bWeek, "This week"));
        stats.add(bannerStat(bFort, "Next 14 days"));
        b.add(left, BorderLayout.CENTER);
        b.add(stats, BorderLayout.EAST);
        return b;
    }

    private static JPanel bannerStat(JLabel num, String caption) {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setOpaque(false);
        num.setFont(Ui.BOLD.deriveFont(22));
        num.setForeground(Color.WHITE);
        JLabel cap = Ui.label(caption, Ui.SMALL, Ui.SOFT);
        cap.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(num);
        p.add(cap);
        return p;
    }

    private void updateBanner() {
        Appointment next = null;
        int week = 0, fortnight = 0;
        LocalDate today = LocalDate.now();
        ArrayList<Appointment> ls15 = Data.visibleAppointments();
        for (int ix15 = 0; ix15 < ls15.size(); ix15++) {
            Appointment a = ls15.get(ix15);
            if (!a.isUpcoming()) continue;
            if (next == null) next = a;
            if (!a.getDate().isAfter(today.plusDays(7))) week++;
            if (!a.getDate().isAfter(today.plusDays(14))) fortnight++;
        }
        if (next == null) {
            bDay.setText("--");
            bKicker.setText("No upcoming events");
            bTitle.setText("Nothing scheduled");
            bPlace.setText(" ");
        } else {
            bDay.setText("<html><center>" + next.getDate().getDayOfMonth() + "<br><font size=2>"
                    + next.getDate().getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).toUpperCase()
                    + "</font></center></html>");
            bKicker.setText("Next event \u00B7 " + Ui.time(next.getTime()));
            bTitle.setText(next.getEvent() + " \u2014 " + next.getRelatedCase().getTitle());
            bPlace.setText(next.getLocation());
        }
        bWeek.setText(String.valueOf(week));
        bFort.setText(String.valueOf(fortnight));
    }
}
