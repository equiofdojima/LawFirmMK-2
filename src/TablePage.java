import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/** heading + action buttons, a toolbar (search, filters, export), a sortable paged table and a pager. */
// >>> OOP | INHERITANCE: Multi-level inheritance: JPanel > Page > TablePage > CasesPage, ClientsPage, UsersPage ... The table, search, sort, paging and export are written once here.
public abstract class TablePage extends Page {
    static final int PAGE_SIZE = 10;

    protected final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    protected final JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    protected final JLabel summary = Ui.label(" ", Ui.BOLD, Ui.NAVY);
    protected final JTextField search;
    protected final DefaultTableModel model;
    protected final JTable table;
    protected final String[] columns;
    private final String exportName;

    // >>> OOP | ENCAPSULATION: The table's data is private. Subclasses add rows only through addRow().
    private final ArrayList<Object[]> rows = new ArrayList<>();
    private final ArrayList<Object> refs = new ArrayList<>();
    private final ArrayList<Integer> order = new ArrayList<>();   // filtered + sorted row indexes
    private final ArrayList<Object> shown = new ArrayList<>();    // refs of the current page
    private final JPanel north = new JPanel(new BorderLayout(0, 14));
    private final JPanel pager = new JPanel(new BorderLayout());
    private int pageNo = 0;
    int sortCol = -1;
    boolean sortAsc = true;

