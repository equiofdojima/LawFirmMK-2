import java.util.ArrayList;
import java.util.LinkedHashMap;
import javax.swing.JComboBox;

/** Case and billing figures grouped by type, lawyer, client or status. */
public class ReportsPage extends TablePage {
    private final JComboBox<String> groupBox = Ui.combo("By case type", "By lawyer", "By client", "By status");

    public ReportsPage() {
        super("ANALYTICS", "Reports",
                new String[]{"Group", "Cases", "Active", "Pending", "Handled", "Billed", "Paid", "Outstanding"},
                new int[]{170, 60, 60, 60, 60, 110, 110, 110}, false);
        groupBox.addActionListener(new RefreshListener(this));
        filters.add(groupBox, 0);
    }

    @Override
    protected void load() {
        // for every group: {active, pending, handled, billed, paid, outstanding}
        LinkedHashMap<String, double[]> groups = new LinkedHashMap<>();
        int mode = groupBox.getSelectedIndex();
        if (mode == 0) {
            for (int i = 0; i < Case.TYPES.length; i++) groups.put(Case.TYPES[i], new double[6]);
        }
        if (mode == 3) {
            for (int i = 0; i < Case.STATUSES.length; i++) groups.put(Case.STATUSES[i], new double[6]);
        }
        ArrayList<Case> mine = Data.visibleCases();
        for (int i = 0; i < mine.size(); i++) {
            Case c = mine.get(i);
            String key;
            if (mode == 0) key = c.getType();
            else if (mode == 1) key = c.getLawyer().getName();
            else if (mode == 2) key = c.getClient().getName();
            else key = c.getStatus();

            double[] a = groups.get(key);
            if (a == null) {
                a = new double[6];
                groups.put(key, a);
            }
            if (c.getStatus().equals(Case.ACTIVE)) a[0]++;
            else if (c.getStatus().equals(Case.PENDING)) a[1]++;
            else a[2]++;

            ArrayList<Invoice> invs = Data.invoicesOf(c);
            for (int j = 0; j < invs.size(); j++) {
                Invoice inv = invs.get(j);
                a[3] += inv.getAmount();
                a[4] += inv.paid();
                a[5] += inv.balance();
            }
        }

        double[] all = new double[6];
        ArrayList<String> names = new ArrayList<>(groups.keySet());
        for (int i = 0; i < names.size(); i++) {
            String key = names.get(i);
            double[] a = groups.get(key);
            if (a[0] + a[1] + a[2] == 0) continue; // nothing to show for this group
            addRow(key, key, (int) (a[0] + a[1] + a[2]), (int) a[0], (int) a[1], (int) a[2],
                    Ui.peso(a[3]), Ui.peso(a[4]), Ui.peso(a[5]));
            for (int j = 0; j < 6; j++) all[j] += a[j];
        }
        addRow("total", "All", (int) (all[0] + all[1] + all[2]), (int) all[0], (int) all[1], (int) all[2],
                Ui.peso(all[3]), Ui.peso(all[4]), Ui.peso(all[5]));
    }
}