    protected TablePage(String eyebrow, String title, String[] columns, int[] widths, boolean searchable) {
        this.columns = columns;
        this.exportName = title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        JPanel list = new JPanel(new BorderLayout(0, 14));
        list.setOpaque(false);
        list.setBorder(new EmptyBorder(20, 24, 20, 24));

        actions.setOpaque(false);
        north.setOpaque(false);
        north.add(Ui.heading(eyebrow, title, actions), BorderLayout.NORTH);
        list.add(north, BorderLayout.NORTH);

        // toolbar
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setBackground(Color.WHITE);
        toolbar.setBorder(new CompoundBorder(new MatteBorder(1, 1, 0, 1, Ui.LINE), new EmptyBorder(10, 12, 10, 12)));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        if (searchable) {
            search = Ui.field("");
            search.setPreferredSize(new Dimension(220, 28));
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void changed() { pageNo = 0; render(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            });
            Ui.tip(search, "Type to filter the rows (" + Ui.MOD + "F to jump here, Esc to clear)");
            search.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE && !search.getText().isEmpty()) {
                        search.setText("");
                        e.consume();
                    }
                }
            });
            left.add(Ui.label("Search:", Ui.BOLD, Ui.NAVY));
            left.add(search);
        } else {
            search = null;
        }
        left.add(summary);
        filters.setOpaque(false);
        Btn export = Ui.secondary("Export");
        export.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportText();
            }
        });
        filters.add(export);
        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(filters, BorderLayout.EAST);

        // table
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer(this));
        header.setPreferredSize(new Dimension(0, 30));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (int i = 0; i < columns.length; i++) {
            TableColumn tc = table.getColumnModel().getColumn(i);
            String name = columns[i];
            if (name.equals("Status") || name.equals("Role")) tc.setCellRenderer(new CellRenderer(CellRenderer.BADGE));
            else if (name.isEmpty()) { tc.setCellRenderer(new CellRenderer(CellRenderer.ARROW)); tc.setMaxWidth(36); tc.setPreferredWidth(36); }
            else tc.setCellRenderer(new CellRenderer(CellRenderer.TEXT));
            if (widths != null && i < widths.length && !name.isEmpty()) tc.setPreferredWidth(widths[i]);
        }
        header.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int c = table.columnAtPoint(e.getPoint());
                if (c < 0 || columns[c].isEmpty()) return;
                if (sortCol == c) sortAsc = !sortAsc; else { sortCol = c; sortAsc = true; }
                render();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                int c = table.columnAtPoint(e.getPoint());
                boolean chevron = c >= 0 && columns[c].isEmpty();
                if (e.getClickCount() == 2 || chevron) {
                    table.setRowSelectionInterval(r, r);
                    Object o = selected();
                    if (o != null) onOpen(o);
                }
            }
        });
        header.setToolTipText("Click a heading to sort; click again to reverse");
        Ui.tip(table, "Double-click or press Enter to open a row; Delete removes it; " + Ui.MOD + "E edits it");
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "openRow");
        table.getActionMap().put("openRow", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Object o = selected();
                if (o != null) onOpen(o);
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteRow");
        table.getActionMap().put("deleteRow", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                shortcutDelete();
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(Ui.LINE, 1));
        scroll.getViewport().setBackground(Color.WHITE);

        pager.setBackground(Color.WHITE);
        pager.setBorder(new CompoundBorder(new MatteBorder(0, 1, 1, 1, Ui.LINE), new EmptyBorder(8, 12, 8, 12)));

        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.add(toolbar, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(pager, BorderLayout.SOUTH);
        list.add(card, BorderLayout.CENTER);
        setList(list);
    }

    protected void setTop(JComponent c) { north.add(c, BorderLayout.CENTER); }

    /** Fill the rows here with addRow(...). */
    // >>> OOP | ABSTRACTION: Template method: TablePage does all the work and calls load(); each subclass only says WHICH rows to show.
    protected abstract void load();

    protected void onRefreshed() {}

    /** Double-click / arrow on a row. */
    protected void onOpen(Object ref) {}

    protected void addRow(Object ref, Object... cells) {
        rows.add(cells);
        refs.add(ref);
    }

    protected static String text(Object cell) {
        if (cell == null) return "";
        if (cell instanceof LocalDate) return Ui.date((LocalDate) cell);
        if (cell instanceof LocalTime) return Ui.time((LocalTime) cell);
        if (cell instanceof Double) return Ui.money((Double) cell);
        return cell.toString();
    }

    @Override
    public void refresh() {
        rows.clear();
        refs.clear();
        load();
        render();
        onRefreshed();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Comparable && a.getClass() == b.getClass()) return ((Comparable) a).compareTo(b);
        return text(a).compareToIgnoreCase(text(b));
    }

    private void render() {
        order.clear();
        String q = search == null ? "" : search.getText().trim().toLowerCase();
        for (int i = 0; i < rows.size(); i++) {
            if (!q.isEmpty() && !matches(rows.get(i), q)) continue;
            order.add(i);
        }
        if (sortCol >= 0) {
            // insertion sort of the row numbers (stable, easy to follow)
            for (int a = 1; a < order.size(); a++) {
                int item = order.get(a);
                int b = a - 1;
                while (b >= 0) {
                    int c = compare(rows.get(order.get(b))[sortCol], rows.get(item)[sortCol]);
                    boolean wrongOrder = sortAsc ? c > 0 : c < 0;
                    if (!wrongOrder) break;
                    order.set(b + 1, order.get(b));
                    b--;
                }
                order.set(b + 1, item);
            }
        }
        int pages = Math.max(1, (order.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pageNo >= pages) pageNo = pages - 1;
        model.setRowCount(0);
        shown.clear();
        for (int i = pageNo * PAGE_SIZE; i < Math.min(order.size(), (pageNo + 1) * PAGE_SIZE); i++) {
            int idx = order.get(i);
            model.addRow(rows.get(idx));
            shown.add(refs.get(idx));
        }
        buildPager(pages);
        table.getTableHeader().repaint();
    }

    private boolean matches(Object[] row, String q) {
        for (int ix1 = 0; ix1 < row.length; ix1++) {
            Object cell = row[ix1];
            if (text(cell).toLowerCase().contains(q)) return true;
        }
        return false;
    }

    private void buildPager(int pages) {
        pager.removeAll();
        int from = order.isEmpty() ? 0 : pageNo * PAGE_SIZE + 1;
        int to = Math.min(order.size(), (pageNo + 1) * PAGE_SIZE);
        pager.add(Ui.label(order.isEmpty() ? "No records" : "Showing " + from + "–" + to + " of " + order.size(),
                Ui.SMALL, Ui.GRAY), BorderLayout.WEST);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        nav.setOpaque(false);
        Btn prev = Ui.secondary("Previous");
        prev.setEnabled(pageNo > 0);
        prev.setToolTipText("Previous page (Alt+Left)");
        prev.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pageNo--;
                render();
            }
        });
        nav.add(prev);
        int start = Math.max(0, Math.min(pageNo - 2, pages - 5));
        for (int p = start; p < Math.min(pages, start + 5); p++) {
            final int target = p;
            Btn b = new Btn(String.valueOf(p + 1), p == pageNo);
            b.setBorder(new EmptyBorder(8, 12, 8, 12));
            b.setToolTipText("Go to page " + (p + 1));
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pageNo = target;
                    render();
                }
            });
            nav.add(b);
        }
        Btn next = Ui.secondary("Next");
        next.setEnabled(pageNo < pages - 1);
        next.setToolTipText("Next page (Alt+Right)");
        next.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pageNo++;
                render();
            }
        });
        nav.add(next);
        pager.add(nav, BorderLayout.EAST);
        pager.revalidate();
        pager.repaint();
    }

    // ------------------------------------------------------------ keyboard shortcuts
    /** Clicks the action button whose text starts with the given word. Returns false when there is none. */
    private boolean clickButton(String word) {
        for (int i = 0; i < actions.getComponentCount(); i++) {
            Component c = actions.getComponent(i);
            if (!(c instanceof Btn) || !c.isEnabled()) continue;
            String text = ((Btn) c).getText().replace("+", "").trim().toLowerCase();
            if (text.startsWith(word)) {
                ((Btn) c).doClick();
                return true;
            }
        }
        return false;
    }

    /** Ctrl+N: clicks the blue "+ Add ..." button. */
    // >>> OOP | POLYMORPHISM: Overrides Page.shortcutNew(): on a table page Ctrl+N clicks the "+ Add" button.
    @Override public void shortcutNew() {
        for (int i = 0; i < actions.getComponentCount(); i++) {
            Component c = actions.getComponent(i);
            if (c instanceof Btn && ((Btn) c).isPrimary() && c.isEnabled()) {
                ((Btn) c).doClick();
                return;
            }
        }
    }

    /** Ctrl+E */
    @Override public void shortcutEdit() { clickButton("edit"); }

    /** Delete key: pages call their button "Delete" or "Remove". */
    @Override public void shortcutDelete() {
        if (!clickButton("delete")) clickButton("remove");
    }

    @Override public void shortcutFind() {
        if (search != null) {
            search.requestFocusInWindow();
            search.selectAll();
        }
    }

    @Override public void shortcutPage(int delta) {
        int pages = Math.max(1, (order.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int target = Math.max(0, Math.min(pages - 1, pageNo + delta));
        if (target != pageNo) {
            pageNo = target;
            render();
        }
    }

    /** The object behind the highlighted row (or null). */
    protected Object selected() {
        int r = table.getSelectedRow();
        return (r < 0 || r >= shown.size()) ? null : shown.get(r);
    }

    /** The selected row's object, or a warning and null when nothing is selected. */
    protected Object pick(String what) {
        Object o = selected();
        if (o == null) warn("Select " + what + " in the table first.");
        return o;
    }

    // ------------------------------------------------------------ text export
    /** Saves what the table shows (after search and sort) as a .txt file: one line per row, values in quotes, separated by commas. */
    protected void exportText() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export table as a text file");
        fc.setSelectedFile(new File(exportName + ".txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = fc.getSelectedFile();
        try (PrintWriter out = new PrintWriter(new FileWriter(target, StandardCharsets.UTF_8))) {
            // first line: the column headings
            String heading = "";
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].isEmpty()) continue;   // the empty column is the little arrow
                if (!heading.isEmpty()) heading = heading + ",";
                heading = heading + csv(columns[i]);
            }
            out.println(heading);
            // then one line for each row that is shown (after search and sort)
            for (int n = 0; n < order.size(); n++) {
                Object[] r = rows.get(order.get(n));
                String text = "";
                for (int i = 0; i < r.length; i++) {
                    if (i < columns.length && columns[i].isEmpty()) continue;
                    if (!text.isEmpty()) text = text + ",";
                    text = text + csv(text(r[i]));
                }
                out.println(text);
            }
            status("Exported " + order.size() + " row(s) to " + target.getName());
        } catch (IOException ex) {
            warn("Could not write the file: " + ex.getMessage());
        }
    }

    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------ colours of the status badges
    static Color[] badgeColors(String text) { // { background, text, border }
        switch (text) {
            case "Confirmed": case "Active": case "Paid":
                return new Color[]{new Color(0xE6F1EC), new Color(0x2F6B4F), new Color(0xB9D6C8)};
            case "Tentative": case "Pending": case "Partial": case "Draft":
                return new Color[]{new Color(0xF6EDDB), new Color(0x8A6A1F), new Color(0xE4D2A8)};
            case "Handled": case "LAWYER": case "Filed": case "Final":
                return new Color[]{new Color(0xE1EAF1), new Color(0x254D68), new Color(0xB7C9D8)};
            case "Cancelled": case "Overdue": case "Blocked":
                return new Color[]{new Color(0xF7E3E1), new Color(0x9B3B33), new Color(0xE5B9B4)};
            case "ADMIN":
                return new Color[]{new Color(0xF3ECD9), new Color(0x7A5E22), new Color(0xE1D2A8)};
            default:
                return new Color[]{new Color(0xE8ECEF), new Color(0x4A5560), new Color(0xC9D0D6)};
        }
    }

    static Color rowBg(boolean sel, int row) {
        return sel ? Ui.SOFT : (row % 2 == 0 ? Color.WHITE : Ui.ROW);
    }
}

/** One renderer for body cells: plain text, a status badge, or the "open" arrow. */
// >>> OOP | INHERITANCE: Extends a Swing class and overrides its method to draw coloured status badges.
class CellRenderer extends DefaultTableCellRenderer {
    static final int TEXT = 0, BADGE = 1, ARROW = 2;
    private final int kind;
    private String badge = "";

    CellRenderer(int kind) { this.kind = kind; }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
        badge = kind == BADGE && v != null ? v.toString() : "";
        super.getTableCellRendererComponent(t, kind == ARROW ? "\u203A" : kind == BADGE ? "" : TablePage.text(v), false, false, row, col);
        setFont(kind == ARROW ? Ui.BOLD.deriveFont(18) : Ui.PLAIN);
        setForeground(kind == ARROW ? Ui.GRAY : Ui.TEXT);
        setBackground(TablePage.rowBg(sel, row));
        setHorizontalAlignment(kind == ARROW ? CENTER : v instanceof Double ? RIGHT : LEFT);
        setBorder(new EmptyBorder(0, 10, 0, 10));
        return this;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (badge.isEmpty()) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color[] c = TablePage.badgeColors(badge);
        g.setFont(Ui.TAG);
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(badge) + 16, bh = 18, bx = 10, by = (getHeight() - bh) / 2;
        g.setColor(c[0]);
        g.fillRect(bx, by, bw, bh);
        g.setColor(c[2]);
        g.drawRect(bx, by, bw - 1, bh - 1);
        g.setColor(c[1]);
        g.drawString(badge, bx + 8, by + (bh - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}

/** Column header; shows the sort arrow when it belongs to a TablePage. */
class HeaderRenderer extends DefaultTableCellRenderer {
    private final TablePage owner;

    HeaderRenderer(TablePage owner) { this.owner = owner; }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
        String name = v == null ? "" : v.toString().toUpperCase();
        if (owner != null && col == owner.sortCol) name += owner.sortAsc ? "  \u25B2" : "  \u25BC";
        super.getTableCellRendererComponent(t, name, false, false, row, col);
        setFont(Ui.TAG);
        setForeground(Ui.NAVY);
        setBackground(Ui.HEAD);
        setOpaque(true);
        setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Ui.LINE), new EmptyBorder(0, 10, 0, 6)));
        setHorizontalAlignment(LEFT);
        return this;
    }
}
